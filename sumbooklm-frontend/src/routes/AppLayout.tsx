import { useState } from 'react';
import { Cpu } from 'lucide-react';
import { Outlet, Navigate, Link } from 'react-router';
import { useTranslation } from 'react-i18next';

import { useAuth } from '@/auth/useAuth';
import { useModelSettings } from '@/byok/useModelSettings';
import { Button } from '@/components/ui/button';
import { BrandMark } from '@/routes/account/BrandMark';
import { ModelSettingsDialog } from '@/routes/settings/ModelSettingsDialog';

/**
 * Frame of every route that requires a signed-in user.
 *
 * The shell carries the dark surface of the account screens without their moving background: those
 * screens are a destination a visitor looks at, while this one is a place they work in, and a
 * backdrop that keeps changing behind the material competes with it.
 *
 * Restoring a stored session takes a request, so the shell waits for that to finish before it
 * decides between the application and the sign-in screen. Rendering the application first and
 * redirecting afterwards would show the dashboard to a visitor who turns out not to be signed in.
 *
 * The model a visitor answers with is reached from here rather than from inside a Sumbook. It is one
 * setting for the whole browser, and putting it next to the account it is stored with is what says
 * so.
 */
export function AppLayout() {
  const { t } = useTranslation();
  const { status, user, logout } = useAuth();
  const { settings, configured } = useModelSettings();
  const [settingsOpen, setSettingsOpen] = useState(false);

  if (status === 'restoring') {
    return (
      <div className="dark flex min-h-svh items-center justify-center bg-nb-ground text-sm text-nb-muted">
        {t('app.restoring')}
      </div>
    );
  }

  if (status === 'anonymous') {
    return <Navigate to="/account/login" replace />;
  }

  return (
    <div className="dark flex h-svh flex-col overflow-hidden bg-nb-ground text-nb-text">
      <header className="shrink-0">
        <div className="flex w-full items-center justify-between gap-4 px-4 py-3 sm:px-5">
          <Link to="/" className="flex items-center gap-3">
            <BrandMark />
            <span className="text-base font-medium text-nb-text">{t('app.name')}</span>
          </Link>
          {user ? (
            <div className="flex items-center gap-3">
              <span className="hidden text-[0.8125rem] text-nb-muted sm:inline">
                {t('account.signedInAs', { username: user.username })}
              </span>
              <Button
                variant="outline"
                size="sm"
                className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
                onClick={() => setSettingsOpen(true)}
              >
                <Cpu aria-hidden />
                <span className="max-w-40 truncate">
                  {configured ? settings.model : t('settings.model.unset')}
                </span>
              </Button>
              <Button
                variant="outline"
                size="sm"
                className="rounded-full border-nb-line bg-transparent text-nb-body hover:bg-nb-hover hover:text-nb-text"
                onClick={() => void logout()}
              >
                {t('account.signOut')}
              </Button>
            </div>
          ) : null}
        </div>
      </header>
      <main className="flex min-h-0 flex-1 flex-col">
        <Outlet />
      </main>
      <ModelSettingsDialog open={settingsOpen} onOpenChange={setSettingsOpen} />
    </div>
  );
}
