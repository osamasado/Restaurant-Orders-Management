import type { Language } from './i18n'
import { useLanguage } from './language-context'
import { useT } from './useT'
import './LanguageSwitcher.css'

const LANGUAGES: Language[] = ['de', 'en', 'ar']

/** Three text pills, no flag icons or a native <select> - matches ThemeToggle's visual language. */
export function LanguageSwitcher() {
  const { t } = useT()
  const { language, setLanguage } = useLanguage()

  return (
    <div className="language-switcher">
      {LANGUAGES.map((code) => (
        <button
          key={code}
          type="button"
          className={
            code === language
              ? 'language-switcher__option language-switcher__option--active'
              : 'language-switcher__option'
          }
          aria-pressed={code === language}
          aria-label={t('common.language.switchTo', { language: t(`common.language.names.${code}`) })}
          onClick={() => setLanguage(code)}
        >
          {code}
        </button>
      ))}
    </div>
  )
}
