import { createContext, useContext } from 'react'

export type ToastKind = 'success' | 'error'

export type ToastApi = {
  /** A short message that goes away by itself: about 4 s for a success, 8 s for an error. */
  show: (kind: ToastKind, message: string) => void
}

export const ToastContext = createContext<ToastApi>({ show: () => undefined })

export function useToast(): ToastApi {
  return useContext(ToastContext)
}
