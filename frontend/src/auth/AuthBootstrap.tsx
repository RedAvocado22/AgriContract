import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'

import { TOKEN_STORAGE_KEY } from '../api/authApi'
import { isNotFoundError, userApi } from '../api/userApi'
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
      const token = window.localStorage.getItem(TOKEN_STORAGE_KEY)

      if (!token) {
        logoutStore()
        setIsReady(true)
        return
      }

      setSession(token)

      try {
        const profile = await userApi.getMe()
        setUser(profile)
      } catch (error) {
        if (isNotFoundError(error)) {
          markProfileMissing()
        } else {
          window.localStorage.removeItem(TOKEN_STORAGE_KEY)
          logoutStore()
        }
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
