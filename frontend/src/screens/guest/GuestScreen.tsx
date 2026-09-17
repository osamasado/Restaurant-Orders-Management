import { useTranslation } from 'react-i18next'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './GuestScreen.css'

function GuestScreenContent() {
  const { t } = useTranslation()

  return (
    <div className="guest-screen">
      <header className="guest-screen__header">
        <span className="guest-screen__eyebrow">{t('guest.tableLabel')}</span>
        <div className="guest-screen__header-controls">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>
      <main className="guest-screen__content">
        <p>{t('guest.orderingComingSoon')}</p>
      </main>
      <div className="guest-screen__action-bar">
        <span>{t('guest.continue')}</span>
      </div>
    </div>
  )
}

export function GuestScreen() {
  return (
    <ThemeProvider defaultTheme="light">
      <LanguageProvider defaultLanguage="de">
        <GuestScreenContent />
      </LanguageProvider>
    </ThemeProvider>
  )
}
