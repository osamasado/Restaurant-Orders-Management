import { useState } from 'react'
import type { FormEvent } from 'react'
import { useT } from '../i18n/useT'
import { useAuth } from './auth-context'
import './LoginForm.css'

export function LoginForm() {
  const { login } = useAuth()
  const { t } = useT()
  const [name, setName] = useState('')
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(name, pin)
    } catch {
      setError(t('admin.auth.error'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-form">
      <form className="login-form__card" onSubmit={handleSubmit}>
        <h2 className="login-form__title">{t('admin.auth.title')}</h2>
        <label className="login-form__field">
          <span>{t('admin.auth.name')}</span>
          <input value={name} onChange={(event) => setName(event.target.value)} autoComplete="username" required />
        </label>
        <label className="login-form__field">
          <span>{t('admin.auth.pin')}</span>
          <input
            type="password"
            inputMode="numeric"
            value={pin}
            onChange={(event) => setPin(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {error && <p className="login-form__error">{error}</p>}
        <button type="submit" className="login-form__submit" disabled={submitting}>
          {t('admin.auth.submit')}
        </button>
      </form>
    </div>
  )
}
