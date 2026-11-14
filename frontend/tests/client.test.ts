import { beforeEach, describe, expect, it, vi } from "vitest"
import { AxiosError } from "axios"

const auth = vi.hoisted(() => ({
  keycloak: { authenticated: true },
  login: vi.fn(),
  refreshAccessToken: vi.fn(),
}))
vi.mock("../src/shared/auth/keycloak", () => auth)
vi.stubGlobal("window", { location: { origin: "http://localhost:5173" } })
vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8080")
const { apiClient, newIdempotencyKey } = await import(
  "../src/shared/api/client"
)

beforeEach(() => {
  vi.clearAllMocks()
  auth.keycloak.authenticated = true
  auth.refreshAccessToken.mockResolvedValue("fresh-token")
})

describe("Gateway authentication", () => {
  it("refreshes before sending and unwraps the legacy response", async () => {
    const adapter = vi.fn(async (config) => {
      expect(auth.refreshAccessToken).toHaveBeenCalledOnce()
      expect(config.headers.get("Authorization")).toBe("Bearer fresh-token")
      return {
        config,
        status: 200,
        statusText: "OK",
        headers: {},
        data: { success: true, data: { userId: "user" } },
      }
    })
    const response = await apiClient.get("/api/v1/users/me", { adapter })
    expect(response.data).toEqual({ userId: "user" })
  })
  it("rejects cross-origin URLs and Gateway overrides before attaching tokens", async () => {
    const adapter = vi.fn()
    await expect(
      apiClient.get("https://external.test/", { adapter })
    ).rejects.toThrow()
    await expect(
      apiClient.get("/api/v1/users/me", {
        baseURL: "https://external.test",
        adapter,
      })
    ).rejects.toThrow()
    expect(auth.refreshAccessToken).not.toHaveBeenCalled()
    expect(adapter).not.toHaveBeenCalled()
  })
  it("does not dispatch an API request after refresh failure", async () => {
    const adapter = vi.fn()
    auth.refreshAccessToken.mockRejectedValue(new Error("expired"))
    await expect(
      apiClient.get("/api/v1/users/me", { adapter })
    ).rejects.toThrow("expired")
    expect(adapter).not.toHaveBeenCalled()
  })
  it("redirects unauthenticated users without dispatching", async () => {
    auth.keycloak.authenticated = false
    const adapter = vi.fn()
    await expect(
      apiClient.get("/api/v1/users/me", { adapter })
    ).rejects.toThrow()
    expect(auth.login).toHaveBeenCalledOnce()
    expect(adapter).not.toHaveBeenCalled()
  })
  it.each([401, 403])(
    "handles HTTP %i without replaying a write",
    async (status) => {
      const adapter = vi.fn(async (config) => {
        throw new AxiosError("failed", "ERR_BAD_RESPONSE", config, undefined, {
          config,
          status,
          statusText: "",
          headers: {},
          data: { code: "STEP_UP_REQUIRED" },
        })
      })
      await expect(
        apiClient.post("/api/v1/example", {}, { adapter })
      ).rejects.toThrow()
      expect(adapter).toHaveBeenCalledOnce()
      expect(auth.login).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
    }
  )
  it("creates a different idempotency UUID per explicit invocation", () => {
    const first = newIdempotencyKey()
    expect(first).toMatch(/^[0-9a-f-]{36}$/)
    expect(newIdempotencyKey()).not.toBe(first)
  })
})
