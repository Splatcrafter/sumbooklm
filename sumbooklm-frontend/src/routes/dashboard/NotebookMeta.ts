import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * Builds the line below the title of a notebook card.
 *
 * The date is formatted with the locale the interface is running in rather than with a fixed
 * pattern, so that a German reader sees a German date without a second translation key. The
 * separator between the date and the source count belongs to the translated string, because it is
 * punctuation and punctuation is part of a language.
 */
export function useNotebookMeta(): (lastActivityAt: string, sourceCount: number) => string {
  const { t, i18n } = useTranslation();
  const dateFormat = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'short', day: 'numeric' }),
    [i18n.language],
  );

  return useMemo(
    () => (lastActivityAt: string, sourceCount: number) =>
      t('dashboard.card.meta', {
        date: dateFormat.format(new Date(lastActivityAt)),
        sources: t('dashboard.card.sources', { count: sourceCount }),
      }),
    [dateFormat, t],
  );
}
