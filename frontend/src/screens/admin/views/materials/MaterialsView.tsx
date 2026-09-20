import { useEffect, useState } from 'react'
import { deleteRawMaterial, listRawMaterials } from '../../../../api/rawMaterialApi'
import type { RawMaterialResponse } from '../../../../api/types'
import { useT } from '../../../../i18n/useT'
import { RawMaterialFormModal } from './RawMaterialFormModal'
import './MaterialsView.css'

export function MaterialsView() {
  const { t } = useT()

  const [rawMaterials, setRawMaterials] = useState<RawMaterialResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<RawMaterialResponse | null>(null)
  const [showModal, setShowModal] = useState(false)

  const refresh = async () => {
    try {
      const response = await listRawMaterials()
      setRawMaterials(response)
      setError(null)
    } catch {
      setError(t('admin.materials.loadError'))
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

  const openEditModal = (rawMaterial: RawMaterialResponse) => {
    setEditing(rawMaterial)
    setShowModal(true)
  }

  const closeModal = () => setShowModal(false)
  const handleSaved = () => {
    closeModal()
    void refresh()
  }

  const handleDelete = async (rawMaterial: RawMaterialResponse) => {
    if (!window.confirm(t('admin.materials.confirmDelete'))) return
    try {
      await deleteRawMaterial(rawMaterial.id)
      void refresh()
    } catch {
      window.alert(t('admin.materials.deleteError'))
    }
  }

  return (
    <div className="materials-view">
      <div>
        <h2 className="materials-view__title">{t('admin.nav.materials')}</h2>
        <p className="materials-view__subtitle">{t('admin.materials.subtitle')}</p>
      </div>

      {error && <p className="materials-view__error">{error}</p>}

      <div className="materials-view__header">
        <button className="materials-view__add-button" onClick={openAddModal}>
          {t('admin.materials.add')}
        </button>
      </div>

      <div className="materials-view__table">
        {rawMaterials.length === 0 && !loading && (
          <p className="materials-view__empty">{t('admin.materials.empty')}</p>
        )}
        {rawMaterials.map((rawMaterial) => (
          <div className="material-row" key={rawMaterial.id}>
            {rawMaterial.imageUrl ? (
              <img src={rawMaterial.imageUrl} alt="" className="material-row__photo" />
            ) : (
              <div className="material-row__placeholder" aria-hidden="true" />
            )}
            <span className="material-row__name">{rawMaterial.name}</span>
            <span className="material-row__meta">
              {[
                rawMaterial.unit,
                rawMaterial.inStockQuantity != null
                  ? t('admin.materials.inStock', { quantity: rawMaterial.inStockQuantity })
                  : null,
                rawMaterial.supplier,
              ]
                .filter(Boolean)
                .join(' · ')}
            </span>
            <div className="material-row__actions">
              <button onClick={() => openEditModal(rawMaterial)}>{t('admin.materials.edit')}</button>
              <button onClick={() => void handleDelete(rawMaterial)}>{t('admin.materials.delete')}</button>
            </div>
          </div>
        ))}
      </div>

      {showModal && <RawMaterialFormModal rawMaterial={editing} onClose={closeModal} onSaved={handleSaved} />}
    </div>
  )
}
