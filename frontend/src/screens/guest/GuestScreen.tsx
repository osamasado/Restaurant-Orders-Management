import { useEffect, useState } from 'react'
import { claimDevice, getGuestSettings, submitOrder } from '../../api/guestApi'
import { ApiError } from '../../api/http'
import type {
  ConfigResponse,
  GuestMealResponse,
  GuestOrderResponse,
  GuestTableResponse,
  Language as ApiLanguage,
  PaymentMethod,
} from '../../api/types'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { formatMoney } from '../../lib/formatMoney'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { CartScreen } from './CartScreen'
import { MAX_QUANTITY, MIN_QUANTITY, type CartLineItem } from './cartTypes'
import { clearDeviceCode, readDeviceCode, writeDeviceCode } from './deviceStorage'
import { MealDetailScreen } from './MealDetailScreen'
import { MenuScreen } from './MenuScreen'
import { OrderConfirmationScreen } from './OrderConfirmationScreen'
import { PairingScreen } from './PairingScreen'
import { PaymentScreen } from './PaymentScreen'
import { WelcomeScreen } from './WelcomeScreen'
import { useCartQuote } from './useCartQuote'
import './GuestScreen.css'

type GuestStep = 'welcome' | 'ordering' | 'detail' | 'cart' | 'payment' | 'confirmation'

/** 'checking' = a stored code is being re-validated with the server on start. */
type DeviceStatus = 'checking' | 'paired' | 'unpaired'

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

  const [deviceCode, setDeviceCode] = useState<string | null>(readDeviceCode)
  const [deviceStatus, setDeviceStatus] = useState<DeviceStatus>(() => (deviceCode ? 'checking' : 'unpaired'))
  const [table, setTable] = useState<GuestTableResponse | null>(null)

  const [cartNotice, setCartNotice] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [paymentError, setPaymentError] = useState<string | null>(null)
  const [placedOrder, setPlacedOrder] = useState<GuestOrderResponse | null>(null)

  // Re-check a stored code once on start: an admin may have unpaired the table since.
  useEffect(() => {
    const storedCode = readDeviceCode()
    if (!storedCode) return
    let cancelled = false
    claimDevice(storedCode)
      .then((claimed) => {
        if (cancelled) return
        setTable(claimed)
        setDeviceStatus('paired')
      })
      .catch((err: unknown) => {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 404) {
          clearDeviceCode()
          setDeviceCode(null)
          setDeviceStatus('unpaired')
        } else {
          // Server unreachable right now - keep the device usable; submission re-checks the code anyway.
          setDeviceStatus('paired')
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

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

  const handlePaired = (code: string, claimed: GuestTableResponse) => {
    writeDeviceCode(code)
    setDeviceCode(code)
    setTable(claimed)
    setDeviceStatus('paired')
  }

  const forgetDevice = () => {
    clearDeviceCode()
    setDeviceCode(null)
    setTable(null)
    setDeviceStatus('unpaired')
  }

  /** Same meal, size and note merges into one line; a different note stays a separate line. */
  const handleAddToOrder = (line: CartLineItem) => {
    setCartNotice(null)
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
    setCartNotice(null)
    setCartItems((items) => items.map((item) => (item.id === lineId ? { ...item, quantity: clamped } : item)))
  }

  const handleRemoveLine = (lineId: string) => {
    const remaining = cartItems.filter((item) => item.id !== lineId)
    setCartNotice(null)
    setCartItems(remaining)
    if (remaining.length === 0) setStep('ordering')
  }

  const handleChoosePayment = () => {
    setCartNotice(null)
    setPaymentError(null)
    setStep('payment')
  }

  /**
   * The cart is only cleared once the server accepted the order. A 409 (a
   * meal ran out meanwhile) goes back to the cart with a fresh quote, which
   * flags the affected lines; a 403 means this device was unpaired.
   */
  const handleConfirmOrder = (paymentMethod: PaymentMethod) => {
    if (!deviceCode || submitting) return
    setSubmitting(true)
    setPaymentError(null)
    submitOrder({
      deviceCode,
      language: language.toUpperCase() as ApiLanguage,
      paymentMethod,
      items: cartItems.map((item) => ({ sizeId: item.sizeId, quantity: item.quantity, note: item.note })),
    })
      .then((order) => {
        setPlacedOrder(order)
        setCartItems([])
        setStep('confirmation')
      })
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 409) {
          setCartNotice(t('guest.payment.unavailableError'))
          cartQuote.refresh()
          setStep('cart')
        } else if (err instanceof ApiError && err.status === 403) {
          forgetDevice()
        } else {
          setPaymentError(t('guest.payment.submitError'))
        }
      })
      .finally(() => setSubmitting(false))
  }

  const handleNewOrder = () => {
    setPlacedOrder(null)
    setStep('ordering')
  }

  const tableLabel = table ? t('guest.tableLabel', { number: table.tableNumber }) : t('guest.tableUnknown')

  const itemCount = cartItems.reduce((count, item) => count + item.quantity, 0)
  const price = (amount: number) =>
    settings ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition) : ''

  if (deviceStatus === 'checking') {
    return <div className="guest-screen guest-screen__checking">{t('guest.pairing.checking')}</div>
  }

  if (deviceStatus === 'unpaired') {
    return <PairingScreen onPaired={handlePaired} />
  }

  if (step === 'welcome') {
    return <WelcomeScreen tableLabel={tableLabel} onLanguageSelected={handleLanguageSelected} />
  }

  if (step === 'confirmation' && placedOrder) {
    return <OrderConfirmationScreen order={placedOrder} settings={settings} onNewOrder={handleNewOrder} />
  }

  if (step === 'payment' && cartItems.length > 0) {
    return (
      <PaymentScreen
        settings={settings}
        quote={cartQuote.quote}
        submitting={submitting}
        error={paymentError}
        onBack={() => setStep('cart')}
        onConfirm={handleConfirmOrder}
      />
    )
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
        onChoosePayment={handleChoosePayment}
        notice={cartNotice}
      />
    )
  }

  return (
    <div className="guest-screen">
      <header className="guest-screen__header">
        <span className="guest-screen__eyebrow">{tableLabel}</span>
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
