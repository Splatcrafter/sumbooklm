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

import { NotebookText } from 'lucide-react';

import { cn } from '@/lib/utils';

/**
 * The symbol standing for the subject of a Sumbook.
 *
 * It is rendered as the characters the backend stored, which is the one place where a symbol reaches
 * the interface as data. It stands on its own without a plate behind it, at a size where an emoji
 * reads as an illustration rather than as a glyph in a line of text.
 *
 * Until the backend has derived one, a neutral outline icon is shown instead of a placeholder
 * character, so an unlabelled Sumbook does not look like a labelled one.
 */
export function TopicIcon({
  topicIcon,
  className,
  iconClassName,
}: {
  topicIcon: string;
  className?: string;
  iconClassName?: string;
}) {
  return (
    <span
      aria-hidden
      className={cn('flex items-center justify-center text-4xl leading-none select-none', className)}
    >
      {topicIcon === '' ? (
        <NotebookText className={cn('size-8 text-nb-muted', iconClassName)} />
      ) : (
        topicIcon
      )}
    </span>
  );
}
