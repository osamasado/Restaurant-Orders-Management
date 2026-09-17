import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import i18n, { RTL_LANGUAGES } from './i18n'
import type { Language } from './i18n'
import { LanguageContext } from './language-context'

const STORAGE_KEY = 'rom-language'

function readStoredLanguage(): Language | null {
  const value = window.localStorage.getItem(STORAGE_KEY)
  return value === 'en' || value === 'de' || value === 'ar' ? value : null
}

type LanguageProviderProps = {
  /** Used only when the shared preference hasn't been set yet. */
  defaultLanguage: Language
  children: ReactNode
}

/**
 * One shared preference (a single localStorage key) across all four
 * screens, same model as ThemeProvider - each screen mounts its own
 * provider, all reading/writing the same key, so a choice made on one
 * screen carries over to the others.
 */
export function LanguageProvider({ defaultLanguage, children }: LanguageProviderProps) {
  const [language, setLanguageState] = useState<Language>(() => readStoredLanguage() ?? defaultLanguage)

  useEffect(() => {
    void i18n.changeLanguage(language)
    document.documentElement.lang = language
    document.documentElement.dir = RTL_LANGUAGES.has(language) ? 'rtl' : 'ltr'
  }, [language])

  const setLanguage = (next: Language) => {
    window.localStorage.setItem(STORAGE_KEY, next)
    setLanguageState(next)
  }

  const value = useMemo(() => ({ language, setLanguage }), [language])

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
}
