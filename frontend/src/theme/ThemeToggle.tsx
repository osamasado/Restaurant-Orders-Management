import { Icon } from '../components/Icon'
import { useT } from '../i18n/useT'
import { useTheme } from './theme-context'
import './ThemeToggle.css'

/**
 * Switches between the light and dark palette. Text pill by default (the boards and the guest screen);
 * iconOnly shows just the sun or moon for the narrow admin sidebar, with the same accessible name.
 */
export function ThemeToggle({ iconOnly = false }: { iconOnly?: boolean }) {
  const { t } = useT()
  const { theme, toggleTheme } = useTheme()
  const nextTheme = theme === 'light' ? 'dark' : 'light'
  const label = nextTheme === 'dark' ? t('common.theme.switchToDark') : t('common.theme.switchToLight')

  return (
    <button
      type="button"
      className={iconOnly ? 'theme-toggle theme-toggle--icon button button--icon button--round' : 'theme-toggle button button--small'}
      onClick={toggleTheme}
      aria-label={label}
      title={iconOnly ? label : undefined}
    >
      {iconOnly ? <Icon name={nextTheme === 'dark' ? 'moon' : 'sun'} size={18} /> : nextTheme === 'dark' ? t('common.theme.dark') : t('common.theme.light')}
    </button>
  )
}
