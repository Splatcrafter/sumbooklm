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
 * Class names shared by the account forms.
 *
 * The semantic theme variables are mapped onto the palette once, in the stylesheet, so these classes
 * only carry what an account form needs beyond a primitive's default: the field height the cards
 * reserve their space for, and the pinned submit button.
 */
export const authInputClasses =
  'h-11 rounded-nb-tile border-nb-line bg-nb-inset px-3.5 text-nb-text ' +
  'placeholder:text-nb-muted focus-visible:border-nb-accent focus-visible:ring-0 dark:bg-nb-inset';

export const authLabelClasses = 'text-[0.8125rem] font-medium text-nb-body';

// The auto margin pins the button to the lower edge of the card, so the height the card reserves for
// the longest form shows up as spacing above the button rather than as a gap in the middle.
export const authSubmitClasses =
  'mt-auto h-11 w-full rounded-full bg-nb-primary font-medium text-nb-on-primary hover:brightness-90 ' +
  'focus-visible:ring-nb-accent disabled:opacity-45';

export const authErrorClasses =
  'rounded-nb-tile bg-nb-inset px-3.5 py-2.5 text-sm text-nb-danger';

export const authLinkClasses =
  'font-medium text-nb-body underline decoration-nb-faint underline-offset-4 ' +
  'transition-colors hover:text-nb-text hover:decoration-nb-body';
