import { apiClient } from '@/api/client';
import { toNotebook, type Notebook } from '@/notebooks/notebook';

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
