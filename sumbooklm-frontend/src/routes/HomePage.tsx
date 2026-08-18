import { useTranslation } from 'react-i18next';

export function HomePage() {
  const { t } = useTranslation();

  return <p className="text-sm">{t('app.tagline')}</p>;
}
