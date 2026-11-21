import { describe, expect, it, vi, beforeEach } from "vitest"
import { AxiosError, AxiosHeaders } from "axios"
const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("@/shared/api/client", () => ({ apiClient: http }))
import {
  getOrganizationProfile,
  isMissingProfile,
  parseProfile,
  organizationFormSchema,
  registrationRequest,
  registerOrganization,
  organizationErrorMessage,
  registrationRoleProblem,
} from "../src/features/organization/api/profile"
import { canUseContractActions } from "../src/features/organization/hooks/use-organization-permissions"
import {
  selectOrganizationDocuments,
  uploadOrganizationDocument,
} from "../src/features/organization/api/documents"

const userId = "test-user"
const data = {
  userId,
  organizationName: "Doanh nghiệp kiểm thử",
  role: "BUYER",
  verificationStatus: "PENDING",
}
const values = {
  organizationName: "  Doanh nghiệp kiểm thử  ",
  role: "BUYER" as const,
  phone: "",
  address: "  Địa chỉ thử  ",
}
function failure(status: number, body: unknown, url = "/api/v1/users/me") {
  const config = { url, method: "get", headers: new AxiosHeaders() }
  return new AxiosError("failed", "ERR_BAD_RESPONSE", config, undefined, {
    status,
    statusText: "",
    config,
    headers: {},
    data: body,
  })
}
beforeEach(() => vi.clearAllMocks())

describe("organization profile API", () => {
  it("accepts only the current actor's exact missing-profile response", async () => {
    const error = failure(404, {
      success: false,
      data: null,
      error: `User not found: ${userId}`,
    })
    expect(isMissingProfile(error, userId)).toBe(true)
    http.get.mockRejectedValue(error)
    expect(await getOrganizationProfile(userId)).toBeNull()
  })
  it.each([
    failure(404, "Not Found"),
    failure(404, { success: false, error: "Not Found" }),
    failure(404, { success: false, error: "User not found: another-user" }),
    failure(
      404,
      { success: false, error: `User not found: ${userId}` },
      "/wrong-endpoint"
    ),
    failure(403, { success: false, error: `User not found: ${userId}` }),
  ])("does not mistake an unknown failure for onboarding", async (error) => {
    http.get.mockRejectedValue(error)
    await expect(getOrganizationProfile(userId)).rejects.toBe(error)
  })
  it("safely handles optional fields without inventing verification", () => {
    const profile = parseProfile({ userId }, userId)
    expect(profile.organizationName).toBeNull()
    expect(profile.verificationStatus).toBeNull()
    expect(profile.phone).toBeNull()
    expect(() => parseProfile({ ...data, userId: "other" }, userId)).toThrow()
    expect(() => parseProfile(null, userId)).toThrow()
  })
  it("fetches /me through the shared client with cancellation", async () => {
    http.get.mockResolvedValue({ data })
    const signal = new AbortController().signal
    expect((await getOrganizationProfile(userId, signal))?.role).toBe("BUYER")
    expect(http.get).toHaveBeenCalledWith("/api/v1/users/me", { signal })
  })
  it("sends only the actual DTO fields and accepts the server's status", async () => {
    http.post.mockResolvedValue({ data })
    const result = await registerOrganization(values, userId, ["BUYER"])
    expect(http.post).toHaveBeenCalledWith("/api/v1/users/register", {
      organizationName: "Doanh nghiệp kiểm thử",
      phone: null,
      address: "Địa chỉ thử",
    })
    expect(result.verificationStatus).toBe("PENDING")
  })
  it("validates a nonblank organization name without inventing phone policy", () => {
    expect(
      organizationFormSchema.safeParse({ ...values, organizationName: "   " })
        .success
    ).toBe(false)
    expect(
      organizationFormSchema.safeParse({ ...values, phone: "+84 123 456" })
        .success
    ).toBe(true)
  })
  it("blocks role mismatch, no role, privileged-only and ambiguous roles before POST", async () => {
    for (const roles of [
      [],
      ["SELLER"],
      ["ADMIN"],
      ["BUYER", "SELLER"],
      ["BUYER", "ADMIN"],
    ] as const) {
      await expect(
        registerOrganization(values, userId, [...roles])
      ).rejects.toThrow()
    }
    expect(http.post).not.toHaveBeenCalled()
    expect(registrationRoleProblem(["SELLER"], "SELLER")).toBeNull()
    expect(
      registrationRequest({ ...values, role: "SELLER" }, ["SELLER"])
    ).not.toHaveProperty("role")
  })
  it.each([401, 403, 404, 500])(
    "preserves HTTP %i errors for UI/auth handling",
    async (status) => {
      const error = failure(status, { success: false, error: "unavailable" })
      http.get.mockRejectedValue(error)
      await expect(getOrganizationProfile(userId)).rejects.toBe(error)
      expect(organizationErrorMessage(error)).not.toContain("unavailable")
    }
  )
  it("preserves network failure and offers a localized message", async () => {
    http.get.mockRejectedValue(new AxiosError("network"))
    await expect(getOrganizationProfile(userId)).rejects.toThrow()
    expect(organizationErrorMessage(new AxiosError("network"))).toContain(
      "Không kết nối"
    )
  })
})

describe("verification permissions", () => {
  it.each(["PENDING", "REJECTED", "NEW_UNKNOWN_STATUS", null])(
    "never permits create/sign for %s",
    (verificationStatus) => {
      expect(
        canUseContractActions(
          parseProfile({ ...data, verificationStatus }, userId),
          ["BUYER"]
        )
      ).toBe(false)
    }
  )
  it("requires verified profile plus matching participant role", () => {
    const profile = parseProfile(
      { ...data, verificationStatus: "VERIFIED" },
      userId
    )
    expect(canUseContractActions(profile, ["BUYER"])).toBe(true)
    expect(canUseContractActions(profile, ["SELLER"])).toBe(false)
    expect(canUseContractActions(null, ["BUYER"])).toBe(false)
  })
})

it("document adapter never sends files or returns fake upload success", async () => {
  const file = { name: "local-document", size: 10 } as File
  const [document] = selectOrganizationDocuments([file])
  expect(document.storage).toBe("local-only")
  await expect(uploadOrganizationDocument(document)).rejects.toThrow(
    "chưa sẵn sàng"
  )
  expect(http.post).not.toHaveBeenCalled()
})
