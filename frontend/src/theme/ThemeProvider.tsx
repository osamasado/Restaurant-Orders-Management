import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ThemeContext } from './theme-context'
import type { Theme } from './theme-context'

const STORAGE_KEY = 'rom-theme'

function readStoredTheme(): Theme | null {
  const value = window.localStorage.getItem(STORAGE_KEY)
  return value === 'light' || value === 'dark' ? value : null
}

type ThemeProviderProps = {
  /** Used only when the shared preference hasn't been set yet. */
  defaultTheme: Theme
  children: ReactNode
}

/**
 * One shared preference (a single localStorage key) applied per screen.
 * Each screen mounts its own provider with its own default so a first-time
 * visitor sees the design's intended look (kitchen/hall dark, guest/admin
 * light), but every provider reads/writes the same key, so a choice made on
 * one screen is honored on every other screen too.
 */
export function ThemeProvider({ defaultTheme, children }: ThemeProviderProps) {
  const [theme, setTheme] = useState<Theme>(() => readStoredTheme() ?? defaultTheme)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
  }, [theme])

  const toggleTheme = () => {
    setTheme((current) => {
      const next: Theme = current === 'light' ? 'dark' : 'light'
      window.localStorage.setItem(STORAGE_KEY, next)
      return next
    })
  }

  const value = useMemo(() => ({ theme, toggleTheme }), [theme])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
