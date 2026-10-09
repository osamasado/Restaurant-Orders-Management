import { useEffect, useState } from 'react'
import { deleteAccount, listAccounts } from '../../../../api/staffApi'
import type { StaffResponse } from '../../../../api/types'
import { useAuth } from '../../../../auth/auth-context'
import { ConfirmDialog } from '../../../../components/ConfirmDialog'
import { useToast } from '../../../../components/toast-context'
import { useLanguage } from '../../../../i18n/language-context'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import { LOCALE_BY_LANGUAGE } from '../../../../lib/formatMoney'
import type { Language } from '../../../../i18n/i18n'
import { ResetPinDialog } from './ResetPinDialog'
import { StaffFormModal } from './StaffFormModal'
import './StaffView.css'

/** In the app's language, not the browser's - otherwise an Arabic screen can show German dates. */
function formatLastSeenAt(lastSeenAt: string, language: Language): string {
  return new Date(lastSeenAt).toLocaleString(LOCALE_BY_LANGUAGE[language])
}

export function StaffView() {
  const { t } = useT()
  const { language } = useLanguage()
  const { staff: currentStaff } = useAuth()
  const toast = useToast()

  const [accounts, setAccounts] = useState<StaffResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<StaffResponse | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [deleting, setDeleting] = useState<StaffResponse | null>(null)
  const [resettingPin, setResettingPin] = useState<StaffResponse | null>(null)

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

  return (
    <div className="staff-view">
      <div>
        <h2 className="staff-view__title">{t('admin.nav.staff')}</h2>
        <p className="staff-view__subtitle">{t('admin.staff.subtitle')}</p>
      </div>

      {error && <p className="staff-view__error">{error}</p>}

      <div className="staff-view__header">
        <button className="staff-view__add-button button button--small button--primary" onClick={openAddModal}>
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
                  ? t('admin.staff.lastSeenAt', { date: formatLastSeenAt(account.lastSeenAt, language) })
                  : t('admin.staff.neverSignedIn'),
              ].join(' · ')}
            </span>
            <div className="staff-row__actions">
              <button className="button button--small" onClick={() => openEditModal(account)}>{t('admin.staff.edit')}</button>
              <button className="button button--small" onClick={() => setResettingPin(account)}>{t('admin.staff.resetPin')}</button>
              {currentStaff?.id !== account.id && (
                <button className="button button--small button--danger" onClick={() => setDeleting(account)}>{t('admin.staff.delete')}</button>
              )}
            </div>
          </div>
        ))}
      </div>

      {showModal && <StaffFormModal staff={editing} onClose={closeModal} onSaved={handleSaved} />}

      {deleting && (
        <ConfirmDialog
          title={t('admin.staff.deleteTitle', { name: isolate(deleting.name) })}
          message={t('admin.staff.deleteMessage', { name: isolate(deleting.name) })}
          confirmLabel={t('admin.staff.deleteConfirm')}
          errorMessage={t('admin.staff.deleteError')}
          onConfirm={async () => {
            await deleteAccount(deleting.id)
            await refresh()
          }}
          onClose={() => setDeleting(null)}
        />
      )}

      {resettingPin && (
        <ResetPinDialog
          account={resettingPin}
          onClose={() => setResettingPin(null)}
          onDone={() => {
            toast.show('success', t('admin.staff.resetPinSuccess', { name: isolate(resettingPin.name) }))
            setResettingPin(null)
          }}
        />
      )}
    </div>
  )
}
