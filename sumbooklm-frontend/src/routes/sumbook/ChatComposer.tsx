import { useState, type KeyboardEvent } from 'react';
import { ArrowUp } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Textarea } from '@/components/ui/textarea';

/**
 * The field a question is written in.
 *
 * Enter sends and shift with enter adds a line, which is what a chat is expected to do and the
 * opposite of what a textarea does on its own. Sending is only offered once the field holds
 * something other than whitespace, so the button never promises to send an empty question.
 */
export function ChatComposer({
  sourceCount,
  onSubmit,
}: {
  sourceCount: number;
  onSubmit: (question: string) => void;
}) {
  const { t } = useTranslation();
  const [question, setQuestion] = useState('');
  const ready = question.trim() !== '';

  function send() {
    if (!ready) {
      return;
    }
    onSubmit(question.trim());
  }

  function keyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      send();
    }
  }

  return (
    <div className="flex flex-col gap-2 rounded-jb-block bg-jb-grey-90/60 p-3 ring-1 ring-jb-grey-70/30 focus-within:ring-jb-grey-50/50">
      <Textarea
        rows={1}
        value={question}
        onChange={(event) => setQuestion(event.target.value)}
        onKeyDown={keyDown}
        placeholder={t('sumbook.chat.placeholder')}
        aria-label={t('sumbook.chat.placeholder')}
        className="max-h-40 min-h-9 resize-none border-0 bg-transparent px-1 py-1.5 text-jb-grey-5 placeholder:text-jb-grey-60 focus-visible:border-0 focus-visible:ring-0 dark:bg-transparent"
      />
      <div className="flex items-center justify-end gap-3">
        <span className="text-xs text-jb-grey-50">{t('sumbook.chat.sources', { count: sourceCount })}</span>
        <button
          type="button"
          disabled={!ready}
          onClick={send}
          aria-label={t('sumbook.chat.send')}
          className={`flex size-9 shrink-0 items-center justify-center rounded-full transition-colors outline-none focus-visible:ring-2 focus-visible:ring-jb-grey-30/40 ${
            ready
              ? 'bg-jb-accent text-white hover:bg-jb-accent-bright'
              : 'cursor-not-allowed bg-jb-grey-80/70 text-jb-grey-60'
          }`}
        >
          <ArrowUp className="size-4" aria-hidden />
        </button>
      </div>
    </div>
  );
}
