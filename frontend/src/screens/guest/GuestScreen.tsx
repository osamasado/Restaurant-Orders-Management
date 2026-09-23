import { useEffect, useRef, useState } from 'react'
import { getGuestSettings } from '../../api/guestApi'
import type { ConfigResponse, GuestMealResponse } from '../../api/types'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useT } from '../../i18n/useT'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import type { CartLineItem } from './cartTypes'
import { MealDetailScreen } from './MealDetailScreen'
import { MenuScreen } from './MenuScreen'
import { WelcomeScreen } from './WelcomeScreen'
import './GuestScreen.css'

type GuestStep = 'welcome' | 'ordering' | 'detail'

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
  const [selectedMeal, setSelectedMeal] = useState<GuestMealResponse | null>(null)
  // Hand-off point for issue #20's cart/checkout screen - a ref, not state,
  // since nothing re-renders from it yet (no cart UI exists in this issue).
  const cartItemsRef = useRef<CartLineItem[]>([])
  const [settings, setSettings] = useState<ConfigResponse | null>(null)

  useEffect(() => {
    let cancelled = false
    getGuestSettings().then((response) => {
      if (!cancelled) setSettings(response)
    })
    return () => {
      cancelled = true
    }
  }, [])

  const handleLanguageSelected = () => {
    try {
      window.sessionStorage.setItem(SESSION_STEP_KEY, 'ordering')
    } catch {
      // sessionStorage unavailable (e.g. private browsing) - step still advances for this render, just won't survive a reload.
    }
    setStep('ordering')
  }

  const handleSelectMeal = (meal: GuestMealResponse) => {
    setSelectedMeal(meal)
    setStep('detail')
  }

  const handleBackToMenu = () => {
    setSelectedMeal(null)
    setStep('ordering')
  }

  const handleAddToOrder = (line: CartLineItem) => {
    cartItemsRef.current = [...cartItemsRef.current, line]
    setSelectedMeal(null)
    setStep('ordering')
  }

  if (step === 'welcome') {
    return <WelcomeScreen onLanguageSelected={handleLanguageSelected} />
  }

  if (step === 'detail' && selectedMeal) {
    return (
      <MealDetailScreen
        key={selectedMeal.id}
        meal={selectedMeal}
        settings={settings}
        onBack={handleBackToMenu}
        onAddToOrder={handleAddToOrder}
      />
    )
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
        <MenuScreen settings={settings} onSelectMeal={handleSelectMeal} />
      </main>
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
