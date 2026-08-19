import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useAuth } from '@/auth/useAuth';
import { useModelSettings } from '@/byok/useModelSettings';
import type { NotebookSummary } from '@/notebooks/notebookSummary';
import {
  getNotebookSummary,
  writeNotebookSummary,
  NotebookRequestError,
} from '@/notebooks/notebooksApi';

/**
 * State of the summary of one Sumbook.
 *
 * Loading it is a plain read. Writing it is a request to the provider of the reader, so it is a state
 * of its own: the panel says that something is being written rather than showing an empty space for
 * as long as a model takes.
 */
export type SummaryStatus = 'loading' | 'ready' | 'writing' | 'failed';

/**
 * Why the last attempt to write a summary did not produce one.
 *
 * The three cases lead somewhere different for the reader: waiting, waiting longer, and looking at
 * their model settings. Anything else is reported as a failure without a reason, because a reason
 * nobody can act on is noise.
 */
export type SummaryFailure = 'unread' | 'limited' | 'model' | null;

/**
 * Everything the view of one Sumbook needs about its summary.
 */
export interface NotebookSummaryValue {
  status: SummaryStatus;
  summary: NotebookSummary | null;
  failure: SummaryFailure;
  /** Whether a summary can be requested right now, which needs sources that were read and a model. */
  writable: boolean;
  write: () => Promise<void>;
}

/**
 * Turns a rejected request into the reason the reader is shown.
 */
function failureOf(error: unknown): SummaryFailure {
  if (!(error instanceof NotebookRequestError)) {
    return null;
  }
  if (error.status === 409) {
    return 'unread';
  }
  if (error.status === 429) {
    return 'limited';
  }
  return error.status === 400 ? 'model' : null;
}

/**
 * Loads the summary of one Sumbook and has one written when there is none.
 *
 * A Sumbook that has been read but never summarised writes its summary once, by itself, as soon as a
 * model is configured. That is the moment where the text is worth its cost: the reader is looking at
 * the space it belongs in and has nothing else there. Everything after that is asked for, because a
 * summary that rewrote itself whenever a source arrived would spend the money of whoever opened the
 * Sumbook, on a text they may not have come for.
 *
 * The attempt is made once per Sumbook and per browser session, whatever it ends in. A provider that
 * refuses would otherwise be asked again by every render that follows.
 */
export function useNotebookSummary(notebookId: string, summarisable: boolean): NotebookSummaryValue {
  const { authorize, status: authStatus } = useAuth();
  const { settings, configured } = useModelSettings();
  const { i18n } = useTranslation();
  const [summary, setSummary] = useState<NotebookSummary | null>(null);
  const [status, setStatus] = useState<SummaryStatus>('loading');
  const [failure, setFailure] = useState<SummaryFailure>(null);
  const attempted = useRef<string | null>(null);

  const write = useCallback(async () => {
    const accessToken = await authorize();
    if (!accessToken) {
      return;
    }
    setStatus('writing');
    setFailure(null);
    try {
      setSummary(await writeNotebookSummary(accessToken, notebookId, i18n.language, settings));
      setStatus('ready');
    } catch (error) {
      setFailure(failureOf(error));
      setStatus('ready');
    }
  }, [authorize, i18n.language, notebookId, settings]);

  useEffect(() => {
    setStatus('loading');
    setSummary(null);
    setFailure(null);
    attempted.current = null;
    if (authStatus !== 'authenticated') {
      return;
    }
    void (async () => {
      const accessToken = await authorize();
      if (!accessToken) {
        return;
      }
      try {
        setSummary(await getNotebookSummary(accessToken, notebookId));
        setStatus('ready');
      } catch {
        setStatus('failed');
      }
    })();
  }, [authStatus, authorize, notebookId]);

  useEffect(() => {
    if (status !== 'ready' || !summarisable || !configured) {
      return;
    }
    if (summary === null || summary.text !== '' || attempted.current === notebookId) {
      return;
    }
    attempted.current = notebookId;
    void write();
  }, [configured, notebookId, status, summarisable, summary, write]);

  return { status, summary, failure, writable: summarisable && configured, write };
}
