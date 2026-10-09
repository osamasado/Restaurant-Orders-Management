import { useId, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { resetPin } from '../../../../api/staffApi'
import type { StaffResponse } from '../../../../api/types'
import { Modal } from '../../../../components/Modal'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import '../../../../components/Dialog.css'
import './ResetPinDialog.css'

const PIN_PATTERN = /^[0-9]{4,8}$/

type ResetPinDialogProps = {
  account: StaffResponse
  onClose: () => void
  /** The PIN was changed. */
  onDone: () => void
}

/**
 * Sets a new PIN for one staff account. The field has a visible label and its rule ("4 to 8 digits"), shows the
 * numeric keypad on a tablet, and tells you what is wrong under the field once you leave it (and takes it back
 * the moment it is fixed). "Show PIN" is there because there is no second field to catch a typing mistake.
 */
export function ResetPinDialog({ account, onClose, onDone }: ResetPinDialogProps) {
  const { t } = useT()
  const inputRef = useRef<HTMLInputElement>(null)
  const hintId = useId()
  const errorId = useId()
  const [pin, setPin] = useState('')
  const [shown, setShown] = useState(false)
  const [invalid, setInvalid] = useState(false)
  const [saving, setSaving] = useState(false)
  const [failed, setFailed] = useState(false)

  const handleChange = (value: string) => {
    setPin(value)
    setFailed(false)
    // The complaint goes away as soon as the PIN is fine; it only ever appears when leaving the field or submitting.
    if (invalid && PIN_PATTERN.test(value.trim())) setInvalid(false)
  }

  const handleBlur = () => {
    if (pin !== '') setInvalid(!PIN_PATTERN.test(pin.trim()))
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!PIN_PATTERN.test(pin.trim())) {
      setInvalid(true)
      inputRef.current?.focus()
      return
    }
    setSaving(true)
    setFailed(false)
    try {
      await resetPin(account.id, pin.trim())
      onDone()
    } catch {
      setFailed(true)
      setSaving(false)
    }
  }

  return (
    <Modal title={t('admin.staff.resetPinTitle', { name: isolate(account.name) })} onClose={onClose} size="small" initialFocus={inputRef} dismissible={!saving}>
      <form className="pin-dialog" onSubmit={(event) => void handleSubmit(event)} noValidate>
        <label className="pin-dialog__field">
          <span>{t('admin.staff.newPin')}</span>
          <input
            ref={inputRef}
            type={shown ? 'text' : 'password'}
            inputMode="numeric"
            autoComplete="new-password"
            maxLength={8}
            dir="ltr"
            value={pin}
            onChange={(event) => handleChange(event.target.value)}
            onBlur={handleBlur}
            aria-invalid={invalid}
            aria-describedby={invalid ? `${hintId} ${errorId}` : hintId}
          />
        </label>
        <span id={hintId} className="pin-dialog__hint">
          {t('admin.staff.pinHint')}
        </span>
        {invalid && (
          <p id={errorId} className="pin-dialog__error" role="alert">
            {t('admin.staff.pinInvalid')}
          </p>
        )}
        <label className="pin-dialog__show">
          <input type="checkbox" checked={shown} onChange={(event) => setShown(event.target.checked)} />
          <span>{t('admin.staff.showPin')}</span>
        </label>

        {failed && (
          <p className="dialog__error" role="alert">
            {t('admin.staff.resetPinError')}
          </p>
        )}

        <div className="dialog__actions">
          <button type="button" className="button" onClick={onClose} disabled={saving}>
            {t('common.cancel')}
          </button>
          <button type="submit" className="button button--primary" disabled={saving} aria-busy={saving}>
            {saving ? t('common.working') : failed ? t('common.tryAgain') : t('admin.staff.resetPinSubmit')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
