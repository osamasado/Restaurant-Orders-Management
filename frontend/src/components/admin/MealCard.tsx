import { useState } from 'react'
import { useT } from '../../i18n/useT'
import { isolate } from '../../lib/bidi'
import { Icon } from '../Icon'
import './MealCard.css'

type MealCardProps = {
  name: string
  description: string | null
  imageUrl: string | null
  categoryName: string
  /** The cheapest size, already formatted; null when the meal has no size yet. */
  price: string | null
  available: boolean
  busy: boolean
  onToggleAvailability: () => void
}

/**
 * One meal: its photo, name, short description, category, starting price and whether it can be ordered. The one
 * action is the same switch the Meals page has (sold out / back on the menu). A meal with no photo, or a photo that
 * does not load, shows a neutral tile instead of a broken-image icon.
 */
export function MealCard({
  name,
  description,
  imageUrl,
  categoryName,
  price,
  available,
  busy,
  onToggleAvailability,
}: MealCardProps) {
  const { t } = useT()
  const [imageFailed, setImageFailed] = useState(false)
  const showImage = imageUrl !== null && !imageFailed

  return (
    <article className={`meal-tile${available ? '' : ' meal-tile--unavailable'}`}>
      <div className="meal-tile__image">
        {showImage ? (
          <img src={imageUrl} alt="" loading="lazy" onError={() => setImageFailed(true)} />
        ) : (
          <Icon name="utensils" size={32} />
        )}
      </div>
      <div className="meal-tile__body">
        <div className="meal-tile__heading">
          <h3 className="meal-tile__name">
            <bdi>{name}</bdi>
          </h3>
          <span className={`meal-tile__availability meal-tile__availability--${available ? 'on' : 'off'}`}>
            {available ? t('admin.meals.available') : t('admin.meals.unavailable')}
          </span>
        </div>
        <p className="meal-tile__category">{categoryName}</p>
        {description && <p className="meal-tile__description">{description}</p>}
        <div className="meal-tile__footer">
          {price && <span className="meal-tile__price">{t('admin.dashboard.fromPrice', { price: isolate(price) })}</span>}
          <button type="button" className={`button button--small button--block ${available ? 'button--warning' : 'button--success'}`} disabled={busy} onClick={onToggleAvailability}>
            {available ? t('admin.dashboard.markUnavailable') : t('admin.dashboard.markAvailable')}
          </button>
        </div>
      </div>
    </article>
  )
}
