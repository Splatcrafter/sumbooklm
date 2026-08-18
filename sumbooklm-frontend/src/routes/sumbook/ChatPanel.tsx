import { useState } from 'react';
import { Copy } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/components/ui/button';
import type { Notebook } from '@/notebooks/notebook';
import { TopicIcon } from '@/notebooks/TopicIcon';
import { ChatComposer } from '@/routes/sumbook/ChatComposer';
import { useSumbookMeta } from '@/routes/sumbook/SumbookMeta';

/**
 * The middle panel, where the Sumbook is summarised and asked questions.
 *
 * The summary is not written yet, so the panel says that instead of showing an empty block, and the
 * button that would copy it is offered as disabled rather than left out: a control that appears only
 * once the feature lands moves everything around it at that moment.
 *
 * Asking is already wired up as far as it can be. The field behaves the way it will behave, and a
 * submitted question is answered with the one honest answer available, which is that answering does
 * not exist yet.
 */
export function ChatPanel({ notebook, sourceCount }: { notebook: Notebook; sourceCount: number }) {
  const { t } = useTranslation();
  const meta = useSumbookMeta();
  const [asked, setAsked] = useState(false);

  return (
    <section
      aria-labelledby="sumbook-title"
      className="flex min-h-0 flex-col gap-4 rounded-jb-block bg-jb-grey-95/60 p-5 ring-1 ring-jb-grey-70/25"
    >
      <header className="flex flex-col items-start gap-3">
        <TopicIcon topicIcon={notebook.topicIcon} className="size-12 text-2xl" iconClassName="size-6" />
        <div className="flex flex-col gap-1">
          <h1 id="sumbook-title" className="text-2xl font-semibold tracking-tight text-jb-grey-5">
            {notebook.title}
          </h1>
          <p className="text-[0.8125rem] leading-5 text-jb-grey-50">
            {meta(sourceCount, notebook.lastActivityAt)}
          </p>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col justify-center gap-4 overflow-y-auto">
        <p className="max-w-2xl text-sm leading-6 text-jb-grey-40">
          {sourceCount === 0 ? t('sumbook.summary.noSources') : t('sumbook.summary.pending')}
        </p>
        <div className="flex">
          <Button
            variant="outline"
            size="sm"
            disabled
            className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-30 disabled:opacity-45"
          >
            <Copy />
            {t('sumbook.summary.copy')}
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <ChatComposer sourceCount={sourceCount} onSubmit={() => setAsked(true)} />
        {asked ? (
          <p className="text-xs text-jb-grey-50" role="status">
            {t('sumbook.chat.notAvailable')}
          </p>
        ) : null}
      </div>
    </section>
  );
}
