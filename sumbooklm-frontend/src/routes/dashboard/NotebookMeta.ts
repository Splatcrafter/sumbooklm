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

import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * The two facts a drawer of the cabinet carries beside its title.
 *
 * They are returned separately rather than as one line, because the overview sets them in their own
 * columns: a reader compares the number of sources of one Sumbook with the next by running down a
 * column, which only works while both values start at the same edge.
 *
 * The date is formatted with the locale the interface is running in rather than with a fixed
 * pattern, so that a German reader sees a German date without a second translation key.
 */
export function useNotebookMeta(): (lastActivityAt: string, sourceCount: number) => {
  date: string;
  sources: string;
} {
  const { t, i18n } = useTranslation();
  const dateFormat = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'short', day: 'numeric' }),
    [i18n.language],
  );

  return useMemo(
    () => (lastActivityAt: string, sourceCount: number) => ({
      date: dateFormat.format(new Date(lastActivityAt)),
      sources: t('dashboard.card.sources', { count: sourceCount }),
    }),
    [dateFormat, t],
  );
}
