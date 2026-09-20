import { useEffect } from 'react'
import type { MouseEvent, ReactNode } from 'react'
import './Modal.css'

type ModalProps = {
  title: string
  onClose: () => void
  children: ReactNode
}

/** First shared modal/dialog primitive in this frontend - built for issue #13's meal/category forms. */
export function Modal({ title, onClose, children }: ModalProps) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  const handleOverlayClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose()
  }

  return (
    <div className="modal__overlay" onMouseDown={handleOverlayClick}>
      <div className="modal__card" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal__header">
          <h2 className="modal__title">{title}</h2>
          <button type="button" className="modal__close" onClick={onClose} aria-label="Close">
            &times;
          </button>
        </div>
        <div className="modal__body">{children}</div>
      </div>
    </div>
  )
}
