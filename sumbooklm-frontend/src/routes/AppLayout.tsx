import { Outlet } from 'react-router';
import { useTranslation } from 'react-i18next';

export function AppLayout() {
  const { t } = useTranslation();

  return (
    <div className="flex min-h-full flex-col">
      <header className="border-b px-6 py-4">
        <h1 className="text-lg font-semibold">{t('app.name')}</h1>
      </header>
      <main className="flex-1 px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}
