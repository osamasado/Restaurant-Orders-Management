import './WelcomeScreen.css'

/** The slowly drifting light behind the guest's first screens (language, eat in or take away). Pure CSS, decorative. */
export function WelcomeBackdrop() {
  return (
    <div className="welcome-screen__ambient" aria-hidden="true">
      <span className="welcome-screen__orb welcome-screen__orb--coral" />
      <span className="welcome-screen__orb welcome-screen__orb--amber" />
      <span className="welcome-screen__orb welcome-screen__orb--terracotta" />
      <span className="welcome-screen__orb welcome-screen__orb--blush" />
    </div>
  )
}
