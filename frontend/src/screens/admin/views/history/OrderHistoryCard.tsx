import type { OrderHistory } from '../../../../api/types'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import { formatOrderNumber } from '../../../../lib/formatOrderNumber'
import './OrderHistoryCard.css'

type OrderHistoryCardProps = {
  order: OrderHistory
  /** Formats an ISO timestamp in the app's language - one formatter shared by every card. */
  formatTime: (isoTimestamp: string) => string
}

/** One order and every status it reached: when, and who made the change ("Guest" when no staff member did). */
export function OrderHistoryCard({ order, formatTime }: OrderHistoryCardProps) {
  const { t } = useT()
  const acknowledgement = order.cancellationAcknowledgement

  return (
    <article className="history-card">
      <header className="history-card__header">
        <span className="history-card__number" dir="ltr">
          {order.orderNumber === null ? '-' : formatOrderNumber(order.orderNumber)}
        </span>
        <span className="history-card__table">
          {t('admin.history.table', { number: isolate(order.tableNumber) })}
        </span>
        <span className={`history-card__stage history-card__stage--${order.status.toLowerCase()}`}>
          {t(`admin.history.stages.${order.status}`)}
        </span>
      </header>

      <ol className="history-card__entries">
        {order.entries.map((entry, index) => (
          <li key={index} className="history-card__entry">
            <span className={`history-card__dot history-card__dot--${entry.stage.toLowerCase()}`} aria-hidden="true" />
            {/* No forced direction: the Arabic date format carries its own right-to-left marks, and a forced
                left-to-right box scrambles them. <bdi> just keeps the value apart from its neighbours. */}
            <time className="history-card__time" dateTime={entry.changedAt}>
              <bdi>{formatTime(entry.changedAt)}</bdi>
            </time>
            <span className="history-card__entry-stage">{t(`admin.history.stages.${entry.stage}`)}</span>
            <span className="history-card__actor">
              {entry.actor === null ? t('admin.history.guest') : <bdi>{entry.actor}</bdi>}
            </span>
          </li>
        ))}
      </ol>

      {acknowledgement && (
        <p className="history-card__acknowledged">
          {t('admin.history.acknowledged', {
            name: isolate(acknowledgement.by ?? '-'),
            time: isolate(formatTime(acknowledgement.at)),
          })}
        </p>
      )}
    </article>
  )
}
