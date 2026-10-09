import { useEffect, useRef, useState } from 'react'
import { BrandMark } from '../../components/admin/BrandMark'
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
const NATIVE_LANGUAGE_NAMES: Record<Language, string> = {
  de: 'Deutsch',
  en: 'English',
  ar: 'العربية',
}

const LANGUAGE_ORDER: Language[] = ['de', 'en', 'ar']

/** How long the chosen language shows in coral before the menu opens: long enough to be seen, short enough not to be felt. */
const CHOICE_FEEDBACK_MS = 380

type WelcomeScreenProps = {
  tableLabel: string
  onLanguageSelected: () => void
}

/**
 * The first screen a guest sees after scanning the table's code: the table, the restaurant, one line of welcome and
 * the three languages. Choosing one switches the app to it at once (the title and text change language under the
 * guest's finger), marks the button coral, and opens the menu a moment later. The slowly drifting coral and amber
 * light behind it is pure CSS and sits behind a soft veil, so it never competes with the text.
 */
export function WelcomeScreen({ tableLabel, onLanguageSelected }: WelcomeScreenProps) {
  const { t } = useT()
  const { setLanguage } = useLanguage()
  const [chosen, setChosen] = useState<Language | null>(null)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const pickLanguage = (language: Language) => {
    if (chosen) return
    setChosen(language)
    setLanguage(language)
    timer.current = window.setTimeout(onLanguageSelected, CHOICE_FEEDBACK_MS)
  }

  return (
    <div className="welcome-screen">
      <div className="welcome-screen__ambient" aria-hidden="true">
        <span className="welcome-screen__orb welcome-screen__orb--coral" />
        <span className="welcome-screen__orb welcome-screen__orb--amber" />
        <span className="welcome-screen__orb welcome-screen__orb--terracotta" />
        <span className="welcome-screen__orb welcome-screen__orb--blush" />
      </div>

      <main className="welcome-screen__content">
        <BrandMark size={60} />
        <span className="welcome-screen__table">
          <span className="welcome-screen__table-dot" aria-hidden="true" />
          {tableLabel}
        </span>
        <h1 className="welcome-screen__title">{t('guest.welcome.title')}</h1>
        <p className="welcome-screen__tagline">{t('guest.welcome.tagline')}</p>

        <div className="welcome-screen__languages" role="group" aria-labelledby="welcome-languages-label">
          {/* Fixed trilingual label - see the NATIVE_LANGUAGE_NAMES comment above for why this isn't translated either. */}
          <span className="welcome-screen__languages-label" id="welcome-languages-label" dir="ltr">
            Sprache &middot; Language &middot; اللغة
          </span>
          <div className="welcome-screen__choices">
            {LANGUAGE_ORDER.map((language) => (
              <button
                type="button"
                key={language}
                className="welcome-screen__language-button button button--large"
                aria-pressed={chosen === language}
                disabled={chosen !== null && chosen !== language}
                onClick={() => pickLanguage(language)}
              >
                <span lang={language} dir={language === 'ar' ? 'rtl' : 'ltr'}>
                  {NATIVE_LANGUAGE_NAMES[language]}
                </span>
              </button>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}
