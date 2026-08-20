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
