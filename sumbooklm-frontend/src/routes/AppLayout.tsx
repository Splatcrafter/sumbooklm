import { Outlet, Link } from 'react-router';
import { useTranslation } from 'react-i18next';

import { useAuth } from '@/auth/useAuth';
import { Button } from '@/components/ui/button';

/**
 * Frame of every route, carrying the application title and the state of the current visitor.
 */
export function AppLayout() {
  const { t } = useTranslation();
  const { status, user, logout } = useAuth();

  return (
    <div className="flex min-h-full flex-col">
      <header className="flex items-center justify-between gap-4 border-b px-6 py-4">
        <Link to="/" className="text-lg font-semibold">
          {t('app.name')}
        </Link>
        {status === 'authenticated' && user ? (
          <div className="flex items-center gap-3">
            <span className="text-sm text-muted-foreground">
              {t('account.signedInAs', { username: user.username })}
            </span>
            <Button variant="outline" size="sm" onClick={() => void logout()}>
              {t('account.signOut')}
            </Button>
          </div>
        ) : null}
        {status === 'anonymous' ? (
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" render={<Link to="/account/login" />}>
              {t('account.login.submit')}
            </Button>
            <Button size="sm" render={<Link to="/account/register" />}>
              {t('account.register.submit')}
            </Button>
          </div>
        ) : null}
      </header>
      <main className="flex-1 px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}
