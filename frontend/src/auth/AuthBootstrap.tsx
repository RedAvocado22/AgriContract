import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'

import { isNotFoundError, userApi } from '../api/userApi'
import { initializeKeycloak, keycloak, refreshKeycloakToken } from './keycloak'
import { env } from '../config/env'
import { useAuthStore } from '../stores/authStore'

interface AuthBootstrapProps {
  children: ReactNode
}

export function AuthBootstrap({ children }: AuthBootstrapProps) {
  const [isReady, setIsReady] = useState(false)
  const setSession = useAuthStore((state) => state.setSession)
  const setUser = useAuthStore((state) => state.setUser)
  const markProfileMissing = useAuthStore((state) => state.markProfileMissing)
  const logoutStore = useAuthStore((state) => state.logout)

  useEffect(() => {
    const bootstrap = async () => {
      if (env.useMocks) {
        setIsReady(true)
        return
      }

      try {
        const authenticated = await initializeKeycloak()
        const token = keycloak.token ?? null

        if (!authenticated || !token) {
          logoutStore()
          setIsReady(true)
          return
        }

        setSession(token)
        keycloak.onTokenExpired = () => {
          void refreshKeycloakToken()
            .then(setSession)
            .catch(logoutStore)
        }

        try {
          const profile = await userApi.getMe()
          setUser(profile)
        } catch (error) {
          if (isNotFoundError(error)) {
            markProfileMissing()
          } else {
            throw error
          }
        }
      } catch {
        logoutStore()
      } finally {
        setIsReady(true)
      }
    }

    void bootstrap()
  }, [logoutStore, markProfileMissing, setSession, setUser])

  if (!isReady) {
    return <div className="empty-state">Đang kiểm tra đăng nhập...</div>
  }

  return children
}
