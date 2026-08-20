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

import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { BrandMark } from '@/routes/account/BrandMark';

/**
 * Stacked cards that carry an account form.
 *
 * The form and the way out of it are two surfaces rather than one, which is how the JetBrains website
 * composes its blocks: the primary card holds the task, a lighter card below it holds what comes next.
 * Everything readable lives on one of the two, because text placed directly on the background
 * competes with it.
 *
 * Both surfaces are translucent so the waves behind them stay perceptible, and the double edge, a
 * dark border with a light inset hairline, is what keeps them legible on a moving backdrop.
 *
 * The primary card reserves more height than the longest form needs and pushes the submit button to
 * its lower edge, so moving between sign-in and registration changes the content of the card and not
 * its size. The reserve has to exceed the content rather than match it: as soon as one form is taller
 * than the reserve, that form decides its own height and the two differ again. Every line box inside
 * the card is a whole number of pixels for the same reason, because a fractional height rounds
 * differently than the reserve does.
 *
 * The reserve applies from the small breakpoint upwards. On a phone the screen is short enough that
 * held-open space costs more than the size change it prevents.
 */
export function AuthCard({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
}) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col gap-3">
      <section className="flex flex-col rounded-3xl border border-nb-line/70 bg-nb-surface/90 p-7 shadow-[0_28px_70px_-20px_rgb(0_0_0/0.85)] backdrop-blur-2xl sm:min-h-[32rem]">
        <div className="mb-6 flex items-center gap-3">
          <BrandMark />
          <span className="text-base font-medium text-nb-text">{t('app.name')}</span>
        </div>
        <header className="flex flex-col gap-1.5">
          <h1 className="text-2xl leading-8 font-medium text-nb-text">{title}</h1>
          <p className="text-sm leading-6 text-nb-muted">{subtitle}</p>
        </header>
        <div className="mt-6 flex flex-1 flex-col">{children}</div>
      </section>
      <aside className="rounded-3xl border border-nb-line/70 bg-nb-surface/80 px-5 py-3.5 text-sm text-nb-muted shadow-[0_16px_40px_-24px_rgb(0_0_0/0.8)] backdrop-blur-xl">
        {footer}
      </aside>
    </div>
  );
}
