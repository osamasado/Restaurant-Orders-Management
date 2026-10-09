import { useEffect, useId, useRef } from 'react'
import type { MouseEvent, ReactNode, RefObject } from 'react'
import { useT } from '../i18n/useT'
import './Modal.css'

type ModalProps = {
  title: string
  onClose: () => void
  children: ReactNode
  /** "small" for a short question or notice, the default for forms. */
  size?: 'default' | 'small'
  /** What should have focus when the dialog opens. Default: the first field or button in the dialog. */
  initialFocus?: RefObject<HTMLElement | null>
  /** False while something is being saved: Escape, the close button and a click outside then do nothing. */
  dismissible?: boolean
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * The shared dialog. Beyond its look it does what a dialog has to: focus moves into it when it opens, Tab and
 * Shift+Tab stay inside it, Escape closes it, the page behind does not scroll, and focus goes back to whatever
 * opened it (or to the page content if that button is gone, like after a delete).
 */
export function Modal({ title, onClose, children, size = 'default', initialFocus, dismissible = true }: ModalProps) {
  const { t } = useT()
  const titleId = useId()
  const cardRef = useRef<HTMLDivElement>(null)
  const bodyRef = useRef<HTMLDivElement>(null)
  // Read at every key press, so a changing onClose or dismissible never has to re-attach the listener.
  const latest = useRef({ onClose, dismissible })
  useEffect(() => {
    latest.current = { onClose, dismissible }
  })

  useEffect(() => {
    const card = cardRef.current
    if (!card) return
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null

    const first = initialFocus?.current ?? bodyRef.current?.querySelector<HTMLElement>(FOCUSABLE) ?? card
    first.focus()

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (latest.current.dismissible) latest.current.onClose()
        return
      }
      if (event.key !== 'Tab') return
      const focusable = [...card.querySelectorAll<HTMLElement>(FOCUSABLE)]
      if (focusable.length === 0) {
        event.preventDefault()
        return
      }
      const firstItem = focusable[0]
      const lastItem = focusable[focusable.length - 1]
      if (event.shiftKey && (document.activeElement === firstItem || document.activeElement === card)) {
        event.preventDefault()
        lastItem.focus()
      } else if (!event.shiftKey && document.activeElement === lastItem) {
        event.preventDefault()
        firstItem.focus()
      }
    }
    window.addEventListener('keydown', handleKeyDown)

    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      const target = opener?.isConnected ? opener : document.querySelector<HTMLElement>('main')
      target?.focus()
    }
    // Opening is the only moment focus is placed: later changes to the props must not steal it back.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleOverlayClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget && dismissible) onClose()
  }

  return (
    <div className="modal__overlay" onMouseDown={handleOverlayClick}>
      <div
        ref={cardRef}
        className={`modal__card${size === 'small' ? ' modal__card--small' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <div className="modal__header">
          <h2 id={titleId} className="modal__title">
            {title}
          </h2>
          <button type="button" className="modal__close button button--icon button--small" onClick={onClose} disabled={!dismissible} aria-label={t('common.close')}>
            &times;
          </button>
        </div>
        <div ref={bodyRef} className="modal__body">
          {children}
        </div>
      </div>
    </div>
  )
}
