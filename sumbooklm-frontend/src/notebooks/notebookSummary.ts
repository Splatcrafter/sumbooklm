import { requireBoolean, requireText, MalformedResponseError } from '@/api/narrowing';
import type { components } from '@/api/schema';

/**
 * The text a model wrote about the sources of one Sumbook.
 *
 * An empty text means that none has been written yet, which is a different thing from one that no
 * longer fits the sources: the first is something to write, the second something to write again, and
 * both cost the reader a request to their own provider.
 */
export interface NotebookSummary {
  text: string;
  stale: boolean;
}

/**
 * Narrows a summary of a backend response into its client side form.
 */
export function toNotebookSummary(
  summary: components['schemas']['NotebookSummaryResponse'] | undefined,
): NotebookSummary {
  if (!summary) {
    throw new MalformedResponseError('summary');
  }
  return {
    text: requireText(summary.text, 'summary.text'),
    stale: requireBoolean(summary.stale, 'summary.stale'),
  };
}
