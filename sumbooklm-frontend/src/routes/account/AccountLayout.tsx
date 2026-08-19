import { Outlet } from 'react-router';

import { WaveBackground } from '@/components/background/WaveBackground';

/**
 * Full bleed frame of the account screens.
 *
 * The form sits on the left rather than in the centre, which leaves the background room to be seen and
 * gives the screen a direction instead of the symmetry every sign-in page has. The frame itself holds
 * no text: everything readable belongs on a card, where it does not have to compete with the waves.
 */
export function AccountLayout() {
  return (
    <div className="dark relative isolate min-h-svh w-full overflow-hidden bg-nb-ground text-nb-text">
      <WaveBackground />
      <div className="relative flex min-h-svh flex-col justify-center px-6 py-14 sm:px-10 lg:px-24">
        <div className="w-full max-w-[27rem]">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
