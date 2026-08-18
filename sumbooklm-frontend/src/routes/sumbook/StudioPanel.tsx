import { Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * The panel the studio will live in.
 *
 * Nothing is generated from a Sumbook yet, so the panel says what it is for instead of showing an
 * empty frame. It keeps its width in the layout, because a panel that appears once the first feature
 * lands would move everything else at that moment.
 */
export function StudioPanel() {
  const { t } = useTranslation();

  return (
    <section
      aria-labelledby="sumbook-studio"
      className="flex min-h-0 flex-col gap-4 rounded-jb-block bg-jb-grey-95/60 p-4 ring-1 ring-jb-grey-70/25"
    >
      <h2 id="sumbook-studio" className="text-sm font-semibold tracking-wide text-jb-grey-20">
        {t('sumbook.studio.heading')}
      </h2>
      <div className="flex flex-1 flex-col items-center justify-center gap-3 rounded-jb-card border border-dashed border-jb-grey-70/50 px-4 py-8 text-center">
        <span className="flex size-10 items-center justify-center rounded-full bg-jb-grey-90 ring-1 ring-jb-grey-70/50">
          <Sparkles className="size-4 text-jb-grey-30" aria-hidden />
        </span>
        <p className="text-[0.8125rem] leading-5 text-jb-grey-50">{t('sumbook.studio.placeholder')}</p>
      </div>
    </section>
  );
}
