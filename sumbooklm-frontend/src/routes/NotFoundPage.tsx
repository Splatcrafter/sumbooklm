import { useTranslation } from 'react-i18next';

export function NotFoundPage() {
  const { t } = useTranslation();

  return <p className="text-sm">{t('errors.routeNotFound')}</p>;
}
