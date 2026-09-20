import { createContext, useContext } from 'react'
import type { StaffResponse } from '../api/types'

export type AuthContextValue = {
  staff: StaffResponse | null
  loading: boolean
  login: (name: string, pin: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
