import { useCallback, useEffect, useMemo, useState } from 'react';

import { useAuth } from '@/auth/useAuth';
import type { Notebook } from '@/notebooks/notebook';
import {
  createNotebook,
  deleteNotebook,
  listNotebooks,
  NotebookRequestError,
  updateNotebook,
} from '@/notebooks/notebooksApi';

/**
 * State of the notebook collection while it is being loaded.
 */
export type NotebooksStatus = 'loading' | 'ready' | 'failed';

/**
 * Everything the dashboard needs in order to show and change the notebooks of the signed-in user.
 */
export interface NotebooksValue {
  status: NotebooksStatus;
  /** Every notebook of the user, most recently active first. */
  notebooks: Notebook[];
  /** The pinned notebooks, in the same order. */
  pinned: Notebook[];
  /** The notebooks that are not pinned, so that no notebook is shown in both sections. */
  recent: Notebook[];
  reload: () => Promise<void>;
  create: (title: string) => Promise<void>;
  rename: (notebookId: string, title: string) => Promise<void>;
  setPinned: (notebookId: string, pinned: boolean) => Promise<void>;
  remove: (notebookId: string) => Promise<void>;
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

function byActivityDescending(left: Notebook, right: Notebook): number {
  return Date.parse(right.lastActivityAt) - Date.parse(left.lastActivityAt);
}

/**
 * Loads the notebooks of the signed-in user and keeps them in step with the changes made to them.
 *
 * Every action sends its request first and adopts the notebook the backend answers with. Updating
 * the list from the response rather than from the submitted values is what keeps derived fields,
 * above all the activity timestamp the list is ordered by, from drifting away from what is stored.
 */
export function useNotebooks(): NotebooksValue {
  const { authorize, status: authStatus } = useAuth();
  const [notebooks, setNotebooks] = useState<Notebook[]>([]);
  const [status, setStatus] = useState<NotebooksStatus>('loading');

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
      setNotebooks((await listNotebooks(accessToken)).toSorted(byActivityDescending));
      setStatus('ready');
    } catch (error) {
      if (error instanceof NotAuthenticatedError) {
        return;
      }
      setStatus('failed');
    }
  }, [requireAccessToken]);

  useEffect(() => {
    if (authStatus === 'authenticated') {
      void reload();
    }
  }, [authStatus, reload]);

  const adopt = useCallback((changed: Notebook) => {
    setNotebooks((current) =>
      current
        .map((notebook) => (notebook.id === changed.id ? changed : notebook))
        .toSorted(byActivityDescending),
    );
  }, []);

  const create = useCallback(
    async (title: string) => {
      const created = await createNotebook(await requireAccessToken(), title);
      setNotebooks((current) => [created, ...current].toSorted(byActivityDescending));
    },
    [requireAccessToken],
  );

  const rename = useCallback(
    async (notebookId: string, title: string) => {
      adopt(await updateNotebook(await requireAccessToken(), notebookId, { title }));
    },
    [adopt, requireAccessToken],
  );

  const setPinned = useCallback(
    async (notebookId: string, pinned: boolean) => {
      adopt(await updateNotebook(await requireAccessToken(), notebookId, { pinned }));
    },
    [adopt, requireAccessToken],
  );

  const remove = useCallback(
    async (notebookId: string) => {
      try {
        await deleteNotebook(await requireAccessToken(), notebookId);
      } catch (error) {
        // A notebook the backend no longer knows is gone either way, so the list is corrected
        // instead of the removal being reported as a failure the user cannot act on.
        if (!(error instanceof NotebookRequestError) || error.status !== 404) {
          throw error;
        }
      }
      setNotebooks((current) => current.filter((notebook) => notebook.id !== notebookId));
    },
    [requireAccessToken],
  );

  const pinned = useMemo(() => notebooks.filter((notebook) => notebook.pinned), [notebooks]);
  const recent = useMemo(() => notebooks.filter((notebook) => !notebook.pinned), [notebooks]);

  return { status, notebooks, pinned, recent, reload, create, rename, setPinned, remove };
}
