import { useCallback, useState } from "react"
import { useAuthStore } from "@/shared/auth/auth-store"
import { keycloak, login } from "@/shared/auth/keycloak"
import { parseApiError } from "@/shared/api/errors"
import {
  INTENT_KEY,
  needsFreshLogin,
  readIntent,
  type ActionIntent,
  type StoredIntent,
} from "@/shared/auth/step-up"

export function useFreshLogin(maxAgeSeconds = 300) {
  const [pendingIntent, setPendingIntent] = useState<StoredIntent | undefined>(
    () => {
      const { userId, authTime } = useAuthStore.getState()
      return readIntent(
        sessionStorage,
        userId,
        location.href,
        authTime,
        Date.now() / 1000 - (keycloak.timeSkew ?? 0)
      )
    }
  )

  const ensureFreshLogin = useCallback(
    async (intent: ActionIntent, force = false) => {
      if (!Number.isFinite(maxAgeSeconds) || maxAgeSeconds <= 30)
        throw new Error("Thời hạn xác thực phải lớn hơn 30 giây.")
      // Decide step-up before API refresh so a forced server challenge cannot be
      // replaced by an ordinary refresh/login that loses the action intent.
      const { userId, authTime } = useAuthStore.getState()
      const now = Date.now() / 1000 - (keycloak.timeSkew ?? 0)
      if (!force && !needsFreshLogin(authTime, maxAgeSeconds, now)) return true
      if (!userId) throw new Error("Bạn cần đăng nhập.")
      const saved: StoredIntent = {
        ...intent,
        userId,
        returnUrl: location.href,
        requestedAt: Math.floor(now),
        maxAgeSeconds,
      }
      // No token, OTP, password or automatic write replay is stored here.
      sessionStorage.setItem(INTENT_KEY, JSON.stringify(saved))
      try {
        await login({ prompt: "login", maxAge: 0, redirectUri: location.href })
      } catch (error) {
        sessionStorage.removeItem(INTENT_KEY)
        throw error
      }
      return false
    },
    [maxAgeSeconds]
  )

  const handleStepUpError = useCallback(
    async (error: unknown, intent: ActionIntent) => {
      const parsed = parseApiError(error)
      if (parsed.status !== 403 || parsed.code !== "STEP_UP_REQUIRED")
        return false
      await ensureFreshLogin(intent, true)
      return true
    },
    [ensureFreshLogin]
  )

  const consumeIntent = useCallback((action: string) => {
    const { userId, authTime } = useAuthStore.getState()
    const intent = readIntent(
      sessionStorage,
      userId,
      location.href,
      authTime,
      Date.now() / 1000 - (keycloak.timeSkew ?? 0)
    )
    if (intent?.action !== action) return
    sessionStorage.removeItem(INTENT_KEY)
    setPendingIntent(undefined)
    return intent
  }, [])

  return { ensureFreshLogin, handleStepUpError, pendingIntent, consumeIntent }
}
