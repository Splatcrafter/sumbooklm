import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * Builds the line below the title of an opened Sumbook.
 *
 * The order is the reverse of the one on a card: inside a Sumbook the number of sources is what the
 * reader is working with, while the date is context. As on the card, the date is formatted with the
 * locale the interface runs in and the separator belongs to the translated string.
 */
export function useSumbookMeta(): (sourceCount: number, lastActivityAt: string) => string {
  const { t, i18n } = useTranslation();
  const dateFormat = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { year: 'numeric', month: 'short', day: 'numeric' }),
    [i18n.language],
  );

  return useMemo(
    () => (sourceCount: number, lastActivityAt: string) =>
      t('sumbook.header.meta', {
        sources: t('sumbook.header.sources', { count: sourceCount }),
        date: dateFormat.format(new Date(lastActivityAt)),
      }),
    [dateFormat, t],
  );
}
