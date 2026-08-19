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
 * backdrop that keeps changing behind a grid of cards competes with the cards. What connects the two
 * is the palette and the single accent glow at the top edge.
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
      <div className="dark flex min-h-svh items-center justify-center bg-jb-black text-sm text-jb-grey-50">
        {t('app.restoring')}
      </div>
    );
  }

  if (status === 'anonymous') {
    return <Navigate to="/account/login" replace />;
  }

  return (
    <div className="dark relative isolate flex min-h-svh flex-col bg-jb-black text-jb-grey-10">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-80 bg-[radial-gradient(60rem_28rem_at_18%_-6rem,rgb(75_18_168/0.5),transparent_70%)]"
      />
      <header className="sticky top-0 z-10 border-b border-jb-grey-90 bg-jb-black/85 backdrop-blur-xl">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4 px-6 py-4 sm:px-8 lg:px-10">
          <Link to="/" className="flex items-center gap-3">
            <BrandMark />
            <span className="text-sm font-semibold tracking-tight text-jb-grey-5">{t('app.name')}</span>
          </Link>
          {user ? (
            <div className="flex items-center gap-3">
              <span className="hidden text-sm text-jb-grey-50 sm:inline">
                {t('account.signedInAs', { username: user.username })}
              </span>
              <Button
                variant="outline"
                size="sm"
                className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-90 hover:text-jb-grey-5"
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
                className="rounded-jb-card border-jb-grey-70 bg-transparent text-jb-grey-20 hover:bg-jb-grey-90 hover:text-jb-grey-5"
                onClick={() => void logout()}
              >
                {t('account.signOut')}
              </Button>
            </div>
          ) : null}
        </div>
      </header>
      <main className="flex flex-1 flex-col">
        <Outlet />
      </main>
      <ModelSettingsDialog open={settingsOpen} onOpenChange={setSettingsOpen} />
    </div>
  );
}
