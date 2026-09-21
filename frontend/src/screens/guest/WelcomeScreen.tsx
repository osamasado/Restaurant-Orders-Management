import type { Language } from '../../i18n/i18n'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import './WelcomeScreen.css'

/**
 * Deliberately NOT run through t(): a guest who reads none of the currently
 * active language still needs to recognize their own language's native name
 * to pick it - translating these (unlike common.language.names.*, which
 * translates a language's name INTO the active language) would defeat their
 * purpose.
 */
const NATIVE_LANGUAGE_NAMES: Record<Language, { name: string; code: string }> = {
  de: { name: 'Deutsch', code: 'DE' },
  en: { name: 'English', code: 'EN' },
  ar: { name: 'العربية', code: 'AR' },
}

const LANGUAGE_ORDER: Language[] = ['de', 'en', 'ar']

type WelcomeScreenProps = {
  onLanguageSelected: () => void
}

export function WelcomeScreen({ onLanguageSelected }: WelcomeScreenProps) {
  const { t } = useT()
  const { setLanguage } = useLanguage()

  const pickLanguage = (language: Language) => {
    setLanguage(language)
    onLanguageSelected()
  }

  return (
    <div className="welcome-screen">
      <div>
        <span className="welcome-screen__eyebrow">{t('guest.tableLabel')}</span>
        <h1 className="welcome-screen__title">{t('guest.welcome.title')}</h1>
        <p className="welcome-screen__tagline">{t('guest.welcome.tagline')}</p>
      </div>
      <div className="welcome-screen__languages">
        {/* Fixed trilingual label - see the NATIVE_LANGUAGE_NAMES comment above for why this isn't translated either. */}
        <span className="welcome-screen__languages-label">Sprache &middot; Language &middot; اللغة</span>
        {LANGUAGE_ORDER.map((language) => (
          <button
            type="button"
            key={language}
            className="welcome-screen__language-button"
            onClick={() => pickLanguage(language)}
          >
            <span>{NATIVE_LANGUAGE_NAMES[language].name}</span>
            <span className="welcome-screen__language-code">{NATIVE_LANGUAGE_NAMES[language].code}</span>
          </button>
        ))}
      </div>
    </div>
  )
}
