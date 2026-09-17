import { useTheme } from './theme-context'
import './ThemeToggle.css'

/** A text control, not an icon - the design deliberately has no icon set. */
export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const nextTheme = theme === 'light' ? 'dark' : 'light'

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={toggleTheme}
      aria-label={`Switch to ${nextTheme} mode`}
    >
      {nextTheme}
    </button>
  )
}
