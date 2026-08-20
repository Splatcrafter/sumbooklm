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

/**
 * Compact identity mark of the application.
 *
 * The three stacked bars repeat the motif of the background: sources that settle into layers. It is
 * drawn inline rather than loaded, so it inherits the surrounding colours and costs no request.
 */
export function BrandMark() {
  return (
    <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-nb-raised text-nb-accent">
      <svg viewBox="0 0 20 20" className="size-5" fill="none" aria-hidden>
        <rect x="3" y="4" width="14" height="2.4" rx="1.2" fill="currentColor" opacity="0.95" />
        <rect x="3" y="8.8" width="10" height="2.4" rx="1.2" fill="currentColor" opacity="0.6" />
        <rect x="3" y="13.6" width="6" height="2.4" rx="1.2" fill="currentColor" opacity="0.33" />
      </svg>
    </span>
  );
}
