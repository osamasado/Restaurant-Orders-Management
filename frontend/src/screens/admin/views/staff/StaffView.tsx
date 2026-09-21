import { useEffect, useState } from 'react'
import { deleteAccount, listAccounts, resetPin } from '../../../../api/staffApi'
import type { StaffResponse } from '../../../../api/types'
import { useAuth } from '../../../../auth/auth-context'
import { useT } from '../../../../i18n/useT'
import { StaffFormModal } from './StaffFormModal'
import './StaffView.css'

function formatLastSeenAt(lastSeenAt: string): string {
  return new Date(lastSeenAt).toLocaleString()
}

export function StaffView() {
  const { t } = useT()
  const { staff: currentStaff } = useAuth()

  const [accounts, setAccounts] = useState<StaffResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<StaffResponse | null>(null)
  const [showModal, setShowModal] = useState(false)

  const refresh = async () => {
    try {
      const response = await listAccounts()
      setAccounts(response)
      setError(null)
    } catch {
      setError(t('admin.staff.loadError'))
    } finally {
      setLoading(false)
    }
  }

  // Fetch-on-mount: see MealsView's identical effect for why the hooks
  // lint rules are suppressed here (no data library in this project yet).
  // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
  useEffect(() => {
    void refresh()
  }, [])

  const openAddModal = () => {
    setEditing(null)
    setShowModal(true)
  }

  const openEditModal = (account: StaffResponse) => {
    setEditing(account)
    setShowModal(true)
  }

  const closeModal = () => setShowModal(false)
  const handleSaved = () => {
    closeModal()
    void refresh()
  }

  const handleDelete = async (account: StaffResponse) => {
    if (!window.confirm(t('admin.staff.confirmDelete'))) return
    try {
      await deleteAccount(account.id)
      void refresh()
    } catch {
      window.alert(t('admin.staff.deleteError'))
    }
  }

  const handleResetPin = async (account: StaffResponse) => {
    const pin = window.prompt(t('admin.staff.resetPinPrompt'))
    if (!pin) return
    try {
      await resetPin(account.id, pin)
      window.alert(t('admin.staff.resetPinSuccess'))
    } catch {
      window.alert(t('admin.staff.resetPinError'))
    }
  }

  return (
    <div className="staff-view">
      <div>
        <h2 className="staff-view__title">{t('admin.nav.staff')}</h2>
        <p className="staff-view__subtitle">{t('admin.staff.subtitle')}</p>
      </div>

      {error && <p className="staff-view__error">{error}</p>}

      <div className="staff-view__header">
        <button className="staff-view__add-button" onClick={openAddModal}>
          {t('admin.staff.add')}
        </button>
      </div>

      <div className="staff-view__table">
        {accounts.length === 0 && !loading && <p className="staff-view__empty">{t('admin.staff.empty')}</p>}
        {accounts.map((account) => (
          <div className="staff-row" key={account.id}>
            <span className="staff-row__name">{account.name}</span>
            <span className="staff-row__meta">
              {[
                t(`admin.staff.roles.${account.role}`),
                account.lastSeenAt
                  ? t('admin.staff.lastSeenAt', { date: formatLastSeenAt(account.lastSeenAt) })
                  : t('admin.staff.neverSignedIn'),
              ].join(' · ')}
            </span>
            <div className="staff-row__actions">
              <button onClick={() => openEditModal(account)}>{t('admin.staff.edit')}</button>
              <button onClick={() => void handleResetPin(account)}>{t('admin.staff.resetPin')}</button>
              {currentStaff?.id !== account.id && (
                <button onClick={() => void handleDelete(account)}>{t('admin.staff.delete')}</button>
              )}
            </div>
          </div>
        ))}
      </div>

      {showModal && <StaffFormModal staff={editing} onClose={closeModal} onSaved={handleSaved} />}
    </div>
  )
}
