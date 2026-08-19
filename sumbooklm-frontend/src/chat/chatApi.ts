import { apiClient } from '@/api/client';
import { modelHeaders, type ModelSettings } from '@/byok/modelSettings';
import { toChatMessage, toChatSource, type ChatMessage, type ChatSource } from '@/chat/chatMessage';

/**
 * Raised when the backend rejects a chat request before an answer is streamed.
 *
 * The status is kept so that a view can tell a rejected configuration from a Sumbook that is gone,
 * without parsing a message.
 */
export class ChatRequestError extends Error {
  readonly status: number;

  constructor(status: number, detail?: string) {
    super(detail && detail !== '' ? detail : `The chat request failed with status ${status}`);
    this.name = 'ChatRequestError';
    this.status = status;
  }
}

/**
 * Receivers of the events one answer arrives as.
 */
export interface AnswerHandlers {
  onSources: (sources: ChatSource[]) => void;
  onToken: (text: string) => void;
  onDone: (answer: string) => void;
  onError: (reason: string) => void;
}

function authorization(accessToken: string): { Authorization: string } {
  return { Authorization: `Bearer ${accessToken}` };
}

/**
 * Reads the conversation of one Sumbook, oldest message first.
 */
export async function loadConversation(
  accessToken: string,
  notebookId: string,
): Promise<ChatMessage[]> {
  const { data, response } = await apiClient.GET('/api/v1/notebooks/{notebookId}/chat/messages', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
  });
  if (!response.ok || !data) {
    throw new ChatRequestError(response.status);
  }
  return (data.messages ?? []).map(toChatMessage);
}

/**
 * Reads the detail out of a problem document, which is what the backend rejects a request with.
 */
async function detailOf(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: unknown };
    return typeof problem.detail === 'string' ? problem.detail : '';
  } catch {
    return '';
  }
}

/**
 * Turns one server sent event into a call on the handlers.
 */
function dispatch(name: string, data: string, handlers: AnswerHandlers): void {
  let payload: unknown;
  try {
    payload = JSON.parse(data);
  } catch {
    return;
  }

  if (name === 'sources' && Array.isArray(payload)) {
    handlers.onSources(payload.map(toChatSource).filter((source) => source !== null));
    return;
  }
  const object = typeof payload === 'object' && payload !== null ? (payload as Record<string, unknown>) : {};
  if (name === 'token' && typeof object.text === 'string') {
    handlers.onToken(object.text);
  } else if (name === 'done' && typeof object.answer === 'string') {
    handlers.onDone(object.answer);
  } else if (name === 'error') {
    handlers.onError(typeof object.reason === 'string' ? object.reason : '');
  }
}

/**
 * Splits off every complete event of the buffer and reports it, returning what is left.
 *
 * An event ends at a blank line, so anything after the last one is the beginning of the next event
 * and stays in the buffer until the rest of it arrives.
 */
function consume(buffer: string, handlers: AnswerHandlers): string {
  let rest = buffer;
  let boundary = rest.indexOf('\n\n');
  while (boundary >= 0) {
    const frame = rest.slice(0, boundary);
    rest = rest.slice(boundary + 2);
    boundary = rest.indexOf('\n\n');

    let name = 'message';
    const data: string[] = [];
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) {
        name = line.slice('event:'.length).trim();
      } else if (line.startsWith('data:')) {
        data.push(line.slice('data:'.length).trimStart());
      }
    }
    if (data.length > 0) {
      dispatch(name, data.join('\n'), handlers);
    }
  }
  return rest;
}

/**
 * Asks a question and reports the answer while it is being generated.
 *
 * The request is made with fetch rather than through the generated client, because the client reads a
 * whole response before returning it and the point of this one is that it is read while it arrives.
 * The generated client is still what types every other call, including the transcript this answer
 * ends up in.
 */
export async function streamAnswer(
  request: {
    accessToken: string;
    notebookId: string;
    question: string;
    settings: ModelSettings;
    signal?: AbortSignal;
  },
  handlers: AnswerHandlers,
): Promise<void> {
  const response = await fetch(`/api/v1/notebooks/${encodeURIComponent(request.notebookId)}/chat`, {
    method: 'POST',
    headers: {
      ...authorization(request.accessToken),
      ...modelHeaders(request.settings),
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ question: request.question }),
    signal: request.signal,
  });

  if (!response.ok || !response.body) {
    throw new ChatRequestError(response.status, await detailOf(response));
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer = consume(buffer + decoder.decode(value, { stream: true }), handlers);
  }
  consume(`${buffer}\n\n`, handlers);
}
