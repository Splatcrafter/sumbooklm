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

import { MalformedResponseError, requireBoolean, requireNumber, requireString, requireText } from '@/api/narrowing';
import type { components } from '@/api/schema';

/**
 * A workspace of the signed-in user, presented as a Sumbook.
 *
 * The product name lives in the localisation files only. Code, like the backend, calls the unit a
 * notebook, so that renaming the product never means renaming identifiers.
 */
export interface Notebook {
  id: string;
  title: string;
  pinned: boolean;
  /**
   * Characters standing for the subject of the notebook. Empty until the backend has derived them,
   * which is why every view has to provide something of its own for that case.
   */
  topicIcon: string;
  createdAt: string;
  lastActivityAt: string;
  sourceCount: number;
}

/**
 * Narrows a notebook of a backend response into its client side form.
 */
export function toNotebook(notebook: components['schemas']['NotebookResponse'] | undefined): Notebook {
  if (!notebook) {
    throw new MalformedResponseError('notebook');
  }
  return {
    id: requireString(notebook.id, 'notebook.id'),
    title: requireString(notebook.title, 'notebook.title'),
    pinned: requireBoolean(notebook.pinned, 'notebook.pinned'),
    topicIcon: requireText(notebook.topicIcon, 'notebook.topicIcon'),
    createdAt: requireString(notebook.createdAt, 'notebook.createdAt'),
    lastActivityAt: requireString(notebook.lastActivityAt, 'notebook.lastActivityAt'),
    sourceCount: requireNumber(notebook.sourceCount, 'notebook.sourceCount'),
  };
}
