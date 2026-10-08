import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useT } from '../i18n/useT'
import { ToastContext } from './toast-context'
import type { ToastKind } from './toast-context'
import './Toast.css'

type ToastItem = { id: number; kind: ToastKind; message: string }

const LIFETIME_MS: Record<ToastKind, number> = { success: 4000, error: 8000 }

/**
 * Small, non-blocking messages ("PIN updated for M. Behr", "Could not update availability"). They stay long
 * enough to read, can be dismissed, and are announced to screen readers: errors at once, successes politely.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useT()
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(1)
  const timers = useRef(new Map<number, number>())

  const dismiss = useCallback((id: number) => {
    window.clearTimeout(timers.current.get(id))
    timers.current.delete(id)
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const show = useCallback(
    (kind: ToastKind, message: string) => {
      const id = nextId.current++
      setToasts((current) => [...current, { id, kind, message }])
      timers.current.set(id, window.setTimeout(() => dismiss(id), LIFETIME_MS[kind]))
    },
    [dismiss],
  )

  useEffect(() => {
    const pending = timers.current
    return () => pending.forEach((timer) => window.clearTimeout(timer))
  }, [])

  const api = useMemo(() => ({ show }), [show])

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast__region">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast--${toast.kind}`} role={toast.kind === 'error' ? 'alert' : 'status'}>
            <span className="toast__message">{toast.message}</span>
            <button type="button" className="toast__dismiss" onClick={() => dismiss(toast.id)} aria-label={t('common.dismiss')}>
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
