import { useEffect, useRef, useState } from 'react'
import type { TableResponse } from '../../../../api/types'
import { Modal } from '../../../../components/Modal'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import '../../../../components/Dialog.css'
import './PairingCodeDialog.css'

type PairingCodeDialogProps = {
  table: TableResponse
  onClose: () => void
}

/**
 * Shows the code a guest device needs, large enough to read across a table and copyable in one press. The code
 * is shown in a left-to-right box in every language, so it never gets reordered inside Arabic text.
 */
export function PairingCodeDialog({ table, onClose }: PairingCodeDialogProps) {
  const { t } = useT()
  const copyRef = useRef<HTMLButtonElement>(null)
  const codeRef = useRef<HTMLOutputElement>(null)
  const [copied, setCopied] = useState(false)
  const code = table.pairedDeviceId ?? ''

  useEffect(() => {
    if (!copied) return
    const timer = window.setTimeout(() => setCopied(false), 2000)
    return () => window.clearTimeout(timer)
  }, [copied])

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
    } catch {
      // No clipboard access (an insecure page, a locked-down browser): select the code so Ctrl+C works.
      const selection = window.getSelection()
      if (codeRef.current && selection) {
        const range = document.createRange()
        range.selectNodeContents(codeRef.current)
        selection.removeAllRanges()
        selection.addRange(range)
      }
    }
  }

  return (
    <Modal title={t('admin.tables.pairingTitle', { table: isolate(table.tableNumber) })} onClose={onClose} size="small" initialFocus={copyRef}>
      <p className="dialog__message">{t('admin.tables.pairingIntro')}</p>
      <output ref={codeRef} className="pairing-dialog__code" dir="ltr">
        {code}
      </output>
      <div className="dialog__actions">
        <span className="pairing-dialog__copied" role="status">
          {copied ? t('common.copied') : ''}
        </span>
        <button ref={copyRef} type="button" className="dialog__button dialog__button--plain" onClick={() => void handleCopy()}>
          {t('common.copy')}
        </button>
        <button type="button" className="dialog__button dialog__button--primary" onClick={onClose}>
          {t('common.done')}
        </button>
      </div>
    </Modal>
  )
}
