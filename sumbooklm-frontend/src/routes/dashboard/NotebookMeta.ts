import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * The two facts a drawer of the cabinet carries beside its title.
 *
 * They are returned separately rather than as one line, because the overview sets them in their own
 * columns: a reader compares the number of sources of one Sumbook with the next by running down a
 * column, which only works while both values start at the same edge.
 *
 * The date is formatted with the locale the interface is running in rather than with a fixed
 * pattern, so that a German reader sees a German date without a second translation key.
 */
export function useNotebookMeta(): (lastActivityAt: string, sourceCount: number) => {
  date: string;
  sources: string;
} {
  const { t, i18n } = useTranslation();
  const dateFormat = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'short', day: 'numeric' }),
    [i18n.language],
  );

  return useMemo(
    () => (lastActivityAt: string, sourceCount: number) => ({
      date: dateFormat.format(new Date(lastActivityAt)),
      sources: t('dashboard.card.sources', { count: sourceCount }),
    }),
    [dateFormat, t],
  );
}
