import { NavLink, Outlet } from 'react-router'
import { useTranslation } from 'react-i18next'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './AdminScreen.css'

const NAV_ITEMS = [
  { to: 'orders', labelKey: 'admin.nav.orders' },
  { to: 'history', labelKey: 'admin.nav.history' },
  { to: 'meals', labelKey: 'admin.nav.meals' },
  { to: 'materials', labelKey: 'admin.nav.materials' },
  { to: 'tables', labelKey: 'admin.nav.tables' },
  { to: 'staff', labelKey: 'admin.nav.staff' },
  { to: 'settings', labelKey: 'admin.nav.settings' },
] as const

function AdminScreenContent() {
  const { t } = useTranslation()

  return (
    <div className="admin-screen">
      <aside className="admin-screen__sidebar">
        <span className="admin-screen__eyebrow">{t('admin.eyebrow')}</span>
        <nav className="admin-screen__nav">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                isActive
                  ? 'admin-screen__nav-link admin-screen__nav-link--active'
                  : 'admin-screen__nav-link'
              }
            >
              {t(item.labelKey)}
            </NavLink>
          ))}
        </nav>
        <div className="admin-screen__spacer" />
        <div className="admin-screen__signed-in">
          <div className="admin-screen__signed-in-controls">
            <LanguageSwitcher />
            <ThemeToggle />
          </div>
          <span>{t('admin.notSignedIn')}</span>
        </div>
      </aside>
      <main className="admin-screen__content">
        <Outlet />
      </main>
    </div>
  )
}

export function AdminScreen() {
  return (
    <ThemeProvider defaultTheme="light">
      <LanguageProvider defaultLanguage="de">
        <AdminScreenContent />
      </LanguageProvider>
    </ThemeProvider>
  )
}
