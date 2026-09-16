import { NavLink, Outlet } from 'react-router'
import { ThemeProvider } from '../../theme/ThemeProvider'
import { ThemeToggle } from '../../theme/ThemeToggle'
import './AdminScreen.css'

const NAV_ITEMS = [
  { to: 'orders', label: 'Orders' },
  { to: 'history', label: 'Audit history' },
  { to: 'meals', label: 'Meals' },
  { to: 'materials', label: 'Raw materials' },
  { to: 'tables', label: 'Tables & devices' },
  { to: 'staff', label: 'Staff accounts' },
  { to: 'settings', label: 'Settings' },
] as const

function AdminScreenContent() {
  return (
    <div className="admin-screen">
      <aside className="admin-screen__sidebar">
        <span className="admin-screen__eyebrow">Management</span>
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
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="admin-screen__spacer" />
        <div className="admin-screen__signed-in">
          <ThemeToggle />
          <span>Signed in as —</span>
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
      <AdminScreenContent />
    </ThemeProvider>
  )
}
