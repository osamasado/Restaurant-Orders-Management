import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import {
  createRawMaterial,
  deleteRawMaterialImage,
  updateRawMaterial,
  uploadRawMaterialImage,
} from '../../../../api/rawMaterialApi'
import type { RawMaterialRequest, RawMaterialResponse } from '../../../../api/types'
import { Modal } from '../../../../components/Modal'
import { PhotoIcon } from '../../../../components/PhotoIcon'
import { useT } from '../../../../i18n/useT'
import './RawMaterialFormModal.css'

type RawMaterialFormModalProps = {
  /** null means "create a new raw material". */
  rawMaterial: RawMaterialResponse | null
  onClose: () => void
  onSaved: () => void
}

export function RawMaterialFormModal({ rawMaterial, onClose, onSaved }: RawMaterialFormModalProps) {
  const { t } = useT()
  const [name, setName] = useState(rawMaterial?.name ?? '')
  const [unit, setUnit] = useState(rawMaterial?.unit ?? '')
  const [inStockQuantity, setInStockQuantity] = useState(
    rawMaterial?.inStockQuantity != null ? String(rawMaterial.inStockQuantity) : '',
  )
  const [supplier, setSupplier] = useState(rawMaterial?.supplier ?? '')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(rawMaterial?.imageUrl ?? null)
  const [removeImage, setRemoveImage] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setImageFile(file)
    setRemoveImage(false)
    setImagePreview(file ? URL.createObjectURL(file) : (rawMaterial?.imageUrl ?? null))
  }

  const handleRemoveImage = () => {
    setImageFile(null)
    setImagePreview(null)
    setRemoveImage(true)
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const request: RawMaterialRequest = {
        name: name.trim(),
        unit: unit.trim(),
        inStockQuantity: inStockQuantity.trim() === '' ? null : Number(inStockQuantity),
        supplier: supplier.trim() || null,
      }

      const saved = rawMaterial ? await updateRawMaterial(rawMaterial.id, request) : await createRawMaterial(request)

      if (imageFile) {
        await uploadRawMaterialImage(saved.id, imageFile)
      } else if (removeImage && rawMaterial?.imageUrl) {
        await deleteRawMaterialImage(saved.id)
      }

      onSaved()
    } catch {
      setError(t('admin.materials.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={rawMaterial ? t('admin.materials.editTitle') : t('admin.materials.addTitle')} onClose={onClose}>
      <form className="raw-material-form" onSubmit={handleSubmit}>
        <div className="raw-material-form__image">
          {imagePreview ? (
            <img src={imagePreview} alt="" className="raw-material-form__image-preview" />
          ) : (
            <div className="raw-material-form__image-placeholder">
              <PhotoIcon />
            </div>
          )}
          <div className="raw-material-form__image-actions">
            <label className="raw-material-form__image-upload">
              {t('admin.materials.uploadImage')}
              <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleImageChange} hidden />
            </label>
            {imagePreview && (
              <button type="button" onClick={handleRemoveImage}>
                {t('admin.materials.removeImage')}
              </button>
            )}
          </div>
        </div>

        <label className="raw-material-form__field">
          <span>{t('admin.materials.name')}</span>
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <label className="raw-material-form__field">
          <span>{t('admin.materials.unit')}</span>
          <input value={unit} onChange={(event) => setUnit(event.target.value)} required />
        </label>
        <label className="raw-material-form__field">
          <span>{t('admin.materials.inStockQuantity')}</span>
          <input
            type="number"
            step="0.01"
            min="0"
            value={inStockQuantity}
            onChange={(event) => setInStockQuantity(event.target.value)}
          />
        </label>
        <label className="raw-material-form__field">
          <span>{t('admin.materials.supplier')}</span>
          <input value={supplier} onChange={(event) => setSupplier(event.target.value)} />
        </label>

        {error && <p className="raw-material-form__error">{error}</p>}

        <div className="raw-material-form__actions">
          <button type="button" onClick={onClose}>
            {t('admin.materials.cancel')}
          </button>
          <button type="submit" disabled={saving}>
            {t('admin.materials.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
