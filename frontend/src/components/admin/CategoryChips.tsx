import type { ReactNode } from 'react'
import { Icon } from '../Icon'
import { Slider } from '../Slider'
import './CategoryChips.css'

export type CategoryChip = {
  /** null is the "All" chip. */
  id: number | null
  name: string
  count: number
  imageUrl: string | null
}

type CategoryChipsProps = {
  /** The section heading shown above the chips. */
  heading: ReactNode
  chips: CategoryChip[]
  selectedId: number | null
  onSelect: (id: number | null) => void
  /** The accessible name of the group. */
  label: string
  /** "Meals: {{count}}", already translated. */
  countLabel: (count: number) => string
  rangeLabel: (from: number, to: number, total: number) => string
}

/**
 * The categories as round pictures with their names, in a slider that shows as many as fit and moves on with
 * "View more". The picture is a meal of that category (a category has none of its own); with no photo it shows a
 * neutral icon. The chosen one gets the coral ring and bold name, and the chips work like toggle buttons, so a
 * keyboard user can pick one too.
 */
export function CategoryChips({ heading, chips, selectedId, onSelect, label, countLabel, rangeLabel }: CategoryChipsProps) {
  return (
    <Slider heading={heading} label={label} minItemWidth={92} gap={12} rangeLabel={rangeLabel}>
      {chips.map((chip) => {
        const selected = chip.id === selectedId
        return (
          <button
            key={chip.id ?? 'all'}
            type="button"
            className={`category-chip${selected ? ' category-chip--selected' : ''}`}
            aria-pressed={selected}
            onClick={() => onSelect(chip.id)}
          >
            <span className="category-chip__picture">
              {chip.imageUrl ? <img src={chip.imageUrl} alt="" loading="lazy" /> : <Icon name="utensils" size={22} />}
            </span>
            <span className="category-chip__name">{chip.name}</span>
            <span className="category-chip__count">{countLabel(chip.count)}</span>
          </button>
        )
      })}
    </Slider>
  )
}
