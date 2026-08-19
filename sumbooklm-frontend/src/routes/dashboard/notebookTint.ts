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
