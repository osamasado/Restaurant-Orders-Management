import { useT } from '../../i18n/useT'
import { MealThumb } from '../MealThumb'
import './OrderItem.css'

type OrderItemProps = {
  name: string
  size: string
  quantity: number
  note: string | null
  /** The meal's photo, when the menu still has a meal by this name; otherwise a plate icon shows. */
  imageUrl?: string | null
}

/**
 * One line of an order, as it was ordered: a small round picture of the meal, the dish, its size and the guest's
 * note, and how many. The name and size are the snapshot the order took when it was placed, so a later menu edit
 * never changes what the line says; the picture is the menu's current one (the order does not store photos).
 */
export function OrderItem({ name, size, quantity, note, imageUrl }: OrderItemProps) {
  const { t } = useT()
  return (
    <li className="order-item">
      <MealThumb imageUrl={imageUrl} />
      <div className="order-item__text">
        <p className="order-item__name">
          <bdi>{name}</bdi>
        </p>
        {size && (
          <p className="order-item__size">
            <bdi>{size}</bdi>
          </p>
        )}
        {note && (
          <p className="order-item__note">
            <bdi>{note}</bdi>
          </p>
        )}
      </div>
      <span className="order-item__quantity" aria-label={t('admin.dashboard.quantity', { count: quantity })}>
        <span aria-hidden="true" dir="ltr">
          {quantity}&times;
        </span>
      </span>
    </li>
  )
}
