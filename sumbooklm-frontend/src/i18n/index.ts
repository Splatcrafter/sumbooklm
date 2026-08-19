import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import de from '@/i18n/locales/de/common.json';
import en from '@/i18n/locales/en/common.json';
import ja from '@/i18n/locales/ja/common.json';

export const supportedLanguages = ['de', 'en', 'ja'] as const;

export type SupportedLanguage = (typeof supportedLanguages)[number];

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      de: { common: de },
      en: { common: en },
      ja: { common: ja },
    },
    supportedLngs: supportedLanguages,
    fallbackLng: 'en',
    defaultNS: 'common',
    ns: ['common'],
    interpolation: { escapeValue: false },
    detection: {
      order: ['querystring', 'localStorage', 'navigator', 'htmlTag'],
      lookupQuerystring: 'lang',
      lookupLocalStorage: 'sumbooklm.language',
      caches: ['localStorage'],
    },
  });

/**
 * Keeps the language of the document in step with the language of the interface.
 *
 * The attribute is what a screen reader picks a voice by and what a browser offers to translate
 * against, so it has to say what the page is actually written in rather than what it was written in
 * when it was served. That is already true of the first render, where the detector rather than a
 * reader chose the language, which is why the moment the setup finishes counts as a change.
 */
function followDocumentLanguage(language: string): void {
  document.documentElement.lang = language;
}

i18n.on('initialized', () => followDocumentLanguage(i18n.resolvedLanguage ?? i18n.language));
i18n.on('languageChanged', followDocumentLanguage);

export default i18n;
