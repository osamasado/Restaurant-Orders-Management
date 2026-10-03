import { useLanguage } from '../../i18n/language-context'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useT } from '../../i18n/useT'
import { LOCALE_BY_LANGUAGE } from '../../lib/formatMoney'
import { useNow } from '../../lib/useNow'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { useHallBoard } from './useHallBoard'
import './HallScreen.css'

const PANELS = [
  { key: 'preparing', labelKey: 'hall.panels.preparing' },
  { key: 'ready', labelKey: 'hall.panels.ready' },
] as const

function HallScreenContent() {
  const { t } = useT()
  const { board, connectionLost } = useHallBoard()
  const now = useNow()
  const { language } = useLanguage()

  const clockFormat = new Intl.DateTimeFormat(LOCALE_BY_LANGUAGE[language], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })

  return (
    <div className="hall-screen">
      <header className="hall-screen__header">
        <h1 className="hall-screen__title">{t('hall.title')}</h1>
        <div className="hall-screen__header-right">
          <time className="hall-screen__clock" dateTime={new Date(now).toISOString()}>
            {clockFormat.format(now)}
          </time>
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>

      {connectionLost && <p className="hall-screen__notice">{t('hall.connectionLost')}</p>}

      <div className="hall-screen__board">
        {PANELS.map((panel) => (
          <section key={panel.key} className="hall-screen__panel">
            <header className="hall-screen__panel-header">
              <span className="hall-screen__dot" />
              <span>{t(panel.labelKey)}</span>
            </header>
            <div className="hall-screen__numbers">
              {board?.[panel.key].map((orderNumber) => (
                <span key={orderNumber} className={`hall-screen__number hall-screen__number--${panel.key}`}>
                  {orderNumber}
                </span>
              ))}
            </div>
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
