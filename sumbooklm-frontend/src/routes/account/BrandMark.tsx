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
