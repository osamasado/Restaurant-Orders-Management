import { useTranslation } from 'react-i18next'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './HallScreen.css'

const PANELS = [
  { key: 'preparing', labelKey: 'hall.panels.preparing' },
  { key: 'ready', labelKey: 'hall.panels.ready' },
] as const

function HallScreenContent() {
  const { t } = useTranslation()

  return (
    <div className="hall-screen">
      <header className="hall-screen__header">
        <h1 className="hall-screen__title">{t('hall.title')}</h1>
        <div className="hall-screen__header-right">
          <span className="hall-screen__clock">--:--</span>
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>

      <div className="hall-screen__board">
        {PANELS.map((panel) => (
          <section key={panel.key} className="hall-screen__panel">
            <header className="hall-screen__panel-header">
              <span className="hall-screen__dot" />
              <span>{t(panel.labelKey)}</span>
            </header>
            <div className="hall-screen__numbers" />
          </section>
        ))}
      </div>

      <footer className="hall-screen__footer">
        <span>{t('hall.footer')}</span>
      </footer>
    </div>
  )
}

export function HallScreen() {
  return (
    <ThemeProvider defaultTheme="dark">
      <LanguageProvider defaultLanguage="de">
        <HallScreenContent />
      </LanguageProvider>
    </ThemeProvider>
  )
}
