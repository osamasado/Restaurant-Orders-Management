import { useCallback, useEffect, useMemo, useState } from 'react'
import { advanceAdminOrder, cancelAdminOrder } from '../../../../api/adminOrdersApi'
import { ApiError } from '../../../../api/http'
import { getSettings } from '../../../../api/settingsApi'
import type { AdminOrderRow, ConfigResponse, OrderStatus } from '../../../../api/types'
import { useLanguage } from '../../../../i18n/language-context'
import { useT } from '../../../../i18n/useT'
import { formatMoney, LOCALE_BY_LANGUAGE } from '../../../../lib/formatMoney'

/**
 * What every screen that lists admin orders shares: sending a step or a cancel (the server's state machine decides
 * what is legal, the buttons only ask), the "are you sure?" step before a cancel, the notice after a failed
 * action, and the time and money formats in the app's language and the configured currency. refresh() is the
 * list's own re-poll, called after every action so the screen shows what the server now says.
 */
export function useOrderActions(refresh: () => void) {
  const { t } = useT()
  const { language } = useLanguage()
  const [busyOrderId, setBusyOrderId] = useState<number | null>(null)
  const [confirmingOrderId, setConfirmingOrderId] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [settings, setSettings] = useState<ConfigResponse | null>(null)

  // The currency comes from the settings; until they arrive (or if they cannot be read) totals show as plain numbers.
  useEffect(() => {
    let cancelled = false
    getSettings()
      .then((config) => {
        if (!cancelled) setSettings(config)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [])

  const locale = LOCALE_BY_LANGUAGE[language]
  const timeOnly = useMemo(
    () => new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }),
    [locale],
  )
  const dateAndTime = useMemo(
    () =>
      new Intl.DateTimeFormat(locale, {
        day: '2-digit',
        month: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      }),
    [locale],
  )
  // Today's orders show just the time; an older one also shows its date.
  const formatTime = useCallback(
    (isoTimestamp: string) => {
      const placed = new Date(isoTimestamp)
      return (placed.toDateString() === new Date().toDateString() ? timeOnly : dateAndTime).format(placed)
    },
    [timeOnly, dateAndTime],
  )
  const formatTotal = useCallback(
    (amount: number) =>
      settings
        ? formatMoney(amount, language, settings.currencySymbol, settings.symbolPosition)
        : new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount),
    [settings, language, locale],
  )

  const run = (order: AdminOrderRow, action: () => Promise<unknown>) => {
    setBusyOrderId(order.orderId)
    setConfirmingOrderId(null)
    setNotice(null)
    action()
      .catch((err: unknown) => {
        setNotice(
          err instanceof ApiError && err.status === 409 ? t('admin.orders.actionConflict') : t('admin.orders.actionError'),
        )
      })
      .finally(() => {
        setBusyOrderId(null)
        refresh()
      })
  }

  return {
    busyOrderId,
    confirmingOrderId,
    notice,
    settings,
    formatTime,
    formatTotal,
    handleAdvance: (order: AdminOrderRow, status: OrderStatus) => run(order, () => advanceAdminOrder(order.orderId, status)),
    handleConfirmCancel: (order: AdminOrderRow) => run(order, () => cancelAdminOrder(order.orderId)),
    askCancel: (order: AdminOrderRow) => setConfirmingOrderId(order.orderId),
    keepOrder: () => setConfirmingOrderId(null),
  }
}
