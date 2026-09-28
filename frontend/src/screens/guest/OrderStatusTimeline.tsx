import type { GuestOrderStatusResponse, OrderStatus } from '../../api/types'
import { useLanguage } from '../../i18n/language-context'
import { useT } from '../../i18n/useT'
import { LOCALE_BY_LANGUAGE } from '../../lib/formatMoney'
import './OrderStatusTimeline.css'

/** The four stages a guest sees, in order. DRAFT is never shown; CANCELLED gets its own notice. */
const STAGES: OrderStatus[] = ['SUBMITTED', 'PREPARING', 'READY', 'SERVED']

const STAGE_LABEL_KEYS: Record<OrderStatus, string> = {
  DRAFT: 'guest.status.submitted',
  SUBMITTED: 'guest.status.submitted',
  PREPARING: 'guest.status.preparing',
  READY: 'guest.status.ready',
  SERVED: 'guest.status.served',
  CANCELLED: 'guest.status.cancelled',
}

type OrderStatusTimelineProps = {
  status: GuestOrderStatusResponse
}

export function OrderStatusTimeline({ status }: OrderStatusTimelineProps) {
  const { t } = useT()
  const { language } = useLanguage()

  const timeFormat = new Intl.DateTimeFormat(LOCALE_BY_LANGUAGE[language], { hour: '2-digit', minute: '2-digit' })

  /** When this stage was reached, or undefined if it hasn't been yet. */
  const reachedAt = (stage: OrderStatus) => status.history.find((entry) => entry.status === stage)?.changedAt

  if (status.status === 'CANCELLED') {
    return (
      <p className="order-status-timeline__cancelled" role="alert">
        {t('guest.status.cancelledNotice')}
      </p>
    )
  }

  return (
    <section className="order-status-timeline" aria-live="polite">
      <span className="order-status-timeline__eyebrow">{t('guest.status.title')}</span>
      <ol className="order-status-timeline__list">
        {STAGES.map((stage) => {
          const changedAt = reachedAt(stage)
          return (
            <li
              key={stage}
              className={'order-status-timeline__row' + (changedAt ? ' order-status-timeline__row--reached' : '')}
            >
              <span className="order-status-timeline__dot" aria-hidden="true">
                ✓
              </span>
              <span className="order-status-timeline__label">{t(STAGE_LABEL_KEYS[stage])}</span>
              <span className="order-status-timeline__time">
                {changedAt ? timeFormat.format(new Date(changedAt)) : '-'}
              </span>
            </li>
          )
        })}
      </ol>
    </section>
  )
}
