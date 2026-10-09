import { Fragment, useEffect, useState } from 'react'
import { deleteTable, listTables, pairTable, unpairTable } from '../../../../api/tableApi'
import type { DeviceStatus, TableResponse } from '../../../../api/types'
import { ConfirmDialog } from '../../../../components/ConfirmDialog'
import { useToast } from '../../../../components/toast-context'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import { PairingCodeDialog } from './PairingCodeDialog'
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
  const toast = useToast()

  const [tables, setTables] = useState<TableResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<TableResponse | null>(null)
  const [showModal, setShowModal] = useState(false)
  const [deleting, setDeleting] = useState<TableResponse | null>(null)
  const [pairedTable, setPairedTable] = useState<TableResponse | null>(null)

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

  const handlePair = async (table: TableResponse) => {
    try {
      const paired = await pairTable(table.id)
      setTables((prev) => prev.map((existing) => (existing.id === paired.id ? paired : existing)))
      setPairedTable(paired)
    } catch {
      toast.show('error', t('admin.tables.pairError'))
    }
  }

  const handleUnpair = async (table: TableResponse) => {
    try {
      const unpaired = await unpairTable(table.id)
      setTables((prev) => prev.map((existing) => (existing.id === unpaired.id ? unpaired : existing)))
    } catch {
      toast.show('error', t('admin.tables.pairError'))
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
        <button className="tables-view__add-button button button--small button--primary" onClick={openAddModal}>
          {t('admin.tables.add')}
        </button>
      </div>

      <div className="tables-view__table">
        {tables.length === 0 && !loading && <p className="tables-view__empty">{t('admin.tables.empty')}</p>}
        {tables.map((table) => (
          <div className="table-row" key={table.id}>
            <span className="table-row__name">{table.tableNumber}</span>
            <span className="table-row__meta">
              {[
                { key: 'room', node: table.room && <bdi>{table.room}</bdi> },
                { key: 'seats', node: t('admin.tables.seatsCount', { count: table.seats }) },
                { key: 'device', node: table.pairedDeviceId && <bdi>{table.pairedDeviceId}</bdi> },
              ]
                .filter((part) => part.node)
                .map((part, index) => (
                  <Fragment key={part.key}>
                    {index > 0 && ' · '}
                    {part.node}
                  </Fragment>
                ))}
            </span>
            <span
              className={`table-row__device table-row__device--${table.deviceStatus.toLowerCase()}`}
            >
              {deviceStatusLabel(table.deviceStatus, t)}
            </span>
            <div className="table-row__actions">
              {table.deviceStatus === 'UNPAIRED' ? (
                <button className="button button--small button--primary" onClick={() => void handlePair(table)}>{t('admin.tables.pair')}</button>
              ) : (
                <button className="button button--small button--warning" onClick={() => void handleUnpair(table)}>{t('admin.tables.unpair')}</button>
              )}
              <button className="button button--small" onClick={() => openEditModal(table)}>{t('admin.tables.edit')}</button>
              <button className="button button--small button--danger" onClick={() => setDeleting(table)}>{t('admin.tables.delete')}</button>
            </div>
          </div>
        ))}
      </div>

      {showModal && <TableFormModal table={editing} onClose={closeModal} onSaved={handleSaved} />}

      {deleting && (
        <ConfirmDialog
          title={t('admin.tables.deleteTitle', { name: isolate(deleting.tableNumber) })}
          message={t('admin.tables.deleteMessage')}
          confirmLabel={t('admin.tables.deleteConfirm')}
          errorMessage={t('admin.tables.deleteError')}
          onConfirm={async () => {
            await deleteTable(deleting.id)
            await refresh()
          }}
          onClose={() => setDeleting(null)}
        />
      )}

      {pairedTable && <PairingCodeDialog table={pairedTable} onClose={() => setPairedTable(null)} />}
    </div>
  )
}
