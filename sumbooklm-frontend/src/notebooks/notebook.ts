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
