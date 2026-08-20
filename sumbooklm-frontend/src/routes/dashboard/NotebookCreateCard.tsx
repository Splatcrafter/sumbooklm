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

import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * The card that starts a new Sumbook.
 *
 * It keeps the height and the shape of a filled card and takes the plain surface instead of a tint,
 * so it reads as the empty slot at the front of the shelf rather than as another Sumbook. It always
 * holds the first position, so the action a visitor came to the overview for does not move as
 * Sumbooks accumulate.
 *
 * Because that surface is the same colour as the overview behind it, this is the one card in the
 * interface that carries an outline. Without it the card has no edge at all and the shelf appears to
 * start with a gap.
 */
export function NotebookCreateCard({ onClick }: { onClick: () => void }) {
  const { t } = useTranslation();

  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-56 flex-col items-center justify-center gap-3 rounded-nb-card border border-nb-outline bg-nb-surface p-4 text-center transition-colors outline-none hover:border-nb-muted hover:bg-nb-hover focus-visible:border-nb-accent focus-visible:ring-2 focus-visible:ring-nb-accent"
    >
      <span className="flex size-12 items-center justify-center rounded-full bg-nb-raised">
        <Plus className="size-6 text-nb-text" aria-hidden />
      </span>
      <span className="text-sm font-medium text-nb-body">{t('dashboard.create.card')}</span>
    </button>
  );
}
