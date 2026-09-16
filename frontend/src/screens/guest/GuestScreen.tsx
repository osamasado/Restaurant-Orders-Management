import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './GuestScreen.css'

function GuestScreenContent() {
  return (
    <div className="guest-screen">
      <div className="guest-screen__device">
        <div className="guest-screen__inner">
          <header className="guest-screen__header">
            <span className="guest-screen__eyebrow">Table — · —</span>
            <ThemeToggle />
          </header>
          <main className="guest-screen__content">
            <p>Guest ordering — coming soon</p>
          </main>
          <div className="guest-screen__action-bar">
            <span>Continue</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export function GuestScreen() {
  return (
    <ThemeProvider defaultTheme="light">
      <GuestScreenContent />
    </ThemeProvider>
  )
}
