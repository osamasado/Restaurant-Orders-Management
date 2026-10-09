import { useState } from 'react'
import { Icon } from './Icon'
import './MealThumb.css'

type MealThumbProps = {
  imageUrl: string | null | undefined
  /** Pixel size of the circle; 44 suits a line of an order. */
  size?: number
}

/**
 * A small round picture of a meal, to sit beside its name. It is decoration (the name is right next to it), so it
 * has no text of its own. A meal without a photo, or a photo that does not load, shows a neutral plate icon in
 * the same circle instead of a broken-image mark, so every line keeps the same shape.
 */
export function MealThumb({ imageUrl, size = 44 }: MealThumbProps) {
  const [failed, setFailed] = useState(false)
  const showImage = Boolean(imageUrl) && !failed
  return (
    <span className="meal-thumb" style={{ width: size, height: size }} aria-hidden="true">
      {showImage ? (
        <img src={imageUrl ?? undefined} alt="" loading="lazy" onError={() => setFailed(true)} />
      ) : (
        <Icon name="utensils" size={Math.round(size * 0.45)} />
      )}
    </span>
  )
}
