import { useState, type ReactNode } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import { useNotebook } from '@/notebooks/useNotebook';
import { AddSourceDialog } from '@/routes/sumbook/AddSourceDialog';
import { ChatPanel } from '@/routes/sumbook/ChatPanel';
import { SourcesPanel } from '@/routes/sumbook/SourcesPanel';
import { StudioPanel } from '@/routes/sumbook/StudioPanel';
import { useSources } from '@/sources/useSources';

/**
 * One opened Sumbook.
 *
 * The screen is three panels: what the Sumbook is grounded in on the left, the conversation about it
 * in the middle, and what will be generated from it on the right. Only the middle one grows, because
 * it is the one whose content has no natural width, while the two beside it are lists.
 *
 * The narrow layout stacks them instead of shrinking them. Three columns on a phone would leave the
 * middle one too narrow to read, and the studio has nothing in it yet, so it is the panel that
 * disappears first.
 */
export function SumbookPage() {
  const { t } = useTranslation();
  const { notebookId } = useParams();
  const id = notebookId ?? '';

  const { status: notebookStatus, notebook, reload: reloadNotebook } = useNotebook(id);
  const { status: sourcesStatus, sources, reload, addFile, addLink, reindex, remove } = useSources(id);
  const [adding, setAdding] = useState(false);

  if (notebookStatus === 'loading') {
    return <Centered>{t('sumbook.loading')}</Centered>;
  }

  if (notebookStatus === 'missing') {
    return (
      <Centered>
        <p className="text-sm text-jb-grey-40">{t('sumbook.errors.missing')}</p>
        <BackToOverview label={t('sumbook.actions.backToOverview')} />
      </Centered>
    );
  }

  if (notebookStatus === 'failed' || !notebook) {
    return (
      <Centered>
        <p className="text-sm text-jb-danger" role="alert">
          {t('sumbook.errors.load')}
        </p>
        <Button
          variant="outline"
          className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
          onClick={() => void reloadNotebook()}
        >
          {t('sumbook.actions.retry')}
        </Button>
      </Centered>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-[110rem] flex-1 flex-col gap-4 px-4 py-4 sm:px-6 lg:px-8">
      <BackToOverview label={t('sumbook.actions.backToOverview')} />
      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 lg:grid-cols-[18rem_minmax(0,1fr)] xl:grid-cols-[18rem_minmax(0,1fr)_20rem]">
        <SourcesPanel
          status={sourcesStatus}
          sources={sources}
          onAdd={() => setAdding(true)}
          onReindex={(sourceId) => void reindex(sourceId)}
          onRemove={(sourceId) => void remove(sourceId)}
          onRetry={() => void reload()}
        />
        <ChatPanel notebook={notebook} sourceCount={sources.length} />
        <div className="hidden xl:flex xl:min-h-0 xl:flex-col">
          <StudioPanel />
        </div>
      </div>

      <AddSourceDialog
        open={adding}
        onOpenChange={setAdding}
        onAddFile={addFile}
        onAddLink={addLink}
      />
    </div>
  );
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col items-center justify-center gap-4 px-6 py-16 text-center">
      {children}
    </div>
  );
}

function BackToOverview({ label }: { label: string }) {
  return (
    <Link
      to="/"
      className="inline-flex w-fit items-center gap-1.5 text-[0.8125rem] text-jb-grey-50 transition-colors outline-none hover:text-jb-grey-20 focus-visible:text-jb-grey-20"
    >
      <ArrowLeft className="size-4" aria-hidden />
      {label}
    </Link>
  );
}
