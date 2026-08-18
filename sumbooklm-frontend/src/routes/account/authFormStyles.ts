/**
 * Class names shared by the account forms.
 *
 * The shadcn primitives follow the semantic theme tokens of the application. The account screens
 * deliberately step out of that theme and use the JetBrains grayscale directly, so the overrides
 * live here instead of being repeated in both forms.
 */
export const authInputClasses =
  'h-10 rounded-jb-card border-jb-grey-80 bg-jb-grey-95/70 px-3 text-jb-grey-10 ' +
  'placeholder:text-jb-grey-60 focus-visible:border-jb-grey-50 focus-visible:ring-2 ' +
  'focus-visible:ring-jb-grey-30/15 dark:bg-jb-grey-95/70';

export const authLabelClasses = 'text-[0.8125rem] font-medium text-jb-grey-30';

// The auto margin pins the button to the lower edge of the card, so the height the card reserves for
// the longest form shows up as spacing above the button rather than as a gap in the middle.
export const authSubmitClasses =
  'mt-auto h-10 w-full rounded-jb-card bg-jb-grey-5 font-medium text-jb-black hover:bg-white ' +
  'focus-visible:ring-jb-grey-30/25 disabled:opacity-45';

export const authErrorClasses =
  'rounded-jb-card border border-jb-danger/30 bg-jb-danger/12 px-3 py-2 text-sm text-jb-danger';

export const authLinkClasses =
  'font-medium text-jb-grey-20 underline decoration-jb-grey-60 underline-offset-4 ' +
  'transition-colors hover:text-white hover:decoration-jb-grey-30';
