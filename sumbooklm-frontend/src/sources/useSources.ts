import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { useAuth } from '@/auth/useAuth';
import { isPending, type Source } from '@/sources/source';
import {
  addSourceLink,
  deleteSource,
  listSources,
  reindexSource,
  SourceRequestError,
  uploadSourceFile,
} from '@/sources/sourcesApi';

/**
 * How often the list is read again while at least one source is still being indexed.
 */
const POLL_INTERVAL_MS = 1500;

/**
 * State of the source collection while it is being loaded.
 */
export type SourcesStatus = 'loading' | 'ready' | 'failed';

/**
 * Everything the Sumbook view needs in order to show and change the sources of one Sumbook.
 */
export interface SourcesValue {
  status: SourcesStatus;
  sources: Source[];
  /** Whether at least one source is still on its way into the index. */
  indexing: boolean;
  reload: () => Promise<void>;
  addFile: (file: File) => Promise<void>;
  addLink: (url: string) => Promise<void>;
  reindex: (sourceId: string) => Promise<void>;
  remove: (sourceId: string) => Promise<void>;
}

/**
 * Raised when an action is attempted while no valid session exists any more.
 */
class NotAuthenticatedError extends Error {
  constructor() {
    super('The session ended before the request could be sent');
    this.name = 'NotAuthenticatedError';
  }
}

/**
 * Loads the sources of one Sumbook and keeps them in step with the indexing that happens for them.
 *
 * Indexing runs in the background on the server, so the only way for the view to learn that a source
 * has become searchable is to ask again. The list is therefore read again while at least one source
 * is still pending and left alone as soon as none is, which keeps an open Sumbook that has nothing
 * to wait for from producing requests.
 */
export function useSources(notebookId: string): SourcesValue {
  const { authorize, status: authStatus } = useAuth();
  const [sources, setSources] = useState<Source[]>([]);
  const [status, setStatus] = useState<SourcesStatus>('loading');
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const requireAccessToken = useCallback(async (): Promise<string> => {
    const accessToken = await authorize();
    if (!accessToken) {
      throw new NotAuthenticatedError();
    }
    return accessToken;
  }, [authorize]);

  const reload = useCallback(async () => {
    try {
      const accessToken = await requireAccessToken();
      setSources(await listSources(accessToken, notebookId));
      setStatus('ready');
    } catch (error) {
      if (error instanceof NotAuthenticatedError) {
        return;
      }
      setStatus('failed');
    }
  }, [notebookId, requireAccessToken]);

  useEffect(() => {
    setStatus('loading');
    setSources([]);
    if (authStatus === 'authenticated') {
      void reload();
    }
  }, [authStatus, reload]);

  const indexing = useMemo(() => sources.some(isPending), [sources]);

  useEffect(() => {
    if (status !== 'ready' || !indexing) {
      return undefined;
    }
    timer.current = setTimeout(() => void reload(), POLL_INTERVAL_MS);
    return () => {
      if (timer.current !== null) {
        clearTimeout(timer.current);
      }
    };
  }, [indexing, reload, sources, status]);

  const addFile = useCallback(
    async (file: File) => {
      const added = await uploadSourceFile(await requireAccessToken(), notebookId, file);
      setSources((current) => [...current, added]);
    },
    [notebookId, requireAccessToken],
  );

  const addLink = useCallback(
    async (url: string) => {
      const added = await addSourceLink(await requireAccessToken(), notebookId, url);
      setSources((current) => [...current, added]);
    },
    [notebookId, requireAccessToken],
  );

  const reindex = useCallback(
    async (sourceId: string) => {
      const queued = await reindexSource(await requireAccessToken(), notebookId, sourceId);
      setSources((current) => current.map((source) => (source.id === sourceId ? queued : source)));
    },
    [notebookId, requireAccessToken],
  );

  const remove = useCallback(
    async (sourceId: string) => {
      try {
        await deleteSource(await requireAccessToken(), notebookId, sourceId);
      } catch (error) {
        // A source the backend no longer knows is gone either way, so the list is corrected instead
        // of the removal being reported as a failure the user cannot act on.
        if (!(error instanceof SourceRequestError) || error.status !== 404) {
          throw error;
        }
      }
      setSources((current) => current.filter((source) => source.id !== sourceId));
    },
    [notebookId, requireAccessToken],
  );

  return { status, sources, indexing, reload, addFile, addLink, reindex, remove };
}
