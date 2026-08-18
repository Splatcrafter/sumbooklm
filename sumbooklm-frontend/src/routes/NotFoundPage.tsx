import { useTranslation } from 'react-i18next';

/**
 * Answer of the application shell to a path that belongs to no route.
 */
export function NotFoundPage() {
  const { t } = useTranslation();

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-10 sm:px-8 lg:px-10">
      <p className="text-sm text-jb-grey-50">{t('errors.routeNotFound')}</p>
    </div>
  );
}
