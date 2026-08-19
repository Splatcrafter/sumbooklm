import { useCallback, useEffect, useRef, useState } from 'react';

import { useAuth } from '@/auth/useAuth';
import type { ModelSettings } from '@/byok/modelSettings';
import {
  ChatRequestError,
  deleteConversation,
  listConversations,
  loadConversation,
  startConversation,
  stopAnswer,
  streamAnswer,
} from '@/chat/chatApi';
import type { ChatMessage, ChatSource, ChatSummary } from '@/chat/chatMessage';

/**
 * State of the conversations while they are being loaded.
 */
export type ChatStatus = 'loading' | 'ready' | 'failed';

/**
 * Everything the Sumbook view needs in order to show and continue its conversations.
 */
export interface ChatValue {
  status: ChatStatus;
  conversations: ChatSummary[];
  /** Conversation the transcript belongs to, null while the Sumbook holds none. */
  currentId: string | null;
  messages: ChatMessage[];
  /** Whether an answer is currently being generated. */
  answering: boolean;
  select: (sessionId: string) => Promise<void>;
  start: () => Promise<void>;
  remove: (sessionId: string) => Promise<void>;
  stop: () => Promise<void>;
  ask: (question: string, settings: ModelSettings) => Promise<void>;
  reload: () => Promise<void>;
}

/**
 * Loads the conversations of one Sumbook and lets them be continued.
 *
 * A Sumbook holds as many conversations as its user starts, and the view shows one of them at a
 * time. Nothing is created by opening a Sumbook: the first question creates the conversation it goes
 * into, so a Sumbook that is only being read stays as empty as it is.
 *
 * An answer is built up in place: the message it will become is appended as soon as the question is
 * sent and grows with every part that arrives. That is what makes the answer readable while it is
 * still being written, and it is also why a failure is recorded on that message rather than beside
 * the conversation, because the question it belongs to is already part of it.
 *
 * A view that is left while an answer is being generated aborts the request without stopping the
 * answer. The backend finishes it and stores it either way, so it is there on the next visit rather
 * than lost. Stopping is the deliberate act, and it is a request of its own.
 */
export function useChat(notebookId: string): ChatValue {
  const { authorize, status: authStatus } = useAuth();
  const [conversations, setConversations] = useState<ChatSummary[]>([]);
  const [currentId, setCurrentId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [status, setStatus] = useState<ChatStatus>('loading');
  const [answering, setAnswering] = useState(false);
  const inFlight = useRef<AbortController | null>(null);
  const turns = useRef(0);

  const requireAccessToken = useCallback(async (): Promise<string | null> => authorize(), [authorize]);

  const openConversation = useCallback(
    async (accessToken: string, sessionId: string) => {
      setCurrentId(sessionId);
      setMessages(await loadConversation(accessToken, notebookId, sessionId));
    },
    [notebookId],
  );

  const reload = useCallback(async () => {
    const accessToken = await requireAccessToken();
    if (!accessToken) {
      return;
    }
    try {
      const listed = await listConversations(accessToken, notebookId);
      setConversations(listed);
      if (listed.length === 0) {
        setCurrentId(null);
        setMessages([]);
      } else {
        await openConversation(accessToken, listed[0].id);
      }
      setStatus('ready');
    } catch {
      setStatus('failed');
    }
  }, [notebookId, openConversation, requireAccessToken]);

  useEffect(() => {
    setStatus('loading');
    setConversations([]);
    setCurrentId(null);
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

  const select = useCallback(
    async (sessionId: string) => {
      const accessToken = await requireAccessToken();
      if (accessToken) {
        await openConversation(accessToken, sessionId);
      }
    },
    [openConversation, requireAccessToken],
  );

  const start = useCallback(async () => {
    const accessToken = await requireAccessToken();
    if (!accessToken) {
      return;
    }
    const started = await startConversation(accessToken, notebookId);
    setConversations((current) => [started, ...current]);
    setCurrentId(started.id);
    setMessages([]);
  }, [notebookId, requireAccessToken]);

  const remove = useCallback(
    async (sessionId: string) => {
      const accessToken = await requireAccessToken();
      if (!accessToken) {
        return;
      }
      await deleteConversation(accessToken, notebookId, sessionId);
      const remaining = conversations.filter((conversation) => conversation.id !== sessionId);
      setConversations(remaining);
      if (currentId === sessionId) {
        if (remaining.length === 0) {
          setCurrentId(null);
          setMessages([]);
        } else {
          await openConversation(accessToken, remaining[0].id);
        }
      }
    },
    [conversations, currentId, notebookId, openConversation, requireAccessToken],
  );

  const stop = useCallback(async () => {
    const accessToken = await requireAccessToken();
    if (accessToken && currentId) {
      await stopAnswer(accessToken, notebookId, currentId);
    }
  }, [currentId, notebookId, requireAccessToken]);

  const update = useCallback((key: string, change: (message: ChatMessage) => ChatMessage) => {
    setMessages((current) =>
      current.map((message) => (message.key === key ? change(message) : message)),
    );
  }, []);

  const ask = useCallback(
    async (question: string, settings: ModelSettings) => {
      const accessToken = await requireAccessToken();
      if (!accessToken) {
        return;
      }

      let sessionId = currentId;
      if (!sessionId) {
        const started = await startConversation(accessToken, notebookId);
        setConversations((current) => [started, ...current]);
        setCurrentId(started.id);
        setMessages([]);
        sessionId = started.id;
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
          { accessToken, notebookId, sessionId, question, settings, signal: controller.signal },
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
        // The title of a conversation is derived from its first question, so the list is read again
        // once a turn has ended rather than being guessed at here.
        const listed = await listConversations(accessToken, notebookId).catch(() => null);
        if (listed) {
          setConversations(listed);
        }
      }
    },
    [currentId, notebookId, requireAccessToken, update],
  );

  return { status, conversations, currentId, messages, answering, select, start, remove, stop, ask, reload };
}
