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
 * Reason a source could not be indexed.
 *
 * The backend reports a cause rather than a message, so that what the user reads is written here and
 * in their own language. An unknown value is narrowed to `UNEXPECTED`, which is what a client of an
 * older version does with a cause that was added after it.
 */
export type SourceFailure =
  | 'NONE'
  | 'BLOCKED'
  | 'UNREACHABLE'
  | 'UNREADABLE'
  | 'EMPTY'
  | 'TOO_LARGE'
  | 'UNEXPECTED';

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
  /** Reason the source could not be indexed, `NONE` unless the status is `ERROR`. */
  failure: SourceFailure;
  /** Point in time the source was last read, absent while it never was. */
  indexedAt: string | null;
  createdAt: string;
}

const STATUSES: readonly string[] = ['UPLOADED', 'INDEXING', 'READY', 'ERROR'];

const KINDS: readonly string[] = ['FILE', 'WEB'];

const FAILURES: readonly string[] = [
  'NONE',
  'BLOCKED',
  'UNREACHABLE',
  'UNREADABLE',
  'EMPTY',
  'TOO_LARGE',
  'UNEXPECTED',
];

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
    failure: FAILURES.includes(source.failure ?? '') ? (source.failure as SourceFailure) : 'UNEXPECTED',
    indexedAt: source.indexedAt ?? null,
    createdAt: requireString(source.createdAt, 'source.createdAt'),
  };
}
