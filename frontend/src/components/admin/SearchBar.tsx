import { Icon } from '../Icon'
import './SearchBar.css'

type SearchBarProps = {
  value: string
  onChange: (value: string) => void
  /** The accessible name; the placeholder alone is only a hint. */
  label: string
  placeholder: string
}

/** A pill-shaped search field. It filters what the screen already shows, so there is no submit. */
export function SearchBar({ value, onChange, label, placeholder }: SearchBarProps) {
  return (
    <label className="search-bar">
      <span className="visually-hidden">{label}</span>
      <Icon name="search" size={18} />
      <input
        type="search"
        className="search-bar__input"
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        autoComplete="off"
      />
    </label>
  )
}
