import { Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * The panel the studio will live in.
 *
 * Nothing is generated from a Sumbook yet, so the panel says what it is for instead of showing an
 * empty frame or a row of controls that lead nowhere. It keeps its width in the layout, because a
 * panel that appears once the first feature lands would move everything else at that moment.
 */
export function StudioPanel() {
  const { t } = useTranslation();

  return (
    <section
      aria-labelledby="sumbook-studio"
      className="flex min-h-0 flex-1 flex-col gap-4 rounded-nb-panel bg-nb-surface p-4"
    >
      <h2 id="sumbook-studio" className="text-base leading-6 font-medium text-nb-text">
        {t('sumbook.studio.heading')}
      </h2>
      <div className="flex flex-1 flex-col items-center justify-center gap-3 rounded-nb-tile bg-nb-inset px-4 py-10 text-center">
        <span className="flex size-10 items-center justify-center rounded-full bg-nb-raised">
          <Sparkles className="size-4.5 text-nb-body" aria-hidden />
        </span>
        <p className="max-w-56 text-[0.8125rem] leading-5 text-nb-muted">
          {t('sumbook.studio.placeholder')}
        </p>
      </div>
    </section>
  );
}
