import { useEffect, useState } from 'react'
import { getGuestSettings } from '../../api/guestApi'
import type { ConfigResponse, GuestMealResponse } from '../../api/types'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { CartScreen } from './CartScreen'
import { MAX_QUANTITY, MIN_QUANTITY, type CartLineItem } from './cartTypes'
import { MealDetailScreen } from './MealDetailScreen'
import { MenuScreen } from './MenuScreen'
import { WelcomeScreen } from './WelcomeScreen'
import { useCartQuote } from './useCartQuote'
import './GuestScreen.css'

type GuestStep = 'welcome' | 'ordering' | 'detail' | 'cart'

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
  const { language } = useLanguage()
  const [step, setStep] = useState<GuestStep>(readInitialStep)
  const [selectedMeal, setSelectedMeal] = useState<GuestMealResponse | null>(null)
  // Lives here rather than in a screen so it survives menu/detail/cart
  // navigation and language switches.
  const [cartItems, setCartItems] = useState<CartLineItem[]>([])
  const cartQuote = useCartQuote(cartItems)
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

  /** Same meal, size and note merges into one line; a different note stays a separate line. */
  const handleAddToOrder = (line: CartLineItem) => {
    setCartItems((items) => {
      const existing = items.find(
        (item) => item.mealId === line.mealId && item.sizeId === line.sizeId && item.note === line.note,
      )
      if (!existing) return [...items, line]
      return items.map((item) =>
        item === existing ? { ...item, quantity: Math.min(MAX_QUANTITY, item.quantity + line.quantity) } : item,
      )
    })
    setSelectedMeal(null)
    setStep('ordering')
  }

  const handleChangeQuantity = (lineId: string, quantity: number) => {
    const clamped = Math.min(MAX_QUANTITY, Math.max(MIN_QUANTITY, quantity))
    setCartItems((items) => items.map((item) => (item.id === lineId ? { ...item, quantity: clamped } : item)))
  }

  const handleRemoveLine = (lineId: string) => {
    const remaining = cartItems.filter((item) => item.id !== lineId)
    setCartItems(remaining)
    if (remaining.length === 0) setStep('ordering')
  }

  const itemCount = cartItems.reduce((count, item) => count + item.quantity, 0)
  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

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

  if (step === 'cart' && cartItems.length > 0) {
    return (
      <CartScreen
        items={cartItems}
        settings={settings}
        quote={cartQuote.quote}
        quoteLoading={cartQuote.loading}
        quoteError={cartQuote.error}
        onBack={handleBackToMenu}
        onChangeQuantity={handleChangeQuantity}
        onRemove={handleRemoveLine}
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
      {cartItems.length > 0 && (
        <button type="button" className="guest-screen__action-bar guest-screen__cart-bar" onClick={() => setStep('cart')}>
          <span className="guest-screen__cart-bar-label">
            <span className="guest-screen__cart-count">{itemCount}</span>
            <span>{t('guest.cart.reviewOrder')}</span>
          </span>
          <span className="guest-screen__cart-total">{cartQuote.quote ? price(cartQuote.quote.total) : ''}</span>
        </button>
      )}
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
