import { useLanguage } from '../../i18n/language-context'
import { LanguageProvider } from '../../i18n/LanguageProvider'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useT } from '../../i18n/useT'
import { LOCALE_BY_LANGUAGE } from '../../lib/formatMoney'
import { formatOrderNumber } from '../../lib/formatOrderNumber'
import { useNow } from '../../lib/useNow'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import type { HallBoardEntry } from '../../api/types'
import { useFitScale } from './useFitScale'
import { useHallBoard } from './useHallBoard'
import './HallScreen.css'

const PANELS = [
  { key: 'preparing', labelKey: 'hall.panels.preparing' },
  { key: 'ready', labelKey: 'hall.panels.ready' },
] as const

type HallPanelProps = {
  panelKey: (typeof PANELS)[number]['key']
  label: string
  entries: HallBoardEntry[]
}

/**
 * One column of the board. Its numbers flow top to bottom and then into further columns, shrinking in steps
 * when there are too many for the screen (see useFitScale), so nothing runs off the bottom of a wall display.
 */
function HallPanel({ panelKey, label, entries }: HallPanelProps) {
  const { t } = useT()
  const { language } = useLanguage()
  const listRef = useFitScale(entries.length, language)

  return (
    <section className={`hall-screen__panel hall-screen__panel--${panelKey}`}>
      <header className="hall-screen__panel-header">
        <span className="hall-screen__dot" aria-hidden="true" />
        <span>{label}</span>
      </header>
      <ul ref={listRef} className="hall-screen__numbers">
        {entries.map((entry) => (
          <li key={entry.orderNumber} className="hall-screen__entry">
            <span className="hall-screen__number" dir="ltr">
              {formatOrderNumber(entry.orderNumber)}
            </span>
            <span className="hall-screen__table">{t('hall.table', { number: entry.tableNumber })}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

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
          <HallPanel key={panel.key} panelKey={panel.key} label={t(panel.labelKey)} entries={board?.[panel.key] ?? []} />
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
