import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { LanguageSwitcher } from '../../i18n/LanguageSwitcher'
import { useT } from '../../i18n/useT'
import { useMediaQuery } from '../../lib/useMediaQuery'
import { ThemeToggle } from '../../theme/ThemeToggle'
import { Icon } from '../Icon'
import { BrandMark } from './BrandMark'
import type { IconName } from '../Icon'
import './AdminSidebar.css'

const NAV_ITEMS: { to: string; labelKey: string; icon: IconName }[] = [
  { to: 'dashboard', labelKey: 'admin.nav.dashboard', icon: 'dashboard' },
  { to: 'orders', labelKey: 'admin.nav.orders', icon: 'orders' },
  { to: 'history', labelKey: 'admin.nav.history', icon: 'history' },
  { to: 'meals', labelKey: 'admin.nav.meals', icon: 'meals' },
  { to: 'materials', labelKey: 'admin.nav.materials', icon: 'materials' },
  { to: 'tables', labelKey: 'admin.nav.tables', icon: 'tables' },
  { to: 'staff', labelKey: 'admin.nav.staff', icon: 'staff' },
]

type AdminSidebarProps = {
  /** "Name · Role: Admin", shown as the tooltip of Sign out. */
  signedInAs: string
  onLogout: () => void
}

/**
 * The admin navigation. On a wide screen it is a narrow rail down the left edge: the logo, then the pages and sign
 * out as small icons on a large neutral capsule that reaches in from the edge, each with a small label beside it,
 * and two round controls (settings, theme) at the bottom. The current page's icon and label are coral and a dot on
 * the capsule's edge slides to it when you navigate; everything else stays quiet. Below 900 px the rail becomes a drawer opened from a bar at the top: while it is open the rest of the page
 * is inert (so Tab stays in the drawer), Escape or a tap outside closes it, and focus goes back to the menu button.
 */
export function AdminSidebar({ signedInAs, onLogout }: AdminSidebarProps) {
  const { t } = useT()
  const location = useLocation()
  const compact = useMediaQuery('(max-width: 900px)')
  const [open, setOpen] = useState(false)
  const menuButton = useRef<HTMLButtonElement>(null)
  const drawer = useRef<HTMLElement>(null)
  const nav = useRef<HTMLElement>(null)
  const [dotTop, setDotTop] = useState<number | null>(null)
  const drawerOpen = compact && open
  // Where focus goes once the drawer has closed: back to its button after Escape or the close button, to the page
  // itself after a link was followed.
  const returnFocusTo = useRef<'menu' | 'main'>('menu')
  const wasOpen = useRef(false)

  const closeDrawer = (returnTo: 'menu' | 'main') => {
    returnFocusTo.current = returnTo
    setOpen(false)
  }

  // The marker on the capsule's edge follows the current page (NavLink marks it with aria-current).
  const measure = useCallback(() => {
    const active = nav.current?.querySelector<HTMLElement>('[aria-current="page"]')
    setDotTop(active ? active.offsetTop + active.offsetHeight / 2 : null)
  }, [])

  useLayoutEffect(() => {
    measure()
  }, [location.pathname, compact, measure])

  // Labels wrap differently per language and once the font has loaded, which moves the items.
  useEffect(() => {
    const element = nav.current
    if (!element) return
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    void document.fonts?.ready.then(measure)
    return () => observer.disconnect()
  }, [measure])

  useEffect(() => {
    if (!drawerOpen) return
    drawer.current?.querySelector<HTMLElement>('a, button')?.focus()
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeDrawer('menu')
    }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [drawerOpen])

  // The page behind an open drawer must not be reachable by Tab or a screen reader.
  useEffect(() => {
    const main = document.getElementById('admin-main')
    const bar = document.getElementById('admin-topbar')
    for (const element of [main, bar]) {
      if (element) element.inert = drawerOpen
    }
    return () => {
      for (const element of [main, bar]) {
        if (element) element.inert = false
      }
    }
  }, [drawerOpen])

  // After the page behind is reachable again (the effect above runs first), put focus back.
  useEffect(() => {
    if (wasOpen.current && !drawerOpen) {
      const target = returnFocusTo.current === 'menu' ? menuButton.current : document.getElementById('admin-main')
      target?.focus()
    }
    wasOpen.current = drawerOpen
  }, [drawerOpen])

  return (
    <>
      <div className="admin-topbar" id="admin-topbar">
        <button
          ref={menuButton}
          type="button"
          className="admin-topbar__menu button button--icon"
          aria-label={t('admin.nav.openMenu')}
          aria-expanded={drawerOpen}
          aria-controls="admin-sidebar"
          onClick={() => setOpen(true)}
        >
          <Icon name="menu" />
        </button>
        <span className="admin-topbar__brand">
          <BrandMark size={32} />
          {t('admin.eyebrow')}
        </span>
      </div>

      {drawerOpen && <div className="admin-sidebar__scrim" onClick={() => closeDrawer('menu')} aria-hidden="true" />}

      <aside
        id="admin-sidebar"
        ref={drawer}
        className={`admin-sidebar${drawerOpen ? ' admin-sidebar--open' : ''}`}
        aria-label={t('admin.nav.label')}
        inert={compact && !open}
      >
        <div className="admin-sidebar__top">
          <BrandMark />
          <span className="admin-sidebar__eyebrow">{t('admin.eyebrow')}</span>
          {compact && (
            <button type="button" className="admin-sidebar__close button button--icon" aria-label={t('common.close')} onClick={() => closeDrawer('menu')}>
              <Icon name="close" />
            </button>
          )}
        </div>

        <div className="admin-sidebar__menu">
          <nav ref={nav} className="admin-sidebar__nav" aria-label={t('admin.nav.label')}>
            {dotTop !== null && (
              <span className="admin-sidebar__dot" aria-hidden="true" style={{ transform: `translateY(${dotTop}px)` }} />
            )}
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={() => closeDrawer('main')}
                className={({ isActive }) =>
                  isActive ? 'admin-sidebar__link admin-sidebar__link--active' : 'admin-sidebar__link'
                }
              >
                <Icon name={item.icon} size={18} />
                <span className="admin-sidebar__label">{t(item.labelKey)}</span>
              </NavLink>
            ))}
          </nav>
          <button type="button" className="admin-sidebar__logout admin-sidebar__link" onClick={onLogout} title={signedInAs}>
            <Icon name="logout" size={18} />
            <span className="admin-sidebar__label">{t('admin.auth.logout')}</span>
          </button>
        </div>

        <div className="admin-sidebar__bottom">
          <LanguageSwitcher />
          <div className="admin-sidebar__round-controls">
            <NavLink
              to="settings"
              onClick={() => closeDrawer('main')}
              className="admin-sidebar__circle button button--icon button--round"
              aria-label={t('admin.nav.settings')}
              title={t('admin.nav.settings')}
            >
              <Icon name="gear" size={16} />
            </NavLink>
            <ThemeToggle iconOnly />
          </div>
        </div>
      </aside>
    </>
  )
}
