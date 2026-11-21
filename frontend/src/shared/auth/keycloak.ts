import Keycloak, { type KeycloakLoginOptions } from "keycloak-js"
import { identityFromToken, useAuthStore } from "./auth-store"
import { queryClient } from "../api/query-client"

const config = {
  url: import.meta.env.VITE_KEYCLOAK_URL,
  realm: import.meta.env.VITE_KEYCLOAK_REALM,
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID,
}
export const keycloak = new Keycloak({
  url: config.url || window.location.origin,
  realm: config.realm || "missing-config",
  clientId: config.clientId || "missing-config",
})

let initialization: Promise<void> | undefined
let redirecting = false
let refreshTimer: ReturnType<typeof setInterval> | undefined

function syncIdentity() {
  const identity = identityFromToken(keycloak.token, keycloak.tokenParsed)
  if (useAuthStore.getState().userId !== identity.userId) queryClient.clear()
  useAuthStore.setState({ ...identity, status: "ready", error: undefined })
}

export function safeRedirectUri(value = window.location.href) {
  const url = new URL(value, window.location.origin)
  if (url.origin !== window.location.origin)
    return `${window.location.origin}/account`
  return url.href
}

export function initializeAuth() {
  initialization ??= (async () => {
    try {
      if (!config.url || !config.realm || !config.clientId)
        throw new Error("missing config")
      keycloak.onAuthSuccess = syncIdentity
      keycloak.onAuthRefreshSuccess = syncIdentity
      keycloak.onAuthLogout = () => {
        sessionStorage.removeItem("agricontract.step-up")
        syncIdentity()
      }
      keycloak.onTokenExpired = () => {
        void refreshAccessToken().catch(() => undefined)
      }
      await keycloak.init({
        onLoad: "check-sso",
        flow: "standard",
        pkceMethod: "S256",
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        scope: "openid profile email",
        enableLogging: false,
      })
      syncIdentity()
      refreshTimer = setInterval(() => {
        if (keycloak.authenticated)
          void refreshAccessToken().catch(() => undefined)
      }, 15_000)
    } catch {
      useAuthStore.setState({
        ...identityFromToken(),
        status: "error",
        error:
          "Không kết nối được hệ thống đăng nhập. Vui lòng thử lại hoặc liên hệ quản trị viên.",
      })
    }
  })()
  return initialization
}

export async function login(options: KeycloakLoginOptions = {}) {
  await initializeAuth()
  if (useAuthStore.getState().status === "error")
    throw new Error(useAuthStore.getState().error)
  if (redirecting) return
  redirecting = true
  try {
    await keycloak.login({
      ...options,
      locale: "vi",
      redirectUri: safeRedirectUri(options.redirectUri),
    })
  } catch (error) {
    redirecting = false
    throw error
  }
}

export async function register() {
  await initializeAuth()
  if (useAuthStore.getState().status === "error")
    throw new Error(useAuthStore.getState().error)
  await keycloak.register({
    redirectUri: `${window.location.origin}/account`,
    locale: "vi",
  })
}

export async function refreshAccessToken() {
  await initializeAuth()
  if (!keycloak.authenticated) throw new Error("Bạn cần đăng nhập.")
  try {
    await keycloak.updateToken(30)
    if (!keycloak.authenticated || !keycloak.token)
      throw new Error("Phiên đăng nhập đã kết thúc.")
    syncIdentity()
    return keycloak.token
  } catch (error) {
    keycloak.clearToken()
    syncIdentity()
    await login()
    throw error
  }
}

export async function logout() {
  sessionStorage.removeItem("agricontract.step-up")
  useAuthStore.setState({ ...identityFromToken(), status: "initializing" })
  queryClient.clear()
  // Keep the adapter's id_token until it has constructed the logout URL.
  try {
    await keycloak.logout({ redirectUri: window.location.origin })
  } catch (error) {
    syncIdentity()
    throw error
  }
}

if (import.meta.hot) import.meta.hot.dispose(() => clearInterval(refreshTimer))
