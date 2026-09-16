import { Link } from 'react-router'
import './HomeScreen.css'

/** Dev-only landing page for this ticket's QA - not a production route. */
export function HomeScreen() {
  return (
    <div className="home-screen">
      <h1>Restaurant Orders Management</h1>
      <nav className="home-screen__links">
        <Link to="/guest">Guest ordering</Link>
        <Link to="/kitchen">Kitchen display</Link>
        <Link to="/hall">Hall status board</Link>
        <Link to="/admin">Management backend</Link>
      </nav>
    </div>
  )
}
