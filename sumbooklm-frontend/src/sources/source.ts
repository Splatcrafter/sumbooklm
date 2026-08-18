import { MalformedResponseError, requireNumber, requireString } from '@/api/narrowing';
import type { components } from '@/api/schema';

/**
 * Stage a source has reached on its way into the retrieval index.
 */
export type SourceStatus = 'UPLOADED' | 'INDEXING' | 'READY' | 'ERROR';

/**
 * Way a source entered its Sumbook.
 */
export type SourceKind = 'FILE' | 'WEB';

/**
 * One source of a Sumbook.
 */
export interface Source {
  id: string;
  notebookId: string;
  displayName: string;
  kind: SourceKind;
  /** Name of the uploaded file or address of the page, shown as the subtitle of a source. */
  origin: string;
  status: SourceStatus;
  /** Number of tokens the indexed text was counted as, zero until the status is `READY`. */
  tokenCount: number;
  createdAt: string;
}

const STATUSES: readonly string[] = ['UPLOADED', 'INDEXING', 'READY', 'ERROR'];

const KINDS: readonly string[] = ['FILE', 'WEB'];

/**
 * Reports whether a source is still on its way into the index, which is what the view polls on.
 */
export function isPending(source: Source): boolean {
  return source.status === 'UPLOADED' || source.status === 'INDEXING';
}

/**
 * Narrows a source of a backend response into its client side form.
 */
export function toSource(source: components['schemas']['SourceResponse'] | undefined): Source {
  if (!source) {
    throw new MalformedResponseError('source');
  }
  const status = requireString(source.status, 'source.status');
  const kind = requireString(source.kind, 'source.kind');
  if (!STATUSES.includes(status)) {
    throw new MalformedResponseError('source.status');
  }
  if (!KINDS.includes(kind)) {
    throw new MalformedResponseError('source.kind');
  }
  return {
    id: requireString(source.id, 'source.id'),
    notebookId: requireString(source.notebookId, 'source.notebookId'),
    displayName: requireString(source.displayName, 'source.displayName'),
    kind: kind as SourceKind,
    origin: requireString(source.origin, 'source.origin'),
    status: status as SourceStatus,
    tokenCount: requireNumber(source.tokenCount, 'source.tokenCount'),
    createdAt: requireString(source.createdAt, 'source.createdAt'),
  };
}
