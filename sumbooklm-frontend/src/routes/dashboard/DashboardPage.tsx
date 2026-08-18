import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import type { Notebook } from '@/notebooks/notebook';
import { useNotebooks } from '@/notebooks/useNotebooks';
import { NotebookCard } from '@/routes/dashboard/NotebookCard';
import { NotebookCreateCard } from '@/routes/dashboard/NotebookCreateCard';
import { NotebookDeleteDialog } from '@/routes/dashboard/NotebookDeleteDialog';
import { NotebookTitleDialog } from '@/routes/dashboard/NotebookTitleDialog';

const GRID_CLASSES = 'grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4';

/**
 * Overview of the notebooks of the signed-in user.
 *
 * The overview has two sections. Pinned notebooks come first and the section disappears entirely
 * when nothing is pinned, so an empty heading never occupies the top of the screen. Everything not
 * pinned follows below, led by the card that creates a notebook. A notebook appears in one section
 * only, because the same card in two places reads as two notebooks.
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
    <div className="mx-auto w-full max-w-6xl px-6 py-10 sm:px-8 lg:px-10">
      <header className="flex flex-col gap-1.5">
        <h1 className="text-2xl font-semibold tracking-tight text-jb-grey-5">{t('dashboard.title')}</h1>
        <p className="text-sm leading-6 text-jb-grey-50">{t('dashboard.subtitle')}</p>
      </header>

      {status === 'failed' ? (
        <div className="mt-10 flex flex-col items-start gap-3 rounded-jb-card border border-jb-danger/30 bg-jb-danger/10 px-5 py-4">
          <p className="text-sm text-jb-danger">{t('dashboard.errors.load')}</p>
          <Button
            variant="outline"
            className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
            onClick={() => void reload()}
          >
            {t('dashboard.actions.retry')}
          </Button>
        </div>
      ) : null}

      {status === 'loading' ? (
        <p className="mt-10 text-sm text-jb-grey-50">{t('dashboard.loading')}</p>
      ) : null}

      {status === 'ready' ? (
        <>
          {pinned.length > 0 ? (
            <section className="mt-10" aria-labelledby="pinned-sumbooks">
              <h2
                id="pinned-sumbooks"
                className="mb-4 text-sm font-semibold tracking-wide text-jb-grey-40"
              >
                {t('dashboard.sections.pinned')}
              </h2>
              <div className={GRID_CLASSES}>{cards(pinned)}</div>
            </section>
          ) : null}

          <section className="mt-10" aria-labelledby="recent-sumbooks">
            <h2
              id="recent-sumbooks"
              className="mb-4 text-sm font-semibold tracking-wide text-jb-grey-40"
            >
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
  );
}
