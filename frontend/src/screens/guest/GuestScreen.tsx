import { useState } from 'react'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useT } from '../../i18n/useT'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { WelcomeScreen } from './WelcomeScreen'
import './GuestScreen.css'

type GuestStep = 'welcome' | 'ordering'

/**
 * sessionStorage, not localStorage: scoped to this browser tab's sit-down,
 * distinct from the language preference itself (already global/persistent
 * via LanguageProvider's shared localStorage key). A fresh tab always starts
 * at the welcome screen; a reload mid-session does not.
 */
const SESSION_STEP_KEY = 'rom-guest-step'

function readInitialStep(): GuestStep {
  try {
    return window.sessionStorage.getItem(SESSION_STEP_KEY) === 'ordering' ? 'ordering' : 'welcome'
  } catch {
    return 'welcome'
  }
}

function GuestScreenContent() {
  const { t } = useT()
  const [step, setStep] = useState<GuestStep>(readInitialStep)

  const handleLanguageSelected = () => {
    try {
      window.sessionStorage.setItem(SESSION_STEP_KEY, 'ordering')
    } catch {
      // sessionStorage unavailable (e.g. private browsing) - step still advances for this render, just won't survive a reload.
    }
    setStep('ordering')
  }

  if (step === 'welcome') {
    return <WelcomeScreen onLanguageSelected={handleLanguageSelected} />
  }

  return (
    <div className="guest-screen">
      <header className="guest-screen__header">
        <span className="guest-screen__eyebrow">{t('guest.tableLabel')}</span>
        <div className="guest-screen__header-controls">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>
      <main className="guest-screen__content">
        <p>{t('guest.orderingComingSoon')}</p>
      </main>
      <div className="guest-screen__action-bar">
        <span>{t('guest.continue')}</span>
      </div>
    </div>
  )
}

export function GuestScreen() {
  return (
    <ThemeProvider defaultTheme="light">
      <LanguageProvider defaultLanguage="de">
        <GuestScreenContent />
      </LanguageProvider>
    </ThemeProvider>
  )
}
