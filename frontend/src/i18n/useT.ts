import { useTranslation } from 'react-i18next'

type TranslateOptions = Record<string, unknown>

/**
 * Wraps react-i18next's t() with an explicit per-language missing-translation
 * check. i18next's own missingKeyHandler/saveMissing only fires when a key is
 * missing from EVERY language in the fallback chain - if e.g. Arabic is
 * missing a key that English (the fallback) has, i18next resolves it via the
 * fallback with no warning at all (confirmed by reading i18next's own
 * translate() source: the missing-key branch is only reached when the
 * resolved value across the *entire* chain is invalid, not just for the
 * active language). This checks the active language specifically, so a
 * translator forgetting one language for an otherwise-translated string is
 * actually caught.
 */
export function useT() {
  const { t: originalT, i18n } = useTranslation()

  const t = (key: string, options?: TranslateOptions): string => {
    if (!i18n.exists(key, { lngs: [i18n.language] })) {
      console.warn(`[i18n] Missing translation for key "${key}" in language "${i18n.language}" - using a fallback.`)
    }
    return originalT(key, options)
  }

  return { t, i18n }
}
