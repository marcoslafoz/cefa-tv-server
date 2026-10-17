'use client'

import { createContext, use, useCallback, useEffect, useMemo, useState } from 'react'
import { useParams, usePathname, useRouter } from 'next/navigation'
import { useDictionary } from '@/contexts/dictionary-context'
import { API_V1, apiClient } from '@/lib/api-client'
import { authEvents } from '@/lib/auth-events'
import { toast } from '@/lib/toast'
import type { AuthUser } from '@/types/api'

interface AuthContextValue {
  user: AuthUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const params = useParams()
  const pathname = usePathname()
  const dict = useDictionary()
  const locale = (params?.locale as string) || 'es'
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    apiClient
      .get<AuthUser>(`${API_V1}/auth/me`)
      .then((me) => active && setUser(me))
      .catch(() => active && setUser(null))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [])

  useEffect(
    () =>
      authEvents.onUnauthorized(() => {
        setUser(null)
        if (pathname.endsWith('/login')) return
        router.replace(`/${locale}/login?redirect=${encodeURIComponent(pathname)}`)
      }),
    [router, locale, pathname]
  )

  useEffect(
    () => authEvents.onForbidden(() => toast.error(dict.access.forbidden)),
    [dict.access.forbidden]
  )

  const login = useCallback(async (username: string, password: string) => {
    const me = await apiClient.post<AuthUser>(`${API_V1}/auth/login`, { username, password })
    setUser(me)
  }, [])

  const logout = useCallback(async () => {
    await apiClient.post(`${API_V1}/auth/logout`).catch(() => undefined)
    setUser(null)
    router.replace(`/${locale}/login`)
  }, [router, locale])

  const refresh = useCallback(async () => {
    const me = await apiClient.get<AuthUser>(`${API_V1}/auth/me`)
    setUser(me)
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, logout, refresh }),
    [user, loading, login, logout, refresh]
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = use(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
