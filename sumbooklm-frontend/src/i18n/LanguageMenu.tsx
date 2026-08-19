import { Check, Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { supportedLanguages, type SupportedLanguage } from '@/i18n';

/**
 * What each language is called, in that language.
 *
 * The names are not translated. A reader looking for their own language is looking for the word they
 * would write it with, and a list that renamed itself with every switch would be a list they have to
 * read in a language they are trying to leave.
 */
const LANGUAGE_NAMES: Record<SupportedLanguage, string> = {
  de: 'Deutsch',
  en: 'English',
  ja: '日本語',
};

/**
 * The control that switches the language of the interface.
 *
 * It sits in the frame of every screen rather than in a settings dialog, because the reader who needs
 * it most is the one who cannot read the screen they are on, and a setting they have to find behind a
 * word is a setting behind the problem. Two clicks, and the whole application answers in the other
 * language.
 *
 * The choice is remembered by the language detector, which writes it into local storage, so it holds
 * for the browser rather than for the account. It is a property of who is reading, not of who is
 * signed in, and it therefore also holds on the screens where nobody is.
 */
export function LanguageMenu() {
  const { t, i18n } = useTranslation();
  const current = supportedLanguages.find((language) => language === i18n.resolvedLanguage);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={t('language.change')}
        className="flex h-8 shrink-0 items-center gap-1.5 rounded-full border border-nb-line px-3 text-[0.8125rem] text-nb-body transition-colors outline-none hover:bg-nb-hover hover:text-nb-text focus-visible:ring-2 focus-visible:ring-nb-accent"
      >
        <Languages className="size-4" aria-hidden />
        <span>{current ? LANGUAGE_NAMES[current] : t('language.change')}</span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44 rounded-nb-tile bg-nb-raised text-nb-text">
        {supportedLanguages.map((language) => (
          <DropdownMenuItem key={language} onClick={() => void i18n.changeLanguage(language)}>
            <Check className={language === current ? 'opacity-100' : 'opacity-0'} aria-hidden />
            <span>{LANGUAGE_NAMES[language]}</span>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
