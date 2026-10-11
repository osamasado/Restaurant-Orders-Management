import { useState, type FormEvent } from 'react'
import { claimDevice } from '../../api/guestApi'
import { ApiError } from '../../api/http'
import type { GuestTableResponse } from '../../api/types'
import { useT } from '../../i18n/useT'
import './PairingScreen.css'

type PairingScreenProps = {
  onPaired: (code: string, table: GuestTableResponse) => void
  /** Back to the choice between eating in and taking away. */
  onBack: () => void
}

/**
 * Shown when a guest chooses to eat in and this device is not paired with a table yet (a paired tablet skips it).
 * The language is already chosen, so it reads in the guest's language.
 */
export function PairingScreen({ onPaired, onBack }: PairingScreenProps) {
  const { t } = useT()
  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const normalized = code.trim().toUpperCase()
    if (!normalized) return
    setSubmitting(true)
    setError(null)
    claimDevice(normalized)
      .then((table) => onPaired(normalized, table))
      .catch((err: unknown) => {
        setError(err instanceof ApiError && err.status === 404 ? t('guest.pairing.unknownCode') : t('guest.pairing.error'))
        setSubmitting(false)
      })
  }

  return (
    <div className="guest-screen pairing-screen">
      <form className="pairing-screen__form" onSubmit={handleSubmit}>
        <h1 className="pairing-screen__title">{t('guest.pairing.title')}</h1>
        <p className="pairing-screen__hint">{t('guest.pairing.hint')}</p>
        <input
          className="pairing-screen__input"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          maxLength={6}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          aria-label={t('guest.pairing.codeLabel')}
          dir="ltr"
        />
        {error && <p className="pairing-screen__error">{error}</p>}
        <button type="submit" className="pairing-screen__submit button button--primary button--large" disabled={submitting || !code.trim()}>
          {submitting ? t('guest.pairing.connecting') : t('guest.pairing.connect')}
        </button>
        <button type="button" className="pairing-screen__back button button--block" onClick={onBack} disabled={submitting}>
          {t('guest.pairing.back')}
        </button>
      </form>
    </div>
  )
}
