import { useState } from 'react'
import type { FormEvent } from 'react'
import { createCategory, updateCategory } from '../../../../api/menuApi'
import type { CategoryRequest, CategoryResponse, Language } from '../../../../api/types'
import { Modal } from '../../../../components/Modal'
import { useT } from '../../../../i18n/useT'
import './CategoryFormModal.css'

type CategoryFormModalProps = {
  /** null means "create a new category". */
  category: CategoryResponse | null
  onClose: () => void
  onSaved: () => void
}

function initialNames(category: CategoryResponse | null): Record<Language, string> {
  const names: Record<Language, string> = { DE: '', EN: '', AR: '' }
  for (const translation of category?.translations ?? []) {
    names[translation.language] = translation.name
  }
  return names
}

export function CategoryFormModal({ category, onClose, onSaved }: CategoryFormModalProps) {
  const { t } = useT()
  const [sortOrder, setSortOrder] = useState(category?.sortOrder ?? 0)
  const [names, setNames] = useState<Record<Language, string>>(() => initialNames(category))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const request: CategoryRequest = {
        sortOrder,
        translations: (Object.keys(names) as Language[])
          .filter((language) => names[language].trim().length > 0)
          .map((language) => ({ language, name: names[language].trim() })),
      }
      if (category) {
        await updateCategory(category.id, request)
      } else {
        await createCategory(request)
      }
      onSaved()
    } catch {
      setError(t('admin.categories.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={category ? t('admin.categories.editTitle') : t('admin.categories.addTitle')} onClose={onClose}>
      <form className="category-form" onSubmit={handleSubmit}>
        <label className="category-form__field">
          <span>{t('admin.categories.sortOrder')}</span>
          <input
            type="number"
            value={sortOrder}
            onChange={(event) => setSortOrder(Number(event.target.value))}
            required
          />
        </label>
        <label className="category-form__field">
          <span>{t('admin.categories.nameDe')}</span>
          <input value={names.DE} onChange={(event) => setNames({ ...names, DE: event.target.value })} />
        </label>
        <label className="category-form__field">
          <span>{t('admin.categories.nameEn')}</span>
          <input value={names.EN} onChange={(event) => setNames({ ...names, EN: event.target.value })} required />
        </label>
        <label className="category-form__field">
          <span>{t('admin.categories.nameAr')}</span>
          <input dir="rtl" value={names.AR} onChange={(event) => setNames({ ...names, AR: event.target.value })} />
        </label>
        {error && <p className="category-form__error">{error}</p>}
        <div className="category-form__actions">
          <button type="button" onClick={onClose}>
            {t('admin.categories.cancel')}
          </button>
          <button type="submit" disabled={saving}>
            {t('admin.categories.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
