import { useEffect, useRef, useState } from 'react';
import { Copy } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { useModelSettings } from '@/byok/useModelSettings';
import { useChat } from '@/chat/useChat';
import { Button } from '@/components/ui/button';
import type { Notebook } from '@/notebooks/notebook';
import { TopicIcon } from '@/notebooks/TopicIcon';
import { ChatComposer } from '@/routes/sumbook/ChatComposer';
import { ChatMessageView } from '@/routes/sumbook/ChatMessageView';
import { useSumbookMeta } from '@/routes/sumbook/SumbookMeta';
import { ModelSettingsDialog } from '@/routes/settings/ModelSettingsDialog';

/**
 * The middle panel, where the Sumbook is asked about its sources.
 *
 * An empty conversation shows what the Sumbook is instead of an empty box, and the summary that will
 * stand there is announced rather than left out. As soon as something has been asked, that space
 * belongs to the conversation: it is what the reader came back for, and it is the part that grows.
 *
 * Asking needs a model, and this browser is the only place one is configured. A Sumbook that has none
 * therefore says so where the question would be typed, with the way to fix it right there, rather
 * than letting the question be sent and rejected.
 */
export function ChatPanel({ notebook, sourceCount }: { notebook: Notebook; sourceCount: number }) {
  const { t } = useTranslation();
  const meta = useSumbookMeta();
  const { settings, configured } = useModelSettings();
  const { status, messages, answering, ask } = useChat(notebook.id);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const transcript = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const element = transcript.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages]);

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

      <div ref={transcript} className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto">
        {messages.length === 0 ? (
          <div className="flex flex-1 flex-col justify-center gap-4">
            <p className="max-w-2xl text-sm leading-6 text-jb-grey-40">
              {status === 'failed'
                ? t('sumbook.chat.historyFailed')
                : sourceCount === 0
                  ? t('sumbook.summary.noSources')
                  : t('sumbook.summary.pending')}
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
        ) : (
          messages.map((message) => <ChatMessageView key={message.key} message={message} />)
        )}
      </div>

      <div className="flex flex-col gap-2">
        {configured ? null : (
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-jb-card bg-jb-grey-90/50 px-3 py-2 ring-1 ring-jb-grey-70/30">
            <p className="text-xs text-jb-grey-40">{t('sumbook.chat.noModel')}</p>
            <Button
              variant="outline"
              size="sm"
              className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-80/40 hover:text-jb-grey-5"
              onClick={() => setSettingsOpen(true)}
            >
              {t('sumbook.chat.chooseModel')}
            </Button>
          </div>
        )}
        <ChatComposer
          sourceCount={sourceCount}
          disabled={!configured || answering}
          onSubmit={(question) => void ask(question, settings)}
        />
      </div>

      <ModelSettingsDialog open={settingsOpen} onOpenChange={setSettingsOpen} />
    </section>
  );
}
