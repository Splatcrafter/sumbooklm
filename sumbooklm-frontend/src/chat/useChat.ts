import { useCallback, useEffect, useRef, useState } from 'react';

import { useAuth } from '@/auth/useAuth';
import type { ModelSettings } from '@/byok/modelSettings';
import { ChatRequestError, loadConversation, streamAnswer } from '@/chat/chatApi';
import type { ChatMessage, ChatSource } from '@/chat/chatMessage';

/**
 * State of the conversation while it is being loaded.
 */
export type ChatStatus = 'loading' | 'ready' | 'failed';

/**
 * Everything the Sumbook view needs in order to show and continue a conversation.
 */
export interface ChatValue {
  status: ChatStatus;
  messages: ChatMessage[];
  /** Whether an answer is currently being generated. */
  answering: boolean;
  reload: () => Promise<void>;
  ask: (question: string, settings: ModelSettings) => Promise<void>;
}

/**
 * Loads the conversation of one Sumbook and lets it be continued.
 *
 * An answer is built up in place: the message it will become is appended as soon as the question is
 * sent and grows with every part that arrives. That is what makes the answer readable while it is
 * still being written, and it is also why a failure is recorded on that message rather than beside
 * the conversation, because the question it belongs to is already part of it.
 *
 * A view that is left while an answer is being generated aborts the request. The backend finishes the
 * answer and stores it either way, so it is there on the next visit rather than lost.
 */
export function useChat(notebookId: string): ChatValue {
  const { authorize, status: authStatus } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [status, setStatus] = useState<ChatStatus>('loading');
  const [answering, setAnswering] = useState(false);
  const inFlight = useRef<AbortController | null>(null);
  const turns = useRef(0);

  const reload = useCallback(async () => {
    const accessToken = await authorize();
    if (!accessToken) {
      return;
    }
    try {
      setMessages(await loadConversation(accessToken, notebookId));
      setStatus('ready');
    } catch {
      setStatus('failed');
    }
  }, [authorize, notebookId]);

  useEffect(() => {
    setStatus('loading');
    setMessages([]);
    if (authStatus === 'authenticated') {
      void reload();
    }
  }, [authStatus, reload]);

  useEffect(
    () => () => {
      inFlight.current?.abort();
    },
    [],
  );

  const update = useCallback((key: string, change: (message: ChatMessage) => ChatMessage) => {
    setMessages((current) =>
      current.map((message) => (message.key === key ? change(message) : message)),
    );
  }, []);

  const ask = useCallback(
    async (question: string, settings: ModelSettings) => {
      const accessToken = await authorize();
      if (!accessToken) {
        return;
      }

      turns.current += 1;
      const askedAt = new Date().toISOString();
      const answerKey = `answer-${turns.current}`;
      setMessages((current) => [
        ...current,
        { key: `question-${turns.current}`, role: 'USER', text: question, createdAt: askedAt },
        { key: answerKey, role: 'ASSISTANT', text: '', createdAt: askedAt, streaming: true },
      ]);

      const controller = new AbortController();
      inFlight.current = controller;
      setAnswering(true);
      try {
        await streamAnswer(
          { accessToken, notebookId, question, settings, signal: controller.signal },
          {
            onSources: (sources: ChatSource[]) =>
              update(answerKey, (message) => ({ ...message, sources })),
            onToken: (text: string) =>
              update(answerKey, (message) => ({ ...message, text: message.text + text })),
            onDone: (answer: string) =>
              update(answerKey, (message) => ({ ...message, text: answer, streaming: false })),
            onError: (reason: string) =>
              update(answerKey, (message) => ({ ...message, streaming: false, failure: reason })),
          },
        );
      } catch (error) {
        if (!controller.signal.aborted) {
          const limited = error instanceof ChatRequestError && error.status === 429;
          update(answerKey, (message) => ({
            ...message,
            streaming: false,
            limited,
            failure: limited ? undefined : error instanceof ChatRequestError ? error.message : '',
          }));
        }
      } finally {
        if (inFlight.current === controller) {
          inFlight.current = null;
          setAnswering(false);
        }
        update(answerKey, (message) => (message.streaming ? { ...message, streaming: false } : message));
      }
    },
    [authorize, notebookId, update],
  );

  return { status, messages, answering, reload, ask };
}
