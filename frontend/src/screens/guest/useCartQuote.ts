import { useEffect, useState } from 'react'
import { quoteCart } from '../../api/guestApi'
import type { CartQuoteResponse } from '../../api/types'
import type { CartLineItem } from './cartTypes'

const DEBOUNCE_MS = 250

type QuoteResult = {
  pricingKey: string
  quote: CartQuoteResponse | null
  error: boolean
}

type CartQuoteState = {
  /** Last successful quote - may be for a slightly older cart while `loading`. */
  quote: CartQuoteResponse | null
  loading: boolean
  error: boolean
}

/**
 * Server-computed subtotal/tax/total for the cart. Debounced so tapping a
 * quantity stepper several times sends one request, and keyed on
 * sizeId+quantity only, so nothing that leaves the price unchanged refetches.
 * `loading` is derived (the stored result is for a different key) rather than
 * set inside the effect, and stale responses are dropped via `cancelled`.
 */
export function useCartQuote(items: CartLineItem[]): CartQuoteState {
  const [result, setResult] = useState<QuoteResult>({ pricingKey: '', quote: null, error: false })
  const pricingKey = items.map((item) => `${item.sizeId}x${item.quantity}`).join(',')

  useEffect(() => {
    if (pricingKey === '') return

    let cancelled = false
    const request = {
      items: pricingKey.split(',').map((entry) => {
        const [sizeId, quantity] = entry.split('x').map(Number)
        return { sizeId, quantity }
      }),
    }
    const timer = window.setTimeout(() => {
      quoteCart(request)
        .then((quote) => {
          if (!cancelled) setResult({ pricingKey, quote, error: false })
        })
        .catch(() => {
          if (!cancelled) setResult((previous) => ({ pricingKey, quote: previous.quote, error: true }))
        })
    }, DEBOUNCE_MS)

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [pricingKey])

  if (pricingKey === '') return { quote: null, loading: false, error: false }
  const current = result.pricingKey === pricingKey
  return { quote: result.quote, loading: !current, error: current && result.error }
}
