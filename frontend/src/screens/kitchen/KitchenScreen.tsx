import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './KitchenScreen.css'

const COLUMNS = [
  { key: 'new', label: 'New' },
  { key: 'preparing', label: 'In preparation' },
  { key: 'ready', label: 'Ready' },
] as const

function KitchenScreenContent() {
  return (
    <div className="kitchen-screen">
      <header className="kitchen-screen__header">
        <div>
          <h1 className="kitchen-screen__title">Kitchen</h1>
          <p className="kitchen-screen__eyebrow">Station 1</p>
        </div>
        <div className="kitchen-screen__header-right">
          <span className="kitchen-screen__clock">—:—</span>
          <ThemeToggle />
        </div>
      </header>

      <div className="kitchen-screen__board">
        {COLUMNS.map((column) => (
          <section key={column.key} className="kitchen-screen__column">
            <header className="kitchen-screen__column-header">
              <span className="kitchen-screen__dot" />
              <span>{column.label}</span>
              <span className="kitchen-screen__count">0</span>
            </header>
            <div className="kitchen-screen__column-list" />
          </section>
        ))}
      </div>

      <footer className="kitchen-screen__footer">
        <span>Ran out?</span>
      </footer>
    </div>
  )
}

export function KitchenScreen() {
  return (
    <ThemeProvider defaultTheme="dark">
      <KitchenScreenContent />
    </ThemeProvider>
  )
}
