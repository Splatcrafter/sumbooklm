import { Outlet } from 'react-router';
import { useTranslation } from 'react-i18next';

import { StrataBackground } from '@/components/background/StrataBackground';
import { BrandMark } from '@/routes/account/BrandMark';

/**
 * Full bleed frame of the account screens.
 *
 * The form sits on the left rather than in the centre, which leaves the background room to be seen
 * and gives the screen a direction instead of the symmetry every sign-in page has.
 */
export function AccountLayout() {
  const { t } = useTranslation();

  return (
    <div className="dark relative isolate min-h-svh w-full overflow-hidden bg-jb-black text-jb-grey-10">
      <StrataBackground />
      <div className="relative flex min-h-svh flex-col justify-center px-6 py-14 sm:px-10 lg:px-24">
        <div className="w-full max-w-[27rem]">
          <div className="mb-7 flex items-center gap-3 text-jb-grey-20">
            <BrandMark />
            <div className="flex flex-col">
              <span className="text-sm font-semibold tracking-tight text-jb-grey-5">
                {t('app.name')}
              </span>
              <span className="text-xs text-jb-grey-50">{t('app.tagline')}</span>
            </div>
          </div>
          <Outlet />
        </div>
      </div>
    </div>
  );
}
