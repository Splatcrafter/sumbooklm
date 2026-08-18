import { useCallback, useEffect, useState } from 'react';

import { useAuth } from '@/auth/useAuth';
import type { Notebook } from '@/notebooks/notebook';
import { getNotebook, NotebookRequestError } from '@/notebooks/notebooksApi';

/**
 * State of one notebook while it is being loaded.
 *
 * A Sumbook that does not exist is told apart from a request that failed, because the two need
 * different answers: the first is a dead address, the second is worth trying again.
 */
export type NotebookStatus = 'loading' | 'ready' | 'missing' | 'failed';

/**
 * Everything the view of one Sumbook needs about the Sumbook itself.
 */
export interface NotebookValue {
  status: NotebookStatus;
  notebook: Notebook | null;
  reload: () => Promise<void>;
}

/**
 * Loads one notebook by its identifier.
 */
export function useNotebook(notebookId: string): NotebookValue {
  const { authorize, status: authStatus } = useAuth();
  const [notebook, setNotebook] = useState<Notebook | null>(null);
  const [status, setStatus] = useState<NotebookStatus>('loading');

  const reload = useCallback(async () => {
    const accessToken = await authorize();
    if (!accessToken) {
      return;
    }
    try {
      setNotebook(await getNotebook(accessToken, notebookId));
      setStatus('ready');
    } catch (error) {
      setStatus(error instanceof NotebookRequestError && error.status === 404 ? 'missing' : 'failed');
    }
  }, [authorize, notebookId]);

  useEffect(() => {
    setStatus('loading');
    setNotebook(null);
    if (authStatus === 'authenticated') {
      void reload();
    }
  }, [authStatus, reload]);

  return { status, notebook, reload };
}
