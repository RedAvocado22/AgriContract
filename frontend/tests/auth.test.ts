import { describe, it, expect } from "vitest"
import { AxiosError, AxiosHeaders } from "axios"
import { identityFromToken } from "../src/shared/auth/auth-store"
import {
  INTENT_KEY,
  needsFreshLogin,
  readIntent,
} from "../src/shared/auth/step-up"
import { parseApiError, unwrapApiResponse } from "../src/shared/api/errors"

describe("identity and freshness", () => {
  it("takes identity from JWT, filters roles, never fabricates auth_time", () => {
    const result = identityFromToken("token", {
      sub: "user",
      iat: 100,
      realm_access: { roles: ["BUYER", "offline_access", "admin", "SELLER"] },
    })
    expect(result.roles).toEqual(["BUYER", "SELLER"])
    expect(result.authTime).toBeUndefined()
    expect(result.userId).toBe("user")
    expect(identityFromToken().authenticated).toBe(false)
  })
  it("requires step-up for missing, stale and implausible future auth time", () => {
    expect(needsFreshLogin(undefined, 300, 1000)).toBe(true)
    expect(needsFreshLogin(729, 300, 1000)).toBe(true)
    expect(needsFreshLogin(730, 300, 1000)).toBe(false)
    expect(needsFreshLogin(1100, 300, 1000)).toBe(true)
  })
})

function storageFor(value: unknown) {
  const data = new Map([[INTENT_KEY, JSON.stringify(value)]])
  return {
    getItem: (key: string) => data.get(key) ?? null,
    removeItem: (key: string) => data.delete(key),
  } as unknown as Storage
}
const intent = {
  action: "sign",
  contractId: "contract-1",
  step: "consent",
  userId: "buyer",
  returnUrl: "http://localhost:5173/account",
  requestedAt: 1000,
  maxAgeSeconds: 300,
}
describe("step-up intent", () => {
  it("restores the same actor, route and step after fresh authentication", () => {
    expect(
      readIntent(storageFor(intent), "buyer", intent.returnUrl, 1010, 1011)
    ).toEqual(intent)
  })
  it("does not restore stale authentication or another page", () => {
    expect(
      readIntent(storageFor(intent), "buyer", intent.returnUrl, 990, 1011)
    ).toBeUndefined()
    expect(
      readIntent(
        storageFor(intent),
        "buyer",
        "http://localhost:5173/",
        1010,
        1011
      )
    ).toBeUndefined()
  })
  it.each([
    { ...intent, userId: "another-user" },
    { ...intent, requestedAt: 1 },
    { ...intent, returnUrl: "https://external.test/" },
    { ...intent, maxAgeSeconds: "300" },
  ])("rejects invalid or cross-account intent", (invalid) => {
    const storage = storageFor(invalid)
    expect(
      readIntent(storage, "buyer", intent.returnUrl, 1010, 1011)
    ).toBeUndefined()
    expect(storage.getItem(INTENT_KEY)).toBeNull()
  })
})

function httpError(status: number, data: unknown) {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError("failed", "ERR_BAD_RESPONSE", config, undefined, {
    status,
    data,
    statusText: "",
    headers: {},
    config,
  })
}
describe("API formats", () => {
  it("unwraps legacy success and preserves direct objects and 204", () => {
    expect(unwrapApiResponse({ success: true, data: { userId: "1" } })).toEqual(
      { userId: "1" }
    )
    expect(unwrapApiResponse({ contractId: "1", terms: {} })).toEqual({
      contractId: "1",
      terms: {},
    })
    expect(unwrapApiResponse(undefined)).toBeUndefined()
    expect(() =>
      unwrapApiResponse({ success: false, error: "FAILED: lỗi" })
    ).toThrow()
  })
  it("splits only the first error-code colon", () => {
    expect(
      parseApiError(
        httpError(403, {
          success: false,
          error: "STEP_UP_REQUIRED: Xác thực: đã hết hạn",
        })
      )
    ).toEqual({
      status: 403,
      code: "STEP_UP_REQUIRED",
      message: "Xác thực: đã hết hạn",
    })
  })
  it("supports direct errors and network failures", () => {
    expect(
      parseApiError(httpError(409, { code: "OTP_EXPIRED", message: "Hết hạn" }))
        .code
    ).toBe("OTP_EXPIRED")
    expect(parseApiError(new Error("network")).status).toBe(0)
  })
})
