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

export default i18n;
