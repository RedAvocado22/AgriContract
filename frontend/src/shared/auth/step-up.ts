export const INTENT_KEY = "agricontract.step-up"
export interface ActionIntent {
  action: string
  contractId?: string
  step?: string
}
export interface StoredIntent extends ActionIntent {
  userId: string
  returnUrl: string
  requestedAt: number
  maxAgeSeconds: number
}

export function needsFreshLogin(
  authTime: number | undefined,
  maxAgeSeconds = 300,
  now = Date.now() / 1000
) {
  return (
    !Number.isFinite(authTime) ||
    !authTime ||
    authTime > now + 30 ||
    now - authTime > maxAgeSeconds - 30
  )
}

export function readIntent(
  storage: Storage,
  userId: string | undefined,
  href: string,
  authTime: number | undefined,
  now = Date.now() / 1000
): StoredIntent | undefined {
  const raw = storage.getItem(INTENT_KEY)
  if (!raw) return
  try {
    const intent = JSON.parse(raw) as StoredIntent
    if (
      !userId ||
      intent.userId !== userId ||
      typeof intent.action !== "string" ||
      !Number.isFinite(intent.requestedAt) ||
      now - intent.requestedAt > 600 ||
      intent.requestedAt > now + 30 ||
      !Number.isFinite(intent.maxAgeSeconds) ||
      intent.maxAgeSeconds <= 30 ||
      new URL(intent.returnUrl).origin !== new URL(href).origin
    ) {
      storage.removeItem(INTENT_KEY)
      return
    }
    if (intent.returnUrl !== href) return
    if (
      needsFreshLogin(authTime, intent.maxAgeSeconds, now) ||
      authTime! < intent.requestedAt - 1
    )
      return
    return intent
  } catch {
    storage.removeItem(INTENT_KEY)
    return
  }
}
