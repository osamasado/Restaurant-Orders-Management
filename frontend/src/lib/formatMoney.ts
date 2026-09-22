import type { Language } from '../i18n/i18n'
import type { SymbolPosition } from '../api/types'

/**
 * Number formatting follows the guest's chosen language (comma decimals for
 * German, period for English, etc.) via the browser's Intl.NumberFormat,
 * rather than a hardcoded separator - the design doc calls this out
 * explicitly ("Numbers and currency format follow the language").
 */
const LOCALE_BY_LANGUAGE: Record<Language, string> = {
  de: 'de-DE',
  en: 'en-US',
  ar: 'ar-EG',
}

export function formatMoney(
  amount: number,
  language: Language,
  currencySymbol: string,
  symbolPosition: SymbolPosition,
): string {
  const formatted = new Intl.NumberFormat(LOCALE_BY_LANGUAGE[language], {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)

  return symbolPosition === 'PREFIX' ? `${currencySymbol} ${formatted}` : `${formatted} ${currencySymbol}`
}
