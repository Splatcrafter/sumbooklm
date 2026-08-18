import { Plus } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * The card that starts a new notebook.
 *
 * It always occupies the first position of the overview, so the action a user comes to the dashboard
 * for sits where their eye starts and does not move as notebooks are added. The dashed edge and the
 * missing fill mark it as an action rather than as an empty notebook.
 */
export function NotebookCreateCard({ onClick }: { onClick: () => void }) {
  const { t } = useTranslation();

  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-40 flex-col items-center justify-center gap-3 rounded-jb-card border border-dashed border-jb-grey-70/70 bg-jb-grey-95/30 p-4 text-center transition-colors outline-none hover:border-jb-grey-50 hover:bg-jb-grey-95/60 focus-visible:border-jb-grey-40 focus-visible:ring-3 focus-visible:ring-jb-grey-30/20"
    >
      <span className="flex size-11 items-center justify-center rounded-full bg-jb-grey-90 ring-1 ring-jb-grey-70/50">
        <Plus className="size-5 text-jb-grey-20" aria-hidden />
      </span>
      <span className="text-sm font-medium text-jb-grey-20">{t('dashboard.create.card')}</span>
    </button>
  );
}
