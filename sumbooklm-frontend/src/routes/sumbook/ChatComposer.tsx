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

import { useState, type KeyboardEvent } from 'react';
import { ArrowRight, Square } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { Textarea } from '@/components/ui/textarea';

/**
 * The field a question is written in.
 *
 * Enter sends and shift with enter adds a line, which is what a chat is expected to do and the
 * opposite of what a textarea does on its own. Sending is only offered once the field holds
 * something other than whitespace, so the button never promises to send an empty question.
 *
 * The field is cleared as the question leaves it. What was asked is already visible in the
 * conversation above, so leaving it behind would only mean deleting it before asking the next one.
 *
 * While an answer is being written the same button stops it, rather than a second control appearing
 * next to one that cannot be pressed. Sending and stopping are the two things this button ever does,
 * and only one of them is possible at a time.
 */
export function ChatComposer({
  sourceCount,
  disabled = false,
  answering = false,
  onSubmit,
  onStop,
}: {
  sourceCount: number;
  disabled?: boolean;
  answering?: boolean;
  onSubmit: (question: string) => void;
  onStop?: () => void;
}) {
  const { t } = useTranslation();
  const [question, setQuestion] = useState('');
  const ready = !disabled && question.trim() !== '';

  function send() {
    if (!ready) {
      return;
    }
    onSubmit(question.trim());
    setQuestion('');
  }

  function keyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      send();
    }
  }

  return (
    <div className="flex items-end gap-2 rounded-[1.75rem] border border-nb-line bg-nb-inset px-4 py-2.5 transition-colors focus-within:border-nb-muted">
      <Textarea
        rows={1}
        disabled={disabled}
        value={question}
        onChange={(event) => setQuestion(event.target.value)}
        onKeyDown={keyDown}
        placeholder={t('sumbook.chat.placeholder')}
        aria-label={t('sumbook.chat.placeholder')}
        className="max-h-40 min-h-9 flex-1 resize-none border-0 bg-transparent px-0 py-1.5 text-[0.9375rem] leading-6 text-nb-text placeholder:text-nb-muted focus-visible:border-0 focus-visible:ring-0 dark:bg-transparent"
      />
      <span className="shrink-0 pb-2 text-xs text-nb-muted">
        {t('sumbook.chat.sources', { count: sourceCount })}
      </span>
      {answering ? (
        <button
          type="button"
          onClick={onStop}
          aria-label={t('sumbook.chat.stop')}
          className="mb-1 flex size-9 shrink-0 items-center justify-center rounded-full bg-nb-raised text-nb-text transition-colors outline-none hover:brightness-125 focus-visible:ring-2 focus-visible:ring-nb-accent"
        >
          <Square className="size-3 fill-current" aria-hidden />
        </button>
      ) : (
        <button
          type="button"
          disabled={!ready}
          onClick={send}
          aria-label={t('sumbook.chat.send')}
          className={`mb-1 flex size-9 shrink-0 items-center justify-center rounded-full transition-colors outline-none focus-visible:ring-2 focus-visible:ring-nb-accent ${
            ready
              ? 'bg-nb-primary text-nb-on-primary hover:brightness-90'
              : 'cursor-not-allowed bg-nb-raised text-nb-faint'
          }`}
        >
          <ArrowRight className="size-4" aria-hidden />
        </button>
      )}
    </div>
  );
}
