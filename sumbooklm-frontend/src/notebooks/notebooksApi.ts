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

import { apiClient } from '@/api/client';
import { modelHeaders, type ModelSettings } from '@/byok/modelSettings';
import { toNotebook, type Notebook } from '@/notebooks/notebook';
import { toNotebookSummary, type NotebookSummary } from '@/notebooks/notebookSummary';

/**
 * Raised when the backend rejects a notebook request.
 *
 * The status is kept so that a view can tell a notebook that no longer exists from a request that
 * failed for another reason, without parsing a message.
 */
export class NotebookRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`The notebook request failed with status ${status}`);
    this.name = 'NotebookRequestError';
    this.status = status;
  }
}

function authorization(accessToken: string): { Authorization: string } {
  return { Authorization: `Bearer ${accessToken}` };
}

/**
 * Lists the notebooks of the signed-in user, most recently active first.
 */
export async function listNotebooks(accessToken: string): Promise<Notebook[]> {
  const { data, response } = await apiClient.GET('/api/v1/notebooks', {
    headers: authorization(accessToken),
  });
  if (!response.ok || !data) {
    throw new NotebookRequestError(response.status);
  }
  return data.map(toNotebook);
}

/**
 * Reads one notebook.
 *
 * The view of a single Sumbook loads it through this call rather than searching the collection, so
 * that opening its address directly works even when the collection was never loaded.
 */
export async function getNotebook(accessToken: string, notebookId: string): Promise<Notebook> {
  const { data, response } = await apiClient.GET('/api/v1/notebooks/{notebookId}', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
  });
  if (!response.ok || !data) {
    throw new NotebookRequestError(response.status);
  }
  return toNotebook(data);
}

/**
 * Creates a notebook and returns it as the backend stored it.
 */
export async function createNotebook(accessToken: string, title: string): Promise<Notebook> {
  const { data, response } = await apiClient.POST('/api/v1/notebooks', {
    headers: authorization(accessToken),
    body: { title },
  });
  if (!response.ok || !data) {
    throw new NotebookRequestError(response.status);
  }
  return toNotebook(data);
}

/**
 * Changes the title, the pin state or both of one notebook. An omitted field stays as it is.
 */
export async function updateNotebook(
  accessToken: string,
  notebookId: string,
  change: { title?: string; pinned?: boolean },
): Promise<Notebook> {
  const { data, response } = await apiClient.PATCH('/api/v1/notebooks/{notebookId}', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
    body: change,
  });
  if (!response.ok || !data) {
    throw new NotebookRequestError(response.status);
  }
  return toNotebook(data);
}

/**
 * Removes one notebook together with everything below it.
 */
export async function deleteNotebook(accessToken: string, notebookId: string): Promise<void> {
  const { response } = await apiClient.DELETE('/api/v1/notebooks/{notebookId}', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
  });
  if (!response.ok) {
    throw new NotebookRequestError(response.status);
  }
}

/**
 * Reads the summary one Sumbook carries, which is empty until one has been written.
 *
 * The call reaches no model and costs nothing, so it is made whenever a Sumbook is opened.
 */
export async function getNotebookSummary(
  accessToken: string,
  notebookId: string,
): Promise<NotebookSummary> {
  const { data, response } = await apiClient.GET('/api/v1/notebooks/{notebookId}/summary', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
  });
  if (!response.ok || !data) {
    throw new NotebookRequestError(response.status);
  }
  return toNotebookSummary(data);
}

/**
 * Has the summary of one Sumbook written and returns the text that was stored.
 *
 * This is a request to the provider of the reader, made with their own key, and it takes as long as
 * the model does. The language is the one the interface is being read in, because a summary has no
 * question whose language it could follow.
 */
export async function writeNotebookSummary(
  accessToken: string,
  notebookId: string,
  language: string,
  settings: ModelSettings,
): Promise<NotebookSummary> {
  const { data, response } = await apiClient.POST('/api/v1/notebooks/{notebookId}/summary', {
    headers: { ...authorization(accessToken), ...modelHeaders(settings) },
    params: { path: { notebookId } },
    body: { language },
  });
  if (!response.ok || !data) {
    throw new NotebookRequestError(response.status);
  }
  return toNotebookSummary(data);
}
