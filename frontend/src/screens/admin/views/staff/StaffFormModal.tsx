import { useState } from 'react'
import type { FormEvent } from 'react'
import { createAccount, updateAccount } from '../../../../api/staffApi'
import type { Role, StaffResponse } from '../../../../api/types'
import { Modal } from '../../../../components/Modal'
import { useT } from '../../../../i18n/useT'
import './StaffFormModal.css'

/** Role.USER is reserved for a future guest-login feature - not selectable here. */
const ASSIGNABLE_ROLES: Role[] = ['ADMIN', 'KITCHEN', 'WAITER', 'CASHIER']

type StaffFormModalProps = {
  /** null means "create a new staff account". */
  staff: StaffResponse | null
  onClose: () => void
  onSaved: () => void
}

export function StaffFormModal({ staff, onClose, onSaved }: StaffFormModalProps) {
  const { t } = useT()
  const [name, setName] = useState(staff?.name ?? '')
  const [role, setRole] = useState<Role>(staff?.role ?? 'WAITER')
  const [pin, setPin] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      if (staff) {
        await updateAccount(staff.id, { name: name.trim(), role })
      } else {
        await createAccount({ name: name.trim(), role, pin: pin.trim() })
      }
      onSaved()
    } catch {
      setError(t('admin.staff.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={staff ? t('admin.staff.editTitle') : t('admin.staff.addTitle')} onClose={onClose}>
      <form className="staff-form" onSubmit={handleSubmit}>
        <label className="staff-form__field">
          <span>{t('admin.staff.name')}</span>
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <label className="staff-form__field">
          <span>{t('admin.staff.role')}</span>
          <select value={role} onChange={(event) => setRole(event.target.value as Role)} required>
            {ASSIGNABLE_ROLES.map((assignableRole) => (
              <option key={assignableRole} value={assignableRole}>
                {t(`admin.staff.roles.${assignableRole}`)}
              </option>
            ))}
          </select>
        </label>
        {!staff && (
          <label className="staff-form__field">
            <span>{t('admin.staff.pin')}</span>
            <input
              type="password"
              inputMode="numeric"
              value={pin}
              onChange={(event) => setPin(event.target.value)}
              required
            />
          </label>
        )}

        {error && <p className="staff-form__error">{error}</p>}

        <div className="staff-form__actions">
          <button type="button" onClick={onClose}>
            {t('admin.staff.cancel')}
          </button>
          <button type="submit" disabled={saving}>
            {t('admin.staff.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
