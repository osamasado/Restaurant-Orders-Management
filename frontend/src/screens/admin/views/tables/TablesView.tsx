import { useEffect, useState } from 'react'
import { deleteTable, listTables, pairTable, unpairTable } from '../../../../api/tableApi'
import type { DeviceStatus, TableResponse } from '../../../../api/types'
import { useT } from '../../../../i18n/useT'
import { TableFormModal } from './TableFormModal'
import './TablesView.css'

function deviceStatusLabel(status: DeviceStatus, t: (key: string) => string): string {
  switch (status) {
    case 'ONLINE':
      return t('admin.tables.online')
    case 'OFFLINE':
      return t('admin.tables.offline')
    default:
      return t('admin.tables.unpaired')
  }
}

export function TablesView() {
  const { t } = useT()

  const [tables, setTables] = useState<TableResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<TableResponse | null>(null)
  const [showModal, setShowModal] = useState(false)

  const refresh = async () => {
    try {
      const response = await listTables()
      setTables(response)
      setError(null)
    } catch {
      setError(t('admin.tables.loadError'))
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

  const openEditModal = (table: TableResponse) => {
    setEditing(table)
    setShowModal(true)
  }

  const closeModal = () => setShowModal(false)
  const handleSaved = () => {
    closeModal()
    void refresh()
  }

  const handleDelete = async (table: TableResponse) => {
    if (!window.confirm(t('admin.tables.confirmDelete'))) return
    try {
      await deleteTable(table.id)
      void refresh()
    } catch {
      window.alert(t('admin.tables.deleteError'))
    }
  }

  const handlePair = async (table: TableResponse) => {
    try {
      const paired = await pairTable(table.id)
      setTables((prev) => prev.map((existing) => (existing.id === paired.id ? paired : existing)))
      window.alert(t('admin.tables.pairingCode', { code: paired.pairedDeviceId }))
    } catch {
      window.alert(t('admin.tables.pairError'))
    }
  }

  const handleUnpair = async (table: TableResponse) => {
    try {
      const unpaired = await unpairTable(table.id)
      setTables((prev) => prev.map((existing) => (existing.id === unpaired.id ? unpaired : existing)))
    } catch {
      window.alert(t('admin.tables.pairError'))
    }
  }

  return (
    <div className="tables-view">
      <div>
        <h2 className="tables-view__title">{t('admin.nav.tables')}</h2>
        <p className="tables-view__subtitle">{t('admin.tables.subtitle')}</p>
      </div>

      {error && <p className="tables-view__error">{error}</p>}

      <div className="tables-view__header">
        <button className="tables-view__add-button" onClick={openAddModal}>
          {t('admin.tables.add')}
        </button>
      </div>

      <div className="tables-view__table">
        {tables.length === 0 && !loading && <p className="tables-view__empty">{t('admin.tables.empty')}</p>}
        {tables.map((table) => (
          <div className="table-row" key={table.id}>
            <span className="table-row__name">{table.tableNumber}</span>
            <span className="table-row__meta">
              {[table.room, t('admin.tables.seatsCount', { count: table.seats }), table.pairedDeviceId]
                .filter(Boolean)
                .join(' · ')}
            </span>
            <span
              className={`table-row__device table-row__device--${table.deviceStatus.toLowerCase()}`}
            >
              {deviceStatusLabel(table.deviceStatus, t)}
            </span>
            <div className="table-row__actions">
              {table.deviceStatus === 'UNPAIRED' ? (
                <button onClick={() => void handlePair(table)}>{t('admin.tables.pair')}</button>
              ) : (
                <button onClick={() => void handleUnpair(table)}>{t('admin.tables.unpair')}</button>
              )}
              <button onClick={() => openEditModal(table)}>{t('admin.tables.edit')}</button>
              <button onClick={() => void handleDelete(table)}>{t('admin.tables.delete')}</button>
            </div>
          </div>
        ))}
      </div>

      {showModal && <TableFormModal table={editing} onClose={closeModal} onSaved={handleSaved} />}
    </div>
  )
}
