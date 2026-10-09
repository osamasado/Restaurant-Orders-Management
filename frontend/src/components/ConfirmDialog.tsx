import { useRef, useState } from 'react'
import { useT } from '../i18n/useT'
import { Modal } from './Modal'
import './Dialog.css'

type ConfirmDialogProps = {
  /** The question, naming what it is about: "Delete table 7?". */
  title: string
  /** What happens, in a sentence. */
  message: string
  /** Verb first and specific: "Delete table", not "OK". */
  confirmLabel: string
  /** Shown inside the dialog when the action fails, and the dialog stays open for another try. */
  errorMessage: string
  /** The action itself. It runs inside the dialog, so a failure can be shown right here. Throw to fail. */
  onConfirm: () => Promise<void>
  onClose: () => void
}

/**
 * Asks before something that cannot be undone. The safe button has focus when it opens, the dangerous one is
 * clay, and the action runs in here: while it runs nothing can be dismissed, and if it fails the reason is
 * shown in the dialog with "Try again" instead of a second browser alert.
 */
export function ConfirmDialog({ title, message, confirmLabel, errorMessage, onConfirm, onClose }: ConfirmDialogProps) {
  const { t } = useT()
  const cancelRef = useRef<HTMLButtonElement>(null)
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)

  const handleConfirm = async () => {
    setBusy(true)
    setFailed(false)
    try {
      await onConfirm()
      onClose()
    } catch {
      setFailed(true)
      setBusy(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose} size="small" initialFocus={cancelRef} dismissible={!busy}>
      <p className="dialog__message">{message}</p>
      {failed && (
        <p className="dialog__error" role="alert">
          {errorMessage}
        </p>
      )}
      <div className="dialog__actions">
        <button ref={cancelRef} type="button" className="button" onClick={onClose} disabled={busy}>
          {t('common.cancel')}
        </button>
        <button type="button" className="button button--danger" onClick={() => void handleConfirm()} disabled={busy} aria-busy={busy}>
          {busy ? t('common.working') : failed ? t('common.tryAgain') : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
