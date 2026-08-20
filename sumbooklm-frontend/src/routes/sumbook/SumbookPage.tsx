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

import { useState, type ReactNode } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import { useNotebook } from '@/notebooks/useNotebook';
import { AddSourceDialog } from '@/routes/sumbook/AddSourceDialog';
import { ChatPanel } from '@/routes/sumbook/ChatPanel';
import { SourceTable } from '@/routes/sumbook/SourceTable';
import { useSources } from '@/sources/useSources';

/**
 * One opened Sumbook.
 *
 * The screen is two panels floating on the page with a gap between them: the sources on the left, the
 * conversation beside them. Only the conversation grows, because it is the one a visitor is here for
 * and the one whose content has no natural width, while the sources are a list.
 *
 * The title of the Sumbook stands in a slim bar above the panels rather than inside one of them, so
 * that it names the whole screen and not the panel it happens to sit in.
 *
 * The narrow layout stacks the panels instead of shrinking them, and the conversation comes first.
 * On a screen showing one panel at a time the order has to be the order of the work.
 */
export function SumbookPage() {
  const { t } = useTranslation();
  const { notebookId } = useParams();
  const id = notebookId ?? '';

  const { status: notebookStatus, notebook, reload: reloadNotebook } = useNotebook(id);
  const {
    status: sourcesStatus,
    sources,
    indexing,
    reload,
    addFile,
    addLink,
    refresh,
    remove,
  } = useSources(id);
  const [adding, setAdding] = useState(false);

  if (notebookStatus === 'loading') {
    return <Centered>{t('sumbook.loading')}</Centered>;
  }

  if (notebookStatus === 'missing') {
    return (
      <Centered>
        <p className="text-sm text-nb-muted">{t('sumbook.errors.missing')}</p>
        <BackToOverview label={t('sumbook.actions.backToOverview')} />
      </Centered>
    );
  }

  if (notebookStatus === 'failed' || !notebook) {
    return (
      <Centered>
        <p className="text-sm text-nb-danger" role="alert">
          {t('sumbook.errors.load')}
        </p>
        <Button
          variant="outline"
          className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
          onClick={() => void reloadNotebook()}
        >
          {t('sumbook.actions.retry')}
        </Button>
      </Centered>
    );
  }

  return (
    <div className="flex min-h-0 w-full flex-1 flex-col gap-3 px-3 pb-3 sm:px-4 sm:pb-4">
      <div className="flex items-center gap-3 pt-1">
        <BackToOverview label={t('sumbook.actions.backToOverview')} />
        <h1 className="min-w-0 truncate text-base leading-6 font-medium text-nb-text">
          {notebook.title}
        </h1>
      </div>

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-3 lg:grid-cols-[19rem_minmax(0,1fr)]">
        <div className="order-2 flex min-h-0 flex-col lg:order-none">
          <SourceTable
            status={sourcesStatus}
            sources={sources}
            onAdd={() => setAdding(true)}
            onRefresh={(sourceId) => void refresh(sourceId)}
            onRemove={(sourceId) => void remove(sourceId)}
            onRetry={() => void reload()}
          />
        </div>

        <div className="order-1 flex min-h-0 min-w-0 flex-col lg:order-none">
          <ChatPanel
            notebook={notebook}
            sourceCount={sources.length}
            summarisable={!indexing && sources.some((source) => source.status === 'READY')}
          />
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
    <div className="mx-auto flex w-full max-w-3xl flex-1 flex-col items-center justify-center gap-4 px-6 py-16 text-center">
      {children}
    </div>
  );
}

function BackToOverview({ label }: { label: string }) {
  return (
    <Link
      to="/"
      aria-label={label}
      className="flex size-9 shrink-0 items-center justify-center rounded-full text-nb-body transition-colors outline-none hover:bg-nb-hover hover:text-nb-text focus-visible:ring-2 focus-visible:ring-nb-accent"
    >
      <ArrowLeft className="size-4.5" aria-hidden />
    </Link>
  );
}
