import { useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { createMeal, deleteMealImage, updateMeal, uploadMealImage } from '../../../../api/menuApi'
import type { CategoryResponse, Language, MealRequest, MealResponse } from '../../../../api/types'
import { Modal } from '../../../../components/Modal'
import { PhotoIcon } from '../../../../components/PhotoIcon'
import { useT } from '../../../../i18n/useT'
import './MealFormModal.css'

type MealFormModalProps = {
  /** null means "create a new meal". */
  meal: MealResponse | null
  categories: CategoryResponse[]
  onClose: () => void
  onSaved: () => void
}

type TranslationFields = {
  name: string
  description: string
  preparationMethod: string
  /** Comma-separated for editing convenience; split into an array on save. */
  ingredients: string
}

type TranslationState = Record<Language, TranslationFields>

type SizeState = {
  /** Stable React key independent of the server id (which is null for new rows). */
  localKey: string
  id: number | null
  price: string
  labels: Record<Language, string>
}

const LANGUAGES: Language[] = ['DE', 'EN', 'AR']

function emptyTranslation(): TranslationFields {
  return { name: '', description: '', preparationMethod: '', ingredients: '' }
}

function emptyLabels(): Record<Language, string> {
  return { DE: '', EN: '', AR: '' }
}

function initialTranslations(meal: MealResponse | null): TranslationState {
  const translations: TranslationState = { DE: emptyTranslation(), EN: emptyTranslation(), AR: emptyTranslation() }
  for (const translation of meal?.translations ?? []) {
    translations[translation.language] = {
      name: translation.name,
      description: translation.description ?? '',
      preparationMethod: translation.preparationMethod ?? '',
      ingredients: translation.ingredients.join(', '),
    }
  }
  return translations
}

function initialSizes(meal: MealResponse | null): SizeState[] {
  if (!meal || meal.sizes.length === 0) {
    return [{ localKey: crypto.randomUUID(), id: null, price: '', labels: emptyLabels() }]
  }
  return meal.sizes.map((size) => {
    const labels = emptyLabels()
    for (const translation of size.translations) {
      labels[translation.language] = translation.label
    }
    return { localKey: crypto.randomUUID(), id: size.id, price: String(size.price), labels }
  })
}

function categoryLabel(category: CategoryResponse, language: Language): string {
  const match =
    category.translations.find((t) => t.language === language) ??
    category.translations.find((t) => t.language === 'EN') ??
    category.translations[0]
  return match?.name ?? `#${category.id}`
}

export function MealFormModal({ meal, categories, onClose, onSaved }: MealFormModalProps) {
  const { t } = useT()
  const [categoryId, setCategoryId] = useState<number | ''>(meal?.categoryId ?? categories[0]?.id ?? '')
  const [available, setAvailable] = useState(meal?.available ?? true)
  const [translations, setTranslations] = useState<TranslationState>(() => initialTranslations(meal))
  const [sizes, setSizes] = useState<SizeState[]>(() => initialSizes(meal))
  const [activeLanguage, setActiveLanguage] = useState<Language>('EN')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [imagePreview, setImagePreview] = useState<string | null>(meal?.imageUrl ?? null)
  const [removeImage, setRemoveImage] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const updateTranslationField = (language: Language, field: keyof TranslationFields, value: string) => {
    setTranslations((prev) => ({ ...prev, [language]: { ...prev[language], [field]: value } }))
  }

  const addSize = () => {
    setSizes((prev) => [...prev, { localKey: crypto.randomUUID(), id: null, price: '', labels: emptyLabels() }])
  }

  const removeSize = (localKey: string) => {
    setSizes((prev) => prev.filter((size) => size.localKey !== localKey))
  }

  const updateSizePrice = (localKey: string, price: string) => {
    setSizes((prev) => prev.map((size) => (size.localKey === localKey ? { ...size, price } : size)))
  }

  const updateSizeLabel = (localKey: string, language: Language, label: string) => {
    setSizes((prev) =>
      prev.map((size) =>
        size.localKey === localKey ? { ...size, labels: { ...size.labels, [language]: label } } : size,
      ),
    )
  }

  const handleImageChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null
    setImageFile(file)
    setRemoveImage(false)
    setImagePreview(file ? URL.createObjectURL(file) : (meal?.imageUrl ?? null))
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
      const request: MealRequest = {
        categoryId: Number(categoryId),
        available,
        translations: LANGUAGES.filter((language) => translations[language].name.trim().length > 0).map(
          (language) => ({
            language,
            name: translations[language].name.trim(),
            description: translations[language].description.trim() || null,
            preparationMethod: translations[language].preparationMethod.trim() || null,
            ingredients: translations[language].ingredients
              .split(',')
              .map((ingredient) => ingredient.trim())
              .filter((ingredient) => ingredient.length > 0),
          }),
        ),
        sizes: sizes.map((size) => ({
          id: size.id,
          price: Number(size.price),
          translations: LANGUAGES.filter((language) => size.labels[language].trim().length > 0).map((language) => ({
            language,
            label: size.labels[language].trim(),
          })),
        })),
      }

      const savedMeal = meal ? await updateMeal(meal.id, request) : await createMeal(request)

      if (imageFile) {
        await uploadMealImage(savedMeal.id, imageFile)
      } else if (removeImage && meal?.imageUrl) {
        await deleteMealImage(savedMeal.id)
      }

      onSaved()
    } catch {
      setError(t('admin.meals.error'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={meal ? t('admin.meals.editTitle') : t('admin.meals.addTitle')} onClose={onClose}>
      <form className="meal-form" onSubmit={handleSubmit}>
        <div className="meal-form__image">
          {imagePreview ? (
            <img src={imagePreview} alt="" className="meal-form__image-preview" />
          ) : (
            <div className="meal-form__image-placeholder">
              <PhotoIcon />
            </div>
          )}
          <div className="meal-form__image-actions">
            <label className="meal-form__image-upload">
              {t('admin.meals.uploadImage')}
              <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleImageChange} hidden />
            </label>
            {imagePreview && (
              <button type="button" onClick={handleRemoveImage}>
                {t('admin.meals.removeImage')}
              </button>
            )}
          </div>
        </div>

        <label className="meal-form__field">
          <span>{t('admin.meals.category')}</span>
          <select value={categoryId} onChange={(event) => setCategoryId(Number(event.target.value))} required>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {categoryLabel(category, activeLanguage)}
              </option>
            ))}
          </select>
        </label>

        <label className="meal-form__checkbox">
          <input type="checkbox" checked={available} onChange={(event) => setAvailable(event.target.checked)} />
          <span>{t('admin.meals.available')}</span>
        </label>

        <div className="meal-form__tabs">
          {LANGUAGES.map((language) => (
            <button
              type="button"
              key={language}
              className={activeLanguage === language ? 'meal-form__tab meal-form__tab--active' : 'meal-form__tab'}
              onClick={() => setActiveLanguage(language)}
            >
              {language}
            </button>
          ))}
        </div>

        <div className="meal-form__translation" dir={activeLanguage === 'AR' ? 'rtl' : 'ltr'}>
          <label className="meal-form__field">
            <span>{t('admin.meals.name')}</span>
            <input
              value={translations[activeLanguage].name}
              onChange={(event) => updateTranslationField(activeLanguage, 'name', event.target.value)}
              required={activeLanguage === 'EN'}
            />
          </label>
          <label className="meal-form__field">
            <span>{t('admin.meals.description')}</span>
            <textarea
              value={translations[activeLanguage].description}
              onChange={(event) => updateTranslationField(activeLanguage, 'description', event.target.value)}
            />
          </label>
          <label className="meal-form__field">
            <span>{t('admin.meals.preparationMethod')}</span>
            <textarea
              value={translations[activeLanguage].preparationMethod}
              onChange={(event) => updateTranslationField(activeLanguage, 'preparationMethod', event.target.value)}
            />
          </label>
          <label className="meal-form__field">
            <span>{t('admin.meals.ingredients')}</span>
            <input
              value={translations[activeLanguage].ingredients}
              onChange={(event) => updateTranslationField(activeLanguage, 'ingredients', event.target.value)}
              placeholder={t('admin.meals.ingredientsPlaceholder')}
            />
          </label>
        </div>

        <div className="meal-form__sizes">
          <span className="meal-form__sizes-title">{t('admin.meals.sizes')}</span>
          {sizes.map((size) => (
            <div className="meal-form__size-row" key={size.localKey}>
              <input
                type="number"
                step="0.01"
                min="0"
                value={size.price}
                onChange={(event) => updateSizePrice(size.localKey, event.target.value)}
                placeholder={t('admin.meals.price')}
                required
              />
              <input
                dir={activeLanguage === 'AR' ? 'rtl' : 'ltr'}
                value={size.labels[activeLanguage]}
                onChange={(event) => updateSizeLabel(size.localKey, activeLanguage, event.target.value)}
                placeholder={t('admin.meals.sizeLabel', { language: activeLanguage })}
                required={activeLanguage === 'EN'}
              />
              <button type="button" onClick={() => removeSize(size.localKey)} aria-label={t('admin.meals.removeSize')}>
                &times;
              </button>
            </div>
          ))}
          <button type="button" onClick={addSize} className="meal-form__add-size">
            {t('admin.meals.addSize')}
          </button>
        </div>

        {error && <p className="meal-form__error">{error}</p>}

        <div className="meal-form__actions">
          <button type="button" onClick={onClose}>
            {t('admin.meals.cancel')}
          </button>
          <button type="submit" disabled={saving}>
            {t('admin.meals.save')}
          </button>
        </div>
      </form>
    </Modal>
  )
}
