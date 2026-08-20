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
 * The tints a Sumbook card can take.
 *
 * One family of desaturated tones at a single lightness, so that a shelf of cards reads as varied
 * without any of them shouting, and so that the same text colour is legible on every one of them.
 */
const TINTS = [
  'bg-nb-tint-olive',
  'bg-nb-tint-clay',
  'bg-nb-tint-moss',
  'bg-nb-tint-steel',
  'bg-nb-tint-iris',
  'bg-nb-tint-rose',
] as const;

/**
 * Picks the tint of a Sumbook from its identifier.
 *
 * The choice is derived rather than stored, so a card keeps its colour across sessions and devices
 * without the backend having to carry a field for it. It is a display detail, and the day it becomes
 * something a user chooses, it becomes a real column instead.
 */
export function notebookTint(notebookId: string): string {
  let hash = 0;
  for (let index = 0; index < notebookId.length; index += 1) {
    hash = (hash * 31 + notebookId.charCodeAt(index)) % 100000;
  }
  return TINTS[hash % TINTS.length];
}
