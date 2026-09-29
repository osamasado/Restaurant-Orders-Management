import { AuthProvider } from '../../auth/AuthProvider'
import { useAuth } from '../../auth/auth-context'
import { LoginForm } from '../../auth/LoginForm'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useT } from '../../i18n/useT'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './KitchenScreen.css'

const COLUMNS = [
  { key: 'new', labelKey: 'kitchen.columns.new' },
  { key: 'preparing', labelKey: 'kitchen.columns.preparing' },
  { key: 'ready', labelKey: 'kitchen.columns.ready' },
] as const

function KitchenScreenContent() {
  const { t } = useT()
  const { staff, loading, logout } = useAuth()

  if (loading) {
    return null
  }

  if (!staff) {
    return <LoginForm />
  }

  if (staff.role !== 'KITCHEN' && staff.role !== 'ADMIN') {
    return (
      <div className="kitchen-screen kitchen-screen--denied">
        <p>{t('kitchen.accessDenied')}</p>
        <button type="button" className="kitchen-screen__logout" onClick={() => void logout()}>
          {t('kitchen.logout')}
        </button>
      </div>
    )
  }

  return (
    <div className="kitchen-screen">
      <header className="kitchen-screen__header">
        <div>
          <h1 className="kitchen-screen__title">{t('kitchen.title')}</h1>
          <p className="kitchen-screen__eyebrow">
            {t('kitchen.station')} · {t('kitchen.onShift', { name: staff.name })}
          </p>
        </div>
        <div className="kitchen-screen__header-right">
          <span className="kitchen-screen__clock">--:--</span>
          <LanguageSwitcher />
          <ThemeToggle />
          <button type="button" className="kitchen-screen__logout" onClick={() => void logout()}>
            {t('kitchen.logout')}
          </button>
        </div>
      </header>

      <div className="kitchen-screen__board">
        {COLUMNS.map((column) => (
          <section key={column.key} className="kitchen-screen__column">
            <header className="kitchen-screen__column-header">
              <span className="kitchen-screen__dot" />
              <span>{t(column.labelKey)}</span>
              <span className="kitchen-screen__count">0</span>
            </header>
            <div className="kitchen-screen__column-list" />
          </section>
        ))}
      </div>

      <footer className="kitchen-screen__footer">
        <span>{t('kitchen.ranOut')}</span>
      </footer>
    </div>
  )
}

export function KitchenScreen() {
  return (
    <ThemeProvider defaultTheme="dark">
      <LanguageProvider defaultLanguage="de">
        <AuthProvider>
          <KitchenScreenContent />
        </AuthProvider>
      </LanguageProvider>
    </ThemeProvider>
  )
}
