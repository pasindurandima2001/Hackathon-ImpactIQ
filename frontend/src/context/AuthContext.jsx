import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi, UNAUTHORIZED_EVENT } from '../services/api.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [registrationEnabled, setRegistrationEnabled] = useState(true)
  const [checking, setChecking] = useState(true)

  const refresh = useCallback(async () => {
    try {
      await authApi.csrf()
      const status = await authApi.me()
      setUser(status.authenticated ? status.user : null)
      setRegistrationEnabled(status.registrationEnabled)
    } catch {
      setUser(null)
    } finally {
      setChecking(false)
    }
  }, [])

  useEffect(() => { refresh() }, [refresh])

  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  const login = useCallback(async (username, password) => {
    const signedIn = await authApi.login({ username, password })
    setUser(signedIn)
    return signedIn
  }, [])

  const register = useCallback(async (payload) => {
    await authApi.register(payload)
    return login(payload.username, payload.password)
  }, [login])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setUser(null)
      await authApi.csrf().catch(() => {})
    }
  }, [])

  const value = useMemo(() => ({ user, checking, registrationEnabled, login, register, logout }),
    [user, checking, registrationEnabled, login, register, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
