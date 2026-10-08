import { useEffect, useState } from 'react'
import { ApiError } from '../../api/http'
import { acknowledgeCancellation, advanceKitchenOrder, setMealAvailability } from '../../api/kitchenApi'
import type { CancelledOrderResponse, KitchenMealResponse, KitchenOrderResponse, StaffResponse } from '../../api/types'
import { AuthProvider } from '../../auth/AuthProvider'
import { useAuth } from '../../auth/auth-context'
import { LoginForm } from '../../auth/LoginForm'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useT } from '../../i18n/useT'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { CancelledOrderBanner } from './CancelledOrderBanner'
import { KitchenOrderCard } from './KitchenOrderCard'
import { RanOutFooter } from './RanOutFooter'
import { useKitchenOrders } from './useKitchenOrders'
import { useNow } from '../../lib/useNow'
import { LOCALE_BY_LANGUAGE } from '../../lib/formatMoney'
import { useLanguage } from '../../i18n/language-context'
import './KitchenScreen.css'

const COLUMNS = [
  { key: 'new', status: 'SUBMITTED', labelKey: 'kitchen.columns.new' },
  { key: 'preparing', status: 'PREPARING', labelKey: 'kitchen.columns.preparing' },
  { key: 'ready', status: 'READY', labelKey: 'kitchen.columns.ready' },
] as const

type KitchenBoardProps = {
  staff: StaffResponse
  onSignOut: () => Promise<void>
}

/** The live board - only rendered once the guard below let a kitchen or admin account through. */
function KitchenBoard({ staff, onSignOut }: KitchenBoardProps) {
  const { t } = useT()
  const { orders, cancelledOrders, meals, connectionLost, sessionExpired, refresh } = useKitchenOrders()
  const [busyOrderId, setBusyOrderId] = useState<number | null>(null)
  const [busyCancelledOrderId, setBusyCancelledOrderId] = useState<number | null>(null)
  const [busyMealId, setBusyMealId] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const now = useNow()
  const { language } = useLanguage()

  const clockFormat = new Intl.DateTimeFormat(LOCALE_BY_LANGUAGE[language], {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })

  // The server forgot this session (e.g. it restarted) - sign out, so the login form comes back.
  useEffect(() => {
    if (sessionExpired) void onSignOut()
  }, [sessionExpired, onSignOut])

  const handleAdvance = (order: KitchenOrderResponse) => {
    if (!order.nextStatus) return
    setBusyOrderId(order.orderId)
    setNotice(null)
    advanceKitchenOrder(order.orderId, order.nextStatus)
      .catch((err: unknown) => {
        setNotice(
          err instanceof ApiError && err.status === 409 ? t('kitchen.actionConflict') : t('kitchen.actionError'),
        )
      })
      .finally(() => {
        setBusyOrderId(null)
        refresh()
      })
  }

  const handleAcknowledge = (order: CancelledOrderResponse) => {
    setBusyCancelledOrderId(order.orderId)
    setNotice(null)
    acknowledgeCancellation(order.orderId)
      .catch(() => setNotice(t('kitchen.actionError')))
      .finally(() => {
        setBusyCancelledOrderId(null)
        refresh()
      })
  }

  const handleToggleMeal = (meal: KitchenMealResponse) => {
    setBusyMealId(meal.id)
    setNotice(null)
    setMealAvailability(meal.id, !meal.available)
      .catch(() => setNotice(t('kitchen.actionError')))
      .finally(() => {
        setBusyMealId(null)
        refresh()
      })
  }

  return (
    <div className="kitchen-screen">
      <header className="kitchen-screen__header">
        <div className="kitchen-screen__titles">
          <h1 className="kitchen-screen__title">{t('kitchen.title')}</h1>
          <p className="kitchen-screen__eyebrow">
            {t('kitchen.station')} · {t('kitchen.onShift', { name: staff.name })}
          </p>
        </div>
        <div className="kitchen-screen__header-right">
          <time className="kitchen-screen__clock" dateTime={new Date(now).toISOString()}>
            {clockFormat.format(now)}
          </time>
          <LanguageSwitcher />
          <ThemeToggle />
          <button type="button" className="kitchen-screen__logout" onClick={() => void onSignOut()}>
            {t('kitchen.logout')}
          </button>
        </div>
      </header>

      {notice && <p className="kitchen-screen__notice">{notice}</p>}
      {connectionLost && <p className="kitchen-screen__notice">{t('kitchen.connectionLost')}</p>}

      <CancelledOrderBanner
        orders={cancelledOrders}
        busyOrderId={busyCancelledOrderId}
        onAcknowledge={handleAcknowledge}
      />

      <div className="kitchen-screen__board">
        {COLUMNS.map((column) => {
          const columnOrders = orders?.filter((order) => order.status === column.status) ?? []
          return (
            <section key={column.key} className={`kitchen-screen__column kitchen-screen__column--${column.key}`}>
              <header className="kitchen-screen__column-header">
                <span className={`kitchen-screen__dot kitchen-screen__dot--${column.key}`} />
                <span>{t(column.labelKey)}</span>
                <span className="kitchen-screen__count">{columnOrders.length}</span>
              </header>
              <div className="kitchen-screen__column-list">
                {orders === null ? (
                  <p className="kitchen-screen__empty">{t('kitchen.loading')}</p>
                ) : columnOrders.length === 0 ? (
                  <p className="kitchen-screen__empty">{t('kitchen.empty')}</p>
                ) : (
                  columnOrders.map((order) => (
                    <KitchenOrderCard
                      key={order.orderId}
                      order={order}
                      now={now}
                      busy={busyOrderId === order.orderId}
                      onAdvance={handleAdvance}
                    />
                  ))
                )}
              </div>
            </section>
          )
        })}
      </div>

      <RanOutFooter meals={meals} busyMealId={busyMealId} onToggle={handleToggleMeal} />
    </div>
  )
}

function KitchenScreenContent() {
  const { t } = useT()
  const { staff, loading, logout } = useAuth()

  if (loading) {
    return null
  }

  if (!staff) {
    return <LoginForm />
  }

  if (staff.role !== 'KITCHEN' && staff.role !== 'ADMIN') {
    return (
      <div className="kitchen-screen kitchen-screen--denied">
        <p>{t('kitchen.accessDenied')}</p>
        <button type="button" className="kitchen-screen__logout" onClick={() => void logout()}>
          {t('kitchen.logout')}
        </button>
      </div>
    )
  }

  return <KitchenBoard staff={staff} onSignOut={logout} />
}

export function KitchenScreen() {
  return (
    <ThemeProvider defaultTheme="dark">
      <LanguageProvider defaultLanguage="de">
        <AuthProvider>
          <KitchenScreenContent />
        </AuthProvider>
      </LanguageProvider>
    </ThemeProvider>
  )
}
