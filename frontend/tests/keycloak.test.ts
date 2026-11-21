import { afterEach, beforeEach, expect, it, vi } from "vitest"

const sdk = vi.hoisted(() => ({
  authenticated: true,
  token: "first-token",
  tokenParsed: {
    sub: "buyer",
    auth_time: 1000,
    realm_access: { roles: ["BUYER"] },
  },
  init: vi.fn(),
  updateToken: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  clearToken: vi.fn(),
  onAuthSuccess: undefined as undefined | (() => void),
  onAuthRefreshSuccess: undefined as undefined | (() => void),
  onAuthLogout: undefined as undefined | (() => void),
}))
vi.mock("keycloak-js", () => ({
  default: class {
    constructor() {
      return sdk
    }
  },
}))
vi.mock("../src/shared/api/query-client", () => ({
  queryClient: { clear: vi.fn() },
}))

beforeEach(() => {
  vi.resetModules()
  vi.clearAllMocks()
  vi.useFakeTimers()
  vi.stubGlobal("window", {
    location: {
      origin: "http://localhost:5173",
      href: "http://localhost:5173/account",
    },
  })
  vi.stubGlobal("sessionStorage", { removeItem: vi.fn() })
  vi.stubEnv("VITE_KEYCLOAK_URL", "http://localhost:8180")
  vi.stubEnv("VITE_KEYCLOAK_REALM", "agricontract")
  vi.stubEnv("VITE_KEYCLOAK_CLIENT_ID", "agricontract-frontend")
  sdk.authenticated = true
  sdk.token = "first-token"
  sdk.init.mockResolvedValue(true)
  sdk.updateToken.mockResolvedValue(false)
})
afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
})

it("initializes once with code + PKCE and refreshes periodically", async () => {
  const auth = await import("../src/shared/auth/keycloak")
  await Promise.all([auth.initializeAuth(), auth.initializeAuth()])
  expect(sdk.init).toHaveBeenCalledOnce()
  expect(sdk.init).toHaveBeenCalledWith(
    expect.objectContaining({
      flow: "standard",
      pkceMethod: "S256",
      onLoad: "check-sso",
      silentCheckSsoRedirectUri: "http://localhost:5173/silent-check-sso.html",
    })
  )
  await vi.advanceTimersByTimeAsync(15_000)
  expect(sdk.updateToken).toHaveBeenCalledWith(30)
})

it("syncs refreshed JWT into memory and restricts redirect origin", async () => {
  const auth = await import("../src/shared/auth/keycloak")
  const { useAuthStore } = await import("../src/shared/auth/auth-store")
  await auth.initializeAuth()
  sdk.updateToken.mockImplementation(async () => {
    sdk.token = "refreshed-token"
    sdk.onAuthRefreshSuccess?.()
    return true
  })
  await auth.refreshAccessToken()
  expect(useAuthStore.getState().token).toBe("refreshed-token")
  expect(useAuthStore.getState().authTime).toBe(1000)
  expect(auth.safeRedirectUri("https://external.test/")).toBe(
    "http://localhost:5173/account"
  )
})

it("clears a failed refresh and redirects to login once", async () => {
  const auth = await import("../src/shared/auth/keycloak")
  await auth.initializeAuth()
  sdk.updateToken.mockRejectedValue(new Error("expired"))
  sdk.clearToken.mockImplementation(() => {
    sdk.authenticated = false
    sdk.token = ""
    sdk.onAuthLogout?.()
  })
  await expect(auth.refreshAccessToken()).rejects.toThrow("expired")
  await auth.login()
  expect(sdk.clearToken).toHaveBeenCalledOnce()
  expect(sdk.login).toHaveBeenCalledOnce()
})

it("reports initialization failure instead of treating it as anonymous success", async () => {
  const auth = await import("../src/shared/auth/keycloak")
  sdk.init.mockRejectedValue(new Error("unreachable"))
  await auth.initializeAuth()
  const { useAuthStore } = await import("../src/shared/auth/auth-store")
  expect(useAuthStore.getState().status).toBe("error")
  expect(useAuthStore.getState().authenticated).toBe(false)
  await expect(auth.login()).rejects.toThrow()
  expect(sdk.login).not.toHaveBeenCalled()
})
