import type { ReactNode } from 'react'
import { Icon } from '../Icon'
import type { IconName } from '../Icon'
import './EmptyState.css'

type EmptyStateProps = {
  icon: IconName
  title: string
  hint?: string
  /** A button or link that gets the user out of the empty state. */
  action?: ReactNode
  /** An error is announced to screen readers right away; an empty list is not. */
  tone?: 'neutral' | 'error'
}

/** One look for "nothing here", "nothing matches" and "could not load", in every list and panel. */
export function EmptyState({ icon, title, hint, action, tone = 'neutral' }: EmptyStateProps) {
  return (
    <div className={`empty-state${tone === 'error' ? ' empty-state--error' : ''}`} role={tone === 'error' ? 'alert' : undefined}>
      <span className="empty-state__icon">
        <Icon name={icon} size={22} />
      </span>
      <p className="empty-state__title">{title}</p>
      {hint && <p className="empty-state__hint">{hint}</p>}
      {action}
    </div>
  )
}
