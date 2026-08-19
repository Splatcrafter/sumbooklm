import { Outlet } from 'react-router';

import { WaveBackground } from '@/components/background/WaveBackground';
import { LanguageMenu } from '@/i18n/LanguageMenu';

/**
 * Full bleed frame of the account screens.
 *
 * The form sits on the left rather than in the centre, which leaves the background room to be seen and
 * gives the screen a direction instead of the symmetry every sign-in page has. The frame itself holds
 * no text: everything readable belongs on a card, where it does not have to compete with the waves.
 *
 * The one control outside the card is the language of the interface. A visitor who cannot read the
 * sign-in form has no account yet to keep a setting on, so the switch has to be on the screen that is
 * in the way, in the corner where it does not compete with the form.
 */
export function AccountLayout() {
  return (
    <div className="dark relative isolate min-h-svh w-full overflow-hidden bg-nb-ground text-nb-text">
      <WaveBackground />
      <div className="absolute top-4 right-4 z-10 sm:top-5 sm:right-6">
        <LanguageMenu />
      </div>
      <div className="relative flex min-h-svh flex-col justify-center px-6 py-14 sm:px-10 lg:px-24">
        <div className="w-full max-w-[27rem]">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
