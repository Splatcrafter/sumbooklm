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

import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useModelSettings } from '@/byok/useModelSettings';
import { useChat } from '@/chat/useChat';
import { Button } from '@/components/ui/button';
import type { Notebook } from '@/notebooks/notebook';
import { TopicIcon } from '@/notebooks/TopicIcon';
import { ChatComposer } from '@/routes/sumbook/ChatComposer';
import { ChatMessageView } from '@/routes/sumbook/ChatMessageView';
import { ConversationBar } from '@/routes/sumbook/ConversationBar';
import { SummaryView } from '@/routes/sumbook/SummaryView';
import { useSumbookMeta } from '@/routes/sumbook/SumbookMeta';
import { ModelSettingsDialog } from '@/routes/settings/ModelSettingsDialog';

/**
 * The middle panel, where the Sumbook is asked about its sources.
 *
 * An empty conversation shows what the Sumbook is instead of an empty box: its symbol, its name, how
 * much it is grounded in, and the summary of what it holds. As soon as something has been asked, that
 * space belongs to the conversation.
 *
 * The content is held to a reading measure and centred in whatever width the panel has, because a
 * line of text stretched across a desk monitor is hard to read at any size.
 *
 * Asking needs a model, and this browser is the only place one is configured. A Sumbook that has none
 * therefore says so where the question would be typed, with the way to fix it right there, rather
 * than letting the question be sent and rejected.
 */
export function ChatPanel({
  notebook,
  sourceCount,
  summarisable,
}: {
  notebook: Notebook;
  sourceCount: number;
  /** Whether every source has been read, which is when a summary can be written from all of them. */
  summarisable: boolean;
}) {
  const { t } = useTranslation();
  const meta = useSumbookMeta();
  const { settings, configured } = useModelSettings();
  const { status, conversations, currentId, messages, answering, select, start, remove, stop, ask } =
    useChat(notebook.id);
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
      aria-labelledby="sumbook-chat"
      className="flex min-h-0 flex-1 flex-col gap-3 rounded-nb-panel bg-nb-surface p-4"
    >
      <div className="flex items-center justify-between gap-2">
        <h2 id="sumbook-chat" className="text-base leading-6 font-medium text-nb-text">
          {t('sumbook.chat.heading')}
        </h2>
        <ConversationBar
          conversations={conversations}
          currentId={currentId}
          onSelect={(sessionId) => void select(sessionId)}
          onStart={() => void start()}
          onRemove={(sessionId) => void remove(sessionId)}
        />
      </div>

      <div ref={transcript} className="flex min-h-0 flex-1 flex-col overflow-y-auto">
        <div className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-4 px-1 py-2">
          {messages.length === 0 ? (
            <div className="flex flex-1 flex-col justify-center gap-5">
              <TopicIcon topicIcon={notebook.topicIcon} className="text-5xl" iconClassName="size-10" />
              <div className="flex flex-col gap-1.5">
                <h3 className="text-[1.75rem] leading-9 font-medium text-nb-text">{notebook.title}</h3>
                <p className="text-[0.8125rem] leading-5 text-nb-muted">
                  {meta(sourceCount, notebook.lastActivityAt)}
                </p>
              </div>
              {status === 'failed' ? (
                <p className="text-[0.9375rem] leading-7 text-nb-body" role="alert">
                  {t('sumbook.chat.historyFailed')}
                </p>
              ) : (
                <SummaryView
                  notebookId={notebook.id}
                  sourceCount={sourceCount}
                  summarisable={summarisable}
                />
              )}
            </div>
          ) : (
            messages.map((message) => <ChatMessageView key={message.key} message={message} />)
          )}
        </div>
      </div>

      <div className="mx-auto flex w-full max-w-2xl flex-col gap-2">
        {configured ? null : (
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-nb-tile bg-nb-inset px-3 py-2">
            <p className="text-xs text-nb-muted">{t('sumbook.chat.noModel')}</p>
            <Button
              variant="outline"
              size="sm"
              className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
              onClick={() => setSettingsOpen(true)}
            >
              {t('sumbook.chat.chooseModel')}
            </Button>
          </div>
        )}
        <ChatComposer
          sourceCount={sourceCount}
          disabled={!configured || answering}
          answering={answering}
          onSubmit={(question) => void ask(question, settings)}
          onStop={() => void stop()}
        />
      </div>

      <ModelSettingsDialog open={settingsOpen} onOpenChange={setSettingsOpen} />
    </section>
  );
}
