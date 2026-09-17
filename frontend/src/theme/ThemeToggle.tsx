import { useT } from '../i18n/useT'
import { useTheme } from './theme-context'
import './ThemeToggle.css'

/** A text control, not an icon - the design deliberately has no icon set. */
export function ThemeToggle() {
  const { t } = useT()
  const { theme, toggleTheme } = useTheme()
  const nextTheme = theme === 'light' ? 'dark' : 'light'

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={toggleTheme}
      aria-label={nextTheme === 'dark' ? t('common.theme.switchToDark') : t('common.theme.switchToLight')}
    >
      {nextTheme === 'dark' ? t('common.theme.dark') : t('common.theme.light')}
    </button>
  )
}
