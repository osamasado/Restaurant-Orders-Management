import { useState } from 'react'
import type { FormEvent } from 'react'
import { createTable, updateTable } from '../../../../api/tableApi'
import type { TableRequest, TableResponse } from '../../../../api/types'
import { Modal } from '../../../../components/Modal'
import { useT } from '../../../../i18n/useT'
import './TableFormModal.css'

type TableFormModalProps = {
  /** null means "create a new table". */
  table: TableResponse | null
  onClose: () => void
  onSaved: () => void
}

export function TableFormModal({ table, onClose, onSaved }: TableFormModalProps) {
  const { t } = useT()
  const [tableNumber, setTableNumber] = useState(table?.tableNumber ?? '')
  const [room, setRoom] = useState(table?.room ?? '')
  const [seats, setSeats] = useState(table ? String(table.seats) : '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const request: TableRequest = {
        tableNumber: tableNumber.trim(),
        room: room.trim(),
        seats: Number(seats),
      }
      if (table) {
        await updateTable(table.id, request)
      } else {
        await createTable(request)
      }
      onSaved()
    } catch {
      setError(t('admin.tables.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={table ? t('admin.tables.editTitle') : t('admin.tables.addTitle')} onClose={onClose}>
      <form className="table-form" onSubmit={handleSubmit}>
        <label className="table-form__field">
          <span>{t('admin.tables.tableNumber')}</span>
          <input value={tableNumber} onChange={(event) => setTableNumber(event.target.value)} required />
        </label>
        <label className="table-form__field">
          <span>{t('admin.tables.room')}</span>
          <input value={room} onChange={(event) => setRoom(event.target.value)} required />
        </label>
        <label className="table-form__field">
          <span>{t('admin.tables.seats')}</span>
          <input
            type="number"
            min="1"
            step="1"
            value={seats}
            onChange={(event) => setSeats(event.target.value)}
            required
          />
        </label>

        {error && <p className="table-form__error">{error}</p>}

        <div className="table-form__actions">
          <button type="button" onClick={onClose}>
            {t('admin.tables.cancel')}
          </button>
          <button type="submit" disabled={saving}>
            {t('admin.tables.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
