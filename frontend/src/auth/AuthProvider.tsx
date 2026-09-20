import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError } from '../api/http'
import * as staffApi from '../api/staffApi'
import type { StaffResponse } from '../api/types'
import { AuthContext } from './auth-context'

type AuthProviderProps = {
  children: ReactNode
}

/**
 * Checks for an existing session on mount via GET /api/staff/me - a 401
 * there just means "not signed in yet", not an error to surface.
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [staff, setStaff] = useState<StaffResponse | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    staffApi
      .me()
      .then((response) => {
        if (!cancelled) setStaff(response)
      })
      .catch((error) => {
        if (!cancelled && !(error instanceof ApiError && error.status === 401)) {
          console.error('Failed to check for an existing session', error)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const login = async (name: string, pin: string) => {
    const response = await staffApi.login(name, pin)
    setStaff(response)
  }

  const logout = async () => {
    try {
      await staffApi.logout()
    } finally {
      setStaff(null)
    }
  }

  const value = useMemo(() => ({ staff, loading, login, logout }), [staff, loading])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
