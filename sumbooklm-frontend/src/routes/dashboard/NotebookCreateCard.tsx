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
