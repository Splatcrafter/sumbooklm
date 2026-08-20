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
 * Builds the line below the title of an opened Sumbook.
 *
 * The order is the reverse of the one on a card: inside a Sumbook the number of sources is what the
 * reader is working with, while the date is context. As on the card, the date is formatted with the
 * locale the interface runs in and the separator belongs to the translated string.
 */
export function useSumbookMeta(): (sourceCount: number, lastActivityAt: string) => string {
  const { t, i18n } = useTranslation();
  const dateFormat = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'short', day: 'numeric' }),
    [i18n.language],
  );

  return useMemo(
    () => (sourceCount: number, lastActivityAt: string) =>
      t('sumbook.header.meta', {
        sources: t('sumbook.header.sources', { count: sourceCount }),
        date: dateFormat.format(new Date(lastActivityAt)),
      }),
    [dateFormat, t],
  );
}
