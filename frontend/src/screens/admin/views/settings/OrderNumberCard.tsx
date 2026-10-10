import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../../../api/http'
import { getOrderNumberStatus, resetOrderNumber } from '../../../../api/settingsApi'
import type { OrderNumberStatus } from '../../../../api/types'
import { ConfirmDialog } from '../../../../components/ConfirmDialog'
import { Modal } from '../../../../components/Modal'
import { useLanguage } from '../../../../i18n/language-context'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import { LOCALE_BY_LANGUAGE } from '../../../../lib/formatMoney'
import { formatOrderNumber } from '../../../../lib/formatOrderNumber'

/**
 * The displayed order number: where the series stands, and the button that restarts it at 001. The button asks the
 * server how many orders are open at the moment it is pressed (not what was true when the page loaded), then opens
 * a dialog: a confirmation when the restart is possible, an explanation when open orders refuse it. Only the
 * confirmed request resets; the server checks again and answers 409 if an order was placed in between.
 */
export function OrderNumberCard() {
  const { t } = useT()
  const { language } = useLanguage()

  const [status, setStatus] = useState<OrderNumberStatus | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [checking, setChecking] = useState(false)
  const [dialog, setDialog] = useState<'confirm' | 'blocked' | null>(null)
  const [restarted, setRestarted] = useState(false)
  const closeRef = useRef<HTMLButtonElement>(null)

  // Fetch-on-mount, like the rest of the Settings page (no data library in this project).
  useEffect(() => {
    let current = true
    getOrderNumberStatus().then(
      (loaded) => {
        if (current) setStatus(loaded)
      },
      () => {
        if (current) setLoadFailed(true)
      },
    )
    return () => {
      current = false
    }
  }, [])

  const timeFormat = useMemo(
    () =>
      new Intl.DateTimeFormat(LOCALE_BY_LANGUAGE[language], {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      }),
    [language],
  )

  const handleRestart = async () => {
    setChecking(true)
    setRestarted(false)
    try {
      const fresh = await getOrderNumberStatus()
      setStatus(fresh)
      setLoadFailed(false)
      setDialog(fresh.openOrders > 0 ? 'blocked' : 'confirm')
    } catch {
      setLoadFailed(true)
    } finally {
      setChecking(false)
    }
  }

  const confirmRestart = async () => {
    try {
      setStatus(await resetOrderNumber())
      setRestarted(true)
    } catch (error) {
      // An order was placed after the check: show the truth, and let the dialog report the failure.
      if (error instanceof ApiError && error.status === 409) {
        getOrderNumberStatus().then(setStatus, () => undefined)
      }
      throw error
    }
  }

  const alreadyAtStart = status !== null && status.nextDisplayNumber === 1

  return (
    <div className="settings-card order-number-card">
      <span className="settings-card__title">{t('admin.settings.orderNumber.title')}</span>

      {status && (
        <>
          <div className="order-number-card__current">
            <span className="settings-card__caption">{t('admin.settings.orderNumber.next')}</span>
            <bdi className="order-number-card__value">{formatOrderNumber(status.nextDisplayNumber)}</bdi>
          </div>
          <p className="settings-card__caption">
            {status.lastReset
              ? t('admin.settings.orderNumber.lastReset', {
                  when: isolate(timeFormat.format(new Date(status.lastReset.resetAt))),
                  name: isolate(status.lastReset.staffName),
                })
              : t('admin.settings.orderNumber.neverReset')}
          </p>
        </>
      )}

      <p className="settings-card__caption">{t('admin.settings.orderNumber.caption')}</p>
      {alreadyAtStart && <p className="settings-card__caption">{t('admin.settings.orderNumber.alreadyAtStart')}</p>}

      {loadFailed && <p className="settings-view__error">{t('admin.settings.orderNumber.loadError')}</p>}
      {restarted && <p className="settings-view__success">{t('admin.settings.orderNumber.restarted')}</p>}

      <button
        type="button"
        className="order-number-card__restart button"
        onClick={() => void handleRestart()}
        disabled={checking || (status !== null && alreadyAtStart)}
        aria-busy={checking || undefined}
      >
        {t('admin.settings.orderNumber.restart')}
      </button>

      {dialog === 'confirm' && (
        <ConfirmDialog
          title={t('admin.settings.orderNumber.dialogTitle')}
          message={t('admin.settings.orderNumber.dialogMessage')}
          confirmLabel={t('admin.settings.orderNumber.confirm')}
          errorMessage={t('admin.settings.orderNumber.failed')}
          onConfirm={confirmRestart}
          onClose={() => setDialog(null)}
        />
      )}

      {dialog === 'blocked' && status && (
        <Modal title={t('admin.settings.orderNumber.blockedTitle')} onClose={() => setDialog(null)} size="small" initialFocus={closeRef}>
          <p className="dialog__message">{t('admin.settings.orderNumber.blockedMessage', { count: status.openOrders })}</p>
          <div className="dialog__actions">
            <button ref={closeRef} type="button" className="button button--primary" onClick={() => setDialog(null)}>
              {t('common.close')}
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
