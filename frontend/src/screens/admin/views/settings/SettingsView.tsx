import { useEffect, useState } from 'react'
import { getSettings, updateSettings } from '../../../../api/settingsApi'
import type { ConfigRequest, Language, PaymentMethod, SymbolPosition } from '../../../../api/types'
import { useT } from '../../../../i18n/useT'
import './SettingsView.css'

const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'CARD', 'PAYPAL', 'CASH_DESK']
const LANGUAGES: Language[] = ['DE', 'EN', 'AR']

function toggleClassName(active: boolean): string {
  return active ? 'settings-card__toggle-button settings-card__toggle-button--active' : 'settings-card__toggle-button'
}

export function SettingsView() {
  const { t } = useT()

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const [currencyCode, setCurrencyCode] = useState('')
  const [currencySymbol, setCurrencySymbol] = useState('')
  const [symbolPosition, setSymbolPosition] = useState<SymbolPosition>('SUFFIX')
  const [taxRate, setTaxRate] = useState('')
  const [defaultLanguage, setDefaultLanguage] = useState<Language>('DE')
  const [enabledPaymentMethods, setEnabledPaymentMethods] = useState<Set<PaymentMethod>>(new Set())

  const load = async () => {
    try {
      const config = await getSettings()
      setCurrencyCode(config.currencyCode)
      setCurrencySymbol(config.currencySymbol)
      setSymbolPosition(config.symbolPosition)
      setTaxRate(String(config.taxRate))
      setDefaultLanguage(config.defaultLanguage)
      setEnabledPaymentMethods(new Set(config.enabledPaymentMethods))
      setLoadError(null)
    } catch {
      setLoadError(t('admin.settings.loadError'))
    } finally {
      setLoading(false)
    }
  }

  // Fetch-on-mount: see MealsView's identical effect for why the hooks
  // lint rules are suppressed here (no data library in this project yet).
  // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
  useEffect(() => {
    void load()
  }, [])

  const togglePaymentMethod = (method: PaymentMethod) => {
    setEnabledPaymentMethods((prev) => {
      const next = new Set(prev)
      if (next.has(method)) {
        next.delete(method)
      } else {
        next.add(method)
      }
      return next
    })
  }

  const handleSave = async () => {
    setSaving(true)
    setSaveError(null)
    setSaved(false)
    try {
      const request: ConfigRequest = {
        currencyCode: currencyCode.trim(),
        currencySymbol: currencySymbol.trim(),
        symbolPosition,
        taxRate: Number(taxRate),
        defaultLanguage,
        enabledPaymentMethods: Array.from(enabledPaymentMethods),
      }
      await updateSettings(request)
      setSaved(true)
    } catch {
      setSaveError(t('admin.settings.error'))
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return <div className="settings-view" />
  }

  const prefixPreview = `${currencySymbol} 9,80`
  const suffixPreview = `9,80 ${currencySymbol}`

  return (
    <div className="settings-view">
      <div>
        <h2 className="settings-view__title">{t('admin.nav.settings')}</h2>
        <p className="settings-view__subtitle">{t('admin.settings.subtitle')}</p>
      </div>

      {loadError && <p className="settings-view__error">{loadError}</p>}

      <div className="settings-view__cards">
        <div className="settings-card">
          <span className="settings-card__title">{t('admin.settings.currencyAndTax')}</span>

          <label className="settings-card__field">
            <span>{t('admin.settings.currencyCode')}</span>
            <input value={currencyCode} onChange={(event) => setCurrencyCode(event.target.value)} required />
          </label>

          <label className="settings-card__field">
            <span>{t('admin.settings.currencySymbol')}</span>
            <input value={currencySymbol} onChange={(event) => setCurrencySymbol(event.target.value)} required />
          </label>

          <div className="settings-card__field">
            <span>{t('admin.settings.symbolPosition')}</span>
            <div className="settings-card__toggle-group">
              <button type="button" className={toggleClassName(symbolPosition === 'PREFIX')} onClick={() => setSymbolPosition('PREFIX')}>
                {prefixPreview}
              </button>
              <button type="button" className={toggleClassName(symbolPosition === 'SUFFIX')} onClick={() => setSymbolPosition('SUFFIX')}>
                {suffixPreview}
              </button>
            </div>
          </div>

          <label className="settings-card__field">
            <span>{t('admin.settings.taxRate')}</span>
            <input
              type="number"
              step="0.01"
              min="0"
              value={taxRate}
              onChange={(event) => setTaxRate(event.target.value)}
              required
            />
          </label>

          <label className="settings-card__field">
            <span>{t('admin.settings.defaultLanguage')}</span>
            <select value={defaultLanguage} onChange={(event) => setDefaultLanguage(event.target.value as Language)}>
              {LANGUAGES.map((language) => (
                <option key={language} value={language}>
                  {t(`admin.settings.languages.${language}`)}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="settings-card">
          <span className="settings-card__title">{t('admin.settings.paymentMethods')}</span>
          {PAYMENT_METHODS.map((method) => {
            const enabled = enabledPaymentMethods.has(method)
            return (
              <div className="payment-method-row" key={method}>
                <span className="payment-method-row__name">{t(`admin.settings.paymentMethodNames.${method}`)}</span>
                <button
                  type="button"
                  className={
                    enabled
                      ? 'payment-method-row__toggle payment-method-row__toggle--on'
                      : 'payment-method-row__toggle payment-method-row__toggle--off'
                  }
                  onClick={() => togglePaymentMethod(method)}
                >
                  {enabled ? t('admin.settings.on') : t('admin.settings.off')}
                </button>
              </div>
            )
          })}
          <p className="settings-card__caption">{t('admin.settings.paymentMethodsCaption')}</p>
        </div>
      </div>

      {saveError && <p className="settings-view__error">{saveError}</p>}

      <div className="settings-view__actions">
        <button className="settings-view__save" onClick={() => void handleSave()} disabled={saving}>
          {t('admin.settings.save')}
        </button>
        {saved && <span className="settings-view__success">{t('admin.settings.saved')}</span>}
      </div>
    </div>
  )
}
