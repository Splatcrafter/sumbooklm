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

import { useState } from 'react';
import { Pin, Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import type { Notebook } from '@/notebooks/notebook';
import { useNotebooks } from '@/notebooks/useNotebooks';
import { NotebookCard } from '@/routes/dashboard/NotebookCard';
import { NotebookCreateCard } from '@/routes/dashboard/NotebookCreateCard';
import { NotebookDeleteDialog } from '@/routes/dashboard/NotebookDeleteDialog';
import { NotebookTitleDialog } from '@/routes/dashboard/NotebookTitleDialog';

const GRID_CLASSES =
  'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5';

/**
 * Overview of the Sumbooks of the signed-in user.
 *
 * The overview has two sections. Pinned Sumbooks come first and the section disappears entirely when
 * nothing is pinned, so an empty heading never occupies the top of the screen. Everything not pinned
 * follows below, led by the card that creates one. A Sumbook appears in one section only, because the
 * same card in two places reads as two Sumbooks.
 *
 * Creating is offered twice on purpose: as the leading card of the shelf, where the eye lands when
 * reading the Sumbooks, and as the one filled button of the screen, where a visitor who came to start
 * something looks first.
 */
export function DashboardPage() {
  const { t } = useTranslation();
  const { status, pinned, recent, reload, create, rename, setPinned, remove } = useNotebooks();

  const [creating, setCreating] = useState(false);
  const [renaming, setRenaming] = useState<Notebook | null>(null);
  const [deleting, setDeleting] = useState<Notebook | null>(null);

  function cards(notebooks: Notebook[]) {
    return notebooks.map((notebook) => (
      <NotebookCard
        key={notebook.id}
        notebook={notebook}
        onEditTitle={() => setRenaming(notebook)}
        onTogglePin={() => void setPinned(notebook.id, !notebook.pinned)}
        onDelete={() => setDeleting(notebook)}
      />
    ));
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto bg-nb-surface">
      <div className="mx-auto w-full max-w-[110rem] px-5 py-8 sm:px-8">
        <header className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <h1 className="text-[1.75rem] leading-9 font-medium text-nb-text">
              {t('dashboard.title')}
            </h1>
            <p className="text-[0.8125rem] leading-5 text-nb-muted">{t('dashboard.subtitle')}</p>
          </div>
          <Button
            onClick={() => setCreating(true)}
            className="h-10 rounded-full bg-nb-primary px-5 text-sm font-medium text-nb-on-primary hover:brightness-90"
          >
            <Plus />
            {t('dashboard.create.card')}
          </Button>
        </header>

        {status === 'failed' ? (
          <div className="mt-8 flex flex-col items-start gap-3 rounded-nb-card bg-nb-ground px-5 py-4">
            <p className="text-sm text-nb-danger">{t('dashboard.errors.load')}</p>
            <Button
              variant="outline"
              className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
              onClick={() => void reload()}
            >
              {t('dashboard.actions.retry')}
            </Button>
          </div>
        ) : null}

        {status === 'loading' ? (
          <p className="mt-8 text-sm text-nb-muted">{t('dashboard.loading')}</p>
        ) : null}

        {status === 'ready' ? (
          <>
            {pinned.length > 0 ? (
              <section className="mt-8" aria-labelledby="pinned-sumbooks">
                <h2
                  id="pinned-sumbooks"
                  className="mb-4 flex items-center gap-2 text-sm font-medium text-nb-body"
                >
                  <Pin className="size-4" aria-hidden />
                  {t('dashboard.sections.pinned')}
                </h2>
                <div className={GRID_CLASSES}>{cards(pinned)}</div>
              </section>
            ) : null}

            <section className="mt-8" aria-labelledby="recent-sumbooks">
              <h2 id="recent-sumbooks" className="mb-4 text-sm font-medium text-nb-body">
                {t('dashboard.sections.recent')}
              </h2>
              <div className={GRID_CLASSES}>
                <NotebookCreateCard onClick={() => setCreating(true)} />
                {cards(recent)}
              </div>
            </section>
          </>
        ) : null}

        <NotebookTitleDialog
          open={creating}
          onOpenChange={setCreating}
          heading={t('dashboard.create.heading')}
          description={t('dashboard.create.description')}
          submitLabel={t('dashboard.create.confirm')}
          initialTitle=""
          onSubmit={create}
        />

        <NotebookTitleDialog
          open={renaming !== null}
          onOpenChange={(open) => {
            if (!open) {
              setRenaming(null);
            }
          }}
          heading={t('dashboard.rename.heading')}
          description={t('dashboard.rename.description')}
          submitLabel={t('dashboard.rename.confirm')}
          initialTitle={renaming?.title ?? ''}
          onSubmit={async (title) => {
            if (renaming) {
              await rename(renaming.id, title);
            }
          }}
        />

        <NotebookDeleteDialog
          open={deleting !== null}
          onOpenChange={(open) => {
            if (!open) {
              setDeleting(null);
            }
          }}
          title={deleting?.title ?? ''}
          onConfirm={async () => {
            if (deleting) {
              await remove(deleting.id);
            }
          }}
        />
      </div>
    </div>
  );
}
