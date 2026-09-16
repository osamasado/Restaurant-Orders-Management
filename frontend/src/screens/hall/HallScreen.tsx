import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './HallScreen.css'

const PANELS = [
  { key: 'preparing', label: 'In preparation' },
  { key: 'ready', label: 'Ready' },
] as const

function HallScreenContent() {
  return (
    <div className="hall-screen">
      <header className="hall-screen__header">
        <h1 className="hall-screen__title">Restaurant Orders Management</h1>
        <div className="hall-screen__header-right">
          <span className="hall-screen__clock">—:—</span>
          <ThemeToggle />
        </div>
      </header>

      <div className="hall-screen__board">
        {PANELS.map((panel) => (
          <section key={panel.key} className="hall-screen__panel">
            <header className="hall-screen__panel-header">
              <span className="hall-screen__dot" />
              <span>{panel.label}</span>
            </header>
            <div className="hall-screen__numbers" />
          </section>
        ))}
      </div>

      <footer className="hall-screen__footer">
        <span>Order numbers update automatically</span>
      </footer>
    </div>
  )
}

export function HallScreen() {
  return (
    <ThemeProvider defaultTheme="dark">
      <HallScreenContent />
    </ThemeProvider>
  )
}
