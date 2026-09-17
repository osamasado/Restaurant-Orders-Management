import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'
import ar from './locales/ar.json'
import de from './locales/de.json'
import en from './locales/en.json'

export type Language = 'en' | 'de' | 'ar'

export const RTL_LANGUAGES: ReadonlySet<Language> = new Set<Language>(['ar'])

void i18next.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    de: { translation: de },
    ar: { translation: ar },
  },
  fallbackLng: 'en',
  // No backend to actually persist missing keys to - saveMissing just makes
  // i18next call missingKeyHandler below for every key that isn't found,
  // which is the whole point: fallback text renders (via fallbackLng above)
  // *and* a warning fires, instead of either silently blank or a hard error.
  saveMissing: true,
  missingKeyHandler: (languages, _namespace, key) => {
    console.warn(`[i18n] Missing translation for key "${key}" in language(s): ${languages.join(', ')}`)
  },
  interpolation: {
    escapeValue: false,
  },
})

export default i18next
