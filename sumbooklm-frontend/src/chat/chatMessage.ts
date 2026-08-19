import type { components } from '@/api/schema';

/**
 * Author of one message of a conversation.
 */
export type ChatRole = 'USER' | 'ASSISTANT';

/**
 * One conversation of a Sumbook, as a list shows it.
 */
export interface ChatSummary {
  id: string;
  title: string;
  createdAt: string;
  lastMessageAt: string;
  messageCount: number;
}

/**
 * One source an answer was allowed to cite, as the stream announced it.
 */
export interface ChatSource {
  number: number;
  sourceDocumentId: string;
  displayName: string;
}

/**
 * One message as the conversation view holds it.
 *
 * The key is assigned by this client rather than by the backend, which stores a transcript instead of
 * addressable messages. It only has to be stable while the list is on screen, which is what a render
 * needs it for.
 *
 * The optional fields describe a message that is still being generated: what it may cite, that it is
 * not finished, and why it stopped early. A stored message carries none of them.
 *
 * A turn the backend refused because too many answers are already being generated is its own kind of
 * ending. It is not a failure of the question, so it is marked rather than described, and the sentence
 * the reader gets is written in their own language instead of taken from the response.
 *
 * A turn refused because the account has asked too often carries how long that lasts, in minutes, so
 * that the reader is told when to come back instead of being invited to try again immediately.
 */
export interface ChatMessage {
  key: string;
  role: ChatRole;
  text: string;
  createdAt: string;
  sources?: ChatSource[];
  streaming?: boolean;
  failure?: string;
  limited?: boolean;
  limitedForMinutes?: number;
}

/**
 * Narrows a message of a backend response into its client side form.
 */
export function toChatMessage(
  message: components['schemas']['ChatMessageResponse'],
  index: number,
): ChatMessage {
  return {
    key: `stored-${index}`,
    role: message.role === 'ASSISTANT' ? 'ASSISTANT' : 'USER',
    text: message.text ?? '',
    createdAt: message.createdAt ?? new Date().toISOString(),
  };
}

/**
 * Narrows a conversation of a backend response into its client side form.
 */
export function toChatSummary(
  summary: components['schemas']['ChatSummaryResponse'],
): ChatSummary {
  return {
    id: summary.id ?? '',
    title: summary.title ?? '',
    createdAt: summary.createdAt ?? new Date().toISOString(),
    lastMessageAt: summary.lastMessageAt ?? new Date().toISOString(),
    messageCount: summary.messageCount ?? 0,
  };
}

/**
 * Narrows a source of a stream event into its client side form.
 */
export function toChatSource(value: unknown): ChatSource | null {
  if (typeof value !== 'object' || value === null) {
    return null;
  }
  const candidate = value as Record<string, unknown>;
  if (
    typeof candidate.number !== 'number' ||
    typeof candidate.sourceDocumentId !== 'string' ||
    typeof candidate.displayName !== 'string'
  ) {
    return null;
  }
  return {
    number: candidate.number,
    sourceDocumentId: candidate.sourceDocumentId,
    displayName: candidate.displayName,
  };
}
