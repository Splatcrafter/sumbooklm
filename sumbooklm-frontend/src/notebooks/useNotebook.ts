/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
