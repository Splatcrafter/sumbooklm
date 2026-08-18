import { apiClient } from '@/api/client';
import { toSource, type Source } from '@/sources/source';

/**
 * Raised when the backend rejects a source request.
 *
 * The status is kept so that a view can tell a rejected duplicate from a request that failed for
 * another reason, without parsing a message.
 */
export class SourceRequestError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`The source request failed with status ${status}`);
    this.name = 'SourceRequestError';
    this.status = status;
  }
}

function authorization(accessToken: string): { Authorization: string } {
  return { Authorization: `Bearer ${accessToken}` };
}

/**
 * Lists the sources of one Sumbook, in the order they were added.
 */
export async function listSources(accessToken: string, notebookId: string): Promise<Source[]> {
  const { data, response } = await apiClient.GET('/api/v1/notebooks/{notebookId}/sources', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
  });
  if (!response.ok || !data) {
    throw new SourceRequestError(response.status);
  }
  return data.map(toSource);
}

/**
 * Uploads a file as a source and returns it as the backend stored it, before it is indexed.
 */
export async function uploadSourceFile(
  accessToken: string,
  notebookId: string,
  file: File,
): Promise<Source> {
  const { data, response } = await apiClient.POST('/api/v1/notebooks/{notebookId}/sources/files', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
    // A binary part is described as a string in the specification, so the generated body type
    // cannot hold a File. The serializer below is what actually builds the request, and it puts
    // the File itself into the form, which is why the value passed here has to be cast.
    body: { file: file as unknown as string },
    bodySerializer: (body: { file: string } | undefined) => {
      const form = new FormData();
      if (body) {
        form.append('file', body.file as unknown as File);
      }
      return form;
    },
  });
  if (!response.ok || !data) {
    throw new SourceRequestError(response.status);
  }
  return toSource(data);
}

/**
 * Adds a web page as a source and returns it as the backend stored it, before it is retrieved.
 */
export async function addSourceLink(
  accessToken: string,
  notebookId: string,
  url: string,
): Promise<Source> {
  const { data, response } = await apiClient.POST('/api/v1/notebooks/{notebookId}/sources/links', {
    headers: authorization(accessToken),
    params: { path: { notebookId } },
    body: { url },
  });
  if (!response.ok || !data) {
    throw new SourceRequestError(response.status);
  }
  return toSource(data);
}

/**
 * Removes one source together with its segments in the retrieval index.
 */
export async function deleteSource(
  accessToken: string,
  notebookId: string,
  sourceId: string,
): Promise<void> {
  const { response } = await apiClient.DELETE('/api/v1/notebooks/{notebookId}/sources/{sourceId}', {
    headers: authorization(accessToken),
    params: { path: { notebookId, sourceId } },
  });
  if (!response.ok) {
    throw new SourceRequestError(response.status);
  }
}
