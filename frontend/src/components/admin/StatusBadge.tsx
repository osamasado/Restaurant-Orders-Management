import type { OrderStatus } from '../../api/types'
import { useT } from '../../i18n/useT'
import { Icon } from '../Icon'
import './StatusBadge.css'

type StatusBadgeProps = {
  status: OrderStatus
  /**
   * Set when the badge is one step of an order's progress. The current step looks exactly like the badge in a table,
   * a step already passed is muted with a check mark, and one still to come is muted with a plain dot, so the
   * position is never told by colour alone.
   */
  progress?: 'done' | 'current' | 'upcoming'
}

/**
 * An order's status as a pill: the word (never colour alone), a dot, and the colour the boards use for the same
 * step: coral for new, amber while it is prepared, green once it is ready, a quieter green when served, red when
 * cancelled. The status is the server's own OrderStatus; nothing here decides what is legal.
 */
export function StatusBadge({ status, progress = 'current' }: StatusBadgeProps) {
  const { t } = useT()
  const muted = progress !== 'current'
  return (
    <span className={`status-badge status-badge--${status.toLowerCase()}${muted ? ' status-badge--muted' : ''}`}>
      {progress === 'done' ? (
        <Icon name="check" size={12} className="status-badge__check" />
      ) : (
        <span className="status-badge__dot" aria-hidden="true" />
      )}
      {t(`admin.history.stages.${status}`)}
    </span>
  )
}
