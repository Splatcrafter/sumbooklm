import { apiClient } from '@/api/client';
import { modelHeaders, type ModelSettings } from '@/byok/modelSettings';
import {
  toChatMessage,
  toChatSource,
  toChatSummary,
  type ChatMessage,
  type ChatSource,
  type ChatSummary,
} from '@/chat/chatMessage';

/**
 * Raised when the backend rejects a chat request before an answer is streamed.
 *
 * The status is kept so that a view can tell a rejected configuration from a Sumbook that is gone,
 * without parsing a message. A refusal that lasts a known time also carries how long that is, which is
 * what separates the two reasons a question can be refused: being busy with one's own answers passes
 * within seconds, having asked too often does not.
 */
export class ChatRequestError extends Error {
  readonly status: number;

  readonly retryAfterSeconds?: number;

  constructor(status: number, detail?: string, retryAfterSeconds?: number) {
    super(detail && detail !== '' ? detail : `The chat request failed with status ${status}`);
    this.name = 'ChatRequestError';
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/**
 * Reads how long a refusal lasts, in seconds, from the header the backend states it in.
 *
 * A value that is not a number of seconds is treated as absent rather than as zero, because a date is
 * also a legal value of that header and this application never sends one.
 */
function retryAfterOf(response: Response): number | undefined {
  const header = response.headers.get('Retry-After');
  if (header === null) {
    return undefined;
  }
  const seconds = Number.parseInt(header, 10);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : undefined;
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
 * Lists the conversations of one Sumbook, most recently used first.
 */
export async function listConversations(
  accessToken: string,
  notebookId: string,
): Promise<ChatSummary[]> {
  const { data, response } = await apiClient.GET('/api/v1/notebooks/{notebookId}/chats', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
  });
  if (!response.ok || !data) {
    throw new ChatRequestError(response.status);
  }
  return data.map(toChatSummary);
}

/**
 * Starts a conversation in one Sumbook and returns it, empty.
 */
export async function startConversation(
  accessToken: string,
  notebookId: string,
): Promise<ChatSummary> {
  const { data, response } = await apiClient.POST('/api/v1/notebooks/{notebookId}/chats', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
  });
  if (!response.ok || !data) {
    throw new ChatRequestError(response.status);
  }
  return toChatSummary({ ...data, messageCount: (data.messages ?? []).length });
}

/**
 * Reads one conversation of one Sumbook, oldest message first.
 */
export async function loadConversation(
  accessToken: string,
  notebookId: string,
  sessionId: string,
): Promise<ChatMessage[]> {
  const { data, response } = await apiClient.GET('/api/v1/notebooks/{notebookId}/chats/{sessionId}', {
    headers: authorization(accessToken),
    params: { path: { notebookId, sessionId } },
  });
  if (!response.ok || !data) {
    throw new ChatRequestError(response.status);
  }
  return (data.messages ?? []).map(toChatMessage);
}

/**
 * Removes one conversation of one Sumbook, together with its transcript.
 */
export async function deleteConversation(
  accessToken: string,
  notebookId: string,
  sessionId: string,
): Promise<void> {
  const { response } = await apiClient.DELETE('/api/v1/notebooks/{notebookId}/chats/{sessionId}', {
    headers: authorization(accessToken),
    params: { path: { notebookId, sessionId } },
  });
  if (!response.ok) {
    throw new ChatRequestError(response.status);
  }
}

/**
 * Asks for the answer being generated in one conversation to stop.
 *
 * What was generated before the stop is kept, so the transcript holds the answer as far as it got
 * rather than nothing at all.
 */
export async function stopAnswer(
  accessToken: string,
  notebookId: string,
  sessionId: string,
): Promise<void> {
  const { response } = await apiClient.POST(
    '/api/v1/notebooks/{notebookId}/chats/{sessionId}/stop',
    {
      headers: authorization(accessToken),
      params: { path: { notebookId, sessionId } },
    },
  );
  if (!response.ok) {
    throw new ChatRequestError(response.status);
  }
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
    sessionId: string;
    question: string;
    settings: ModelSettings;
    signal?: AbortSignal;
  },
  handlers: AnswerHandlers,
): Promise<void> {
  const path = `/api/v1/notebooks/${encodeURIComponent(request.notebookId)}`
    + `/chats/${encodeURIComponent(request.sessionId)}/questions`;
  const response = await fetch(path, {
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
    throw new ChatRequestError(response.status, await detailOf(response), retryAfterOf(response));
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
