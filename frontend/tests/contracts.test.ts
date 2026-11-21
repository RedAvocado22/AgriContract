import { beforeEach, describe, expect, it, vi } from "vitest"
import { AxiosError, AxiosHeaders } from "axios"
const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  key: 0,
}))
vi.mock("@/shared/api/client", () => ({
  apiClient: http,
  newIdempotencyKey: () => `operation-${++http.key}`,
}))
import {
  getContract,
  listContracts,
  mapContract,
  mapContractPage,
  patchTerms,
  termsInput,
  inputKeys,
} from "../src/features/contracts/api/contracts"
import {
  initiateSigning,
  validConsent,
  verifySigning,
} from "../src/features/contracts/api/signing"
import {
  getSigningConsent,
  createCoffeeContract,
  getContractDocuments,
} from "../src/features/contracts/api/capabilities"
import { SigningSession } from "../src/features/contracts/hooks/signing-session"
import {
  activationMessage,
  canDownloadDocument,
  canNegotiate,
  contractError,
  decimalMoney,
  documentMessage,
  participantRole,
  pollInterval,
  vietnamTime,
} from "../src/features/contracts/types/presentation"
import { formatDecimalVnd } from "../src/shared/lib/decimal-display"
import { canUseContractActions } from "../src/features/organization/hooks/use-organization-permissions"
import { negotiationSchema } from "../src/features/contracts/schemas/negotiation"

// Synthetic fixtures only. No production/runtime mock data or backend writes.
const hash = "a".repeat(64)
const id = "10000000-0000-4000-8000-000000000001"
const legacy = {
  contractId: id,
  listingId: "listing-test",
  buyerId: "buyer-test",
  sellerId: "seller-test",
  status: "OFFERED",
  terms: {
    agreedPrice: { amount: 95000, currency: "VND" },
    quantity: { value: 100, unit: "kg" },
  },
}
const coffee = {
  ...legacy,
  signedContentHash: hash,
  reservedQuantity: 100,
  milestones: [],
  terms: { agreedPrice: "95000.00", totalContractValue: "9999999999999999.99" },
}
const consent = {
  action: "SIGN_CONTRACT_AS_BUYER",
  version: "test-version",
  text: "Cam kết chỉ dùng trong test",
  contractId: id,
  contentHash: hash,
}
const challenge = {
  otpId: "20000000-0000-4000-8000-000000000002",
  expiresAt: "2099-01-01T00:00:00Z",
}
const envelope = (data: unknown) => ({ success: true, data })
const page = (content: unknown[]) => ({
  content,
  page: 0,
  totalPages: content.length ? 1 : 0,
  totalElements: content.length,
})
function failure(status: number, code = "TEST_ERROR") {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError("test failure", "ERR_BAD_RESPONSE", config, undefined, {
    status,
    statusText: "",
    config,
    headers: {},
    data: { success: false, error: `${code}: test message` },
  })
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe("contract read adapters (mock HTTP)", () => {
  it("maps legacy envelope and Phase 2 direct object without inventing fields", () => {
    const old = mapContract(envelope(legacy), "buyer-test")
    const current = mapContract(coffee, "buyer-test")
    expect(old.generation).toBe("legacy")
    expect(old.hash).toBeNull()
    expect(current.generation).toBe("coffee")
    expect(current.hash).toBe(hash)
    expect(current.terms.totalContractValue).toBe("9999999999999999.99")
    expect(current.sellerName).toBeUndefined()
    expect(current.editability).toBe("unknown")
  })
  it("recognizes a null hash as Phase 2 without granting signing or editing", () => {
    expect(
      mapContract({ ...coffee, signedContentHash: null }, "buyer-test")
        .generation
    ).toBe("coffee")
    expect(validConsent(consent, id, null, "BUYER")).toBe(false)
  })
  it("lists only actor-owned data for selected role with server pagination", async () => {
    http.get.mockResolvedValueOnce({ data: envelope(page([legacy])) })
    const result = await listContracts("buyer-test", "BUYER", "SIGNED", 2)
    expect(result.content).toHaveLength(1)
    expect(http.get.mock.calls[0][1].params).toEqual({
      role: "BUYER",
      status: "SIGNED",
      page: 2,
      size: 20,
    })
  })
  it("handles an empty list without a manufactured contract", () => {
    expect(
      mapContractPage(envelope(page([])), "buyer-test", "BUYER").content
    ).toEqual([])
  })
  it("rejects unrelated user and wrong role list instead of leaking rows", () => {
    expect(() => mapContract(coffee, "stranger")).toThrow()
    expect(() =>
      mapContractPage(page([legacy]), "buyer-test", "SELLER")
    ).toThrow()
  })
  it("gets the exact detail and rejects mismatched ids", async () => {
    http.get.mockResolvedValue({ data: coffee })
    expect((await getContract(id, "buyer-test")).id).toBe(id)
    await expect(getContract("other-id", "buyer-test")).rejects.toThrow()
  })
  it.each([400, 401, 403, 404, 409, 422, 500, 503])(
    "preserves HTTP %s without producing a success/empty state",
    async (status) => {
      http.get.mockRejectedValue(failure(status))
      await expect(getContract(id, "buyer-test")).rejects.toBeInstanceOf(
        AxiosError
      )
      await expect(
        listContracts("buyer-test", "BUYER", "", 0)
      ).rejects.toBeInstanceOf(AxiosError)
      expect(contractError(failure(status))).not.toBe("")
    }
  )
  it("preserves network error", async () => {
    http.get.mockRejectedValue(new AxiosError("network"))
    await expect(getContract(id, "buyer-test")).rejects.toThrow()
    expect(contractError(new AxiosError("network"))).toContain("kết nối")
  })
})

describe("negotiation and capability boundaries", () => {
  it("prepares only allowed input and excludes server fields", () => {
    const snapshot = Object.fromEntries(inputKeys.map((key) => [key, null]))
    const input = termsInput(
      {
        ...snapshot,
        goodsTerms: {},
        totalContractValue: "999.00",
        reservedQuantity: 50,
        contractTemplateVersion: "test",
      },
      "90000.00"
    )
    expect(Object.keys(input)).toEqual([...inputKeys])
    expect(input.agreedPrice).toBe("90000.00")
    expect(() => termsInput({}, "10.00")).toThrow()
  })
  it("maps PATCH success but does not change state after conflict", async () => {
    http.patch.mockResolvedValueOnce({
      data: { ...coffee, signedContentHash: "b".repeat(64) },
    })
    expect(
      (await patchTerms(id, { agreedPrice: "90000.00" }, "key", "buyer-test"))
        .hash
    ).toBe("b".repeat(64))
    http.patch.mockRejectedValueOnce(failure(409, "CONTRACT_TERMS_FROZEN"))
    await expect(
      patchTerms(id, { agreedPrice: "90000.00" }, "key2", "buyer-test")
    ).rejects.toThrow()
    expect(coffee.signedContentHash).toBe(hash)
  })
  it("rejects server-owned fields before HTTP", async () => {
    await expect(
      patchTerms(id, { totalContractValue: "1.00" }, "key", "buyer-test")
    ).rejects.toThrow()
    expect(http.patch).not.toHaveBeenCalled()
  })
  it("blocks save even for VERIFIED when current API cannot expose freeze/staleness", () => {
    expect(canNegotiate(mapContract(coffee, "buyer-test"), true)).toBe(false)
    expect(canNegotiate(mapContract(coffee, "buyer-test"), false)).toBe(false)
  })
  it("validates exact price text without parseFloat", () => {
    expect(
      negotiationSchema.safeParse({ agreedPrice: "95000.00" }).success
    ).toBe(true)
    for (const value of ["", "NaN", "1e6", "0.001", "100000000.00"])
      expect(negotiationSchema.safeParse({ agreedPrice: value }).success).toBe(
        false
      )
  })
  it("does not call absent consent/create/document endpoints", async () => {
    expect(await getSigningConsent()).toBeNull()
    await expect(createCoffeeContract()).rejects.toThrow()
    await expect(getContractDocuments()).rejects.toThrow()
    expect(http.get).not.toHaveBeenCalled()
    expect(http.post).not.toHaveBeenCalled()
  })
})

describe("OTP session and actual DTO transport with mocked server", () => {
  it.each([
    null,
    { ...consent, version: "" },
    { ...consent, text: "" },
    { ...consent, contentHash: "b".repeat(64) },
    { ...consent, action: "OTHER_ACTION" },
    { ...consent, contractId: "other" },
  ])("blocks missing or mismatched consent before initiate", async (value) => {
    await expect(
      initiateSigning(id, "BUYER", hash, value, "key")
    ).rejects.toThrow()
    expect(http.post).not.toHaveBeenCalled()
  })
  it("sends actual initiate DTO with server-provided version, never statement/hash fabrication", async () => {
    http.post.mockResolvedValueOnce({ data: challenge })
    expect(await initiateSigning(id, "BUYER", hash, consent, "key")).toEqual(
      challenge
    )
    expect(http.post.mock.calls[0][1]).toEqual({
      signerRole: "BUYER",
      consentStatementVersion: consent.version,
      presentedContentHash: hash,
    })
  })
  it("only 204 verifies one signature; never sets ACTIVE", async () => {
    http.post
      .mockResolvedValueOnce({ data: challenge })
      .mockResolvedValueOnce({ status: 204 })
    const session = new SigningSession(id, "BUYER", hash)
    await session.initiate(consent)
    await session.verify("123456")
    expect(session.complete).toBe(true)
    expect(session.challenge).toBeNull()
    expect(coffee.status).toBe("OFFERED")
    await expect(session.verify("123456")).rejects.toThrow()
    expect(http.post).toHaveBeenCalledTimes(2)
  })
  it("rejects non-204 success, expired and malformed OTP", async () => {
    http.post.mockResolvedValueOnce({ status: 200 })
    await expect(
      verifySigning(id, challenge, "123456", "key")
    ).rejects.toThrow()
    await expect(
      verifySigning(
        id,
        { ...challenge, expiresAt: "2000-01-01T00:00:00Z" },
        "123456",
        "key"
      )
    ).rejects.toThrow("hết hạn")
    await expect(verifySigning(id, challenge, "1", "key")).rejects.toThrow()
    expect(http.post).toHaveBeenCalledTimes(1)
  })
  it("blocks duplicate in-flight initiate and retains key for explicit network retry", async () => {
    let reject!: (value: unknown) => void
    http.post.mockReturnValueOnce(
      new Promise((_, r) => {
        reject = r
      })
    )
    const session = new SigningSession(id, "BUYER", hash)
    const pending = session.initiate(consent)
    await expect(session.initiate(consent)).rejects.toThrow()
    reject(new AxiosError("network"))
    await expect(pending).rejects.toThrow()
    http.post.mockResolvedValueOnce({ data: challenge })
    await session.initiate(consent)
    expect(http.post.mock.calls[0][2]).toEqual(http.post.mock.calls[1][2])
  })
  it("retains verify key on ambiguous failure and blocks concurrent verify", async () => {
    const session = new SigningSession(id, "BUYER", hash)
    session.challenge = challenge
    let reject!: (value: unknown) => void
    http.post.mockReturnValueOnce(
      new Promise((_, r) => {
        reject = r
      })
    )
    const pending = session.verify("123456")
    await expect(session.verify("123456")).rejects.toThrow()
    reject(new AxiosError("network"))
    await expect(pending).rejects.toThrow()
    http.post.mockResolvedValueOnce({ status: 204 })
    await session.verify("123456")
    expect(http.post.mock.calls[0][2]).toEqual(http.post.mock.calls[1][2])
  })
  it("wrong OTP allows user correction with a new logical key", async () => {
    const session = new SigningSession(id, "BUYER", hash)
    session.challenge = challenge
    http.post.mockRejectedValueOnce(failure(409, "OTP_INVALID"))
    await expect(session.verify("111111")).rejects.toThrow()
    expect(session.complete).toBe(false)
    expect(session.challenge).toEqual(challenge)
    http.post.mockResolvedValueOnce({ status: 204 })
    await session.verify("222222")
    expect(http.post.mock.calls[0][2]).not.toEqual(http.post.mock.calls[1][2])
  })
  it.each([
    "OTP_EXPIRED",
    "OTP_ATTEMPTS_EXHAUSTED",
    "OTP_CHALLENGE_NOT_ACTIVE",
    "OTP_TERMS_CHANGED",
  ])("invalidates challenge after %s", async (code) => {
    const session = new SigningSession(id, "BUYER", hash)
    session.challenge = challenge
    http.post.mockRejectedValueOnce(failure(409, code))
    await expect(session.verify("111111")).rejects.toThrow()
    expect(session.challenge).toBeNull()
    expect(session.complete).toBe(false)
  })
  it("resend invalidates previous challenge even if email fails", async () => {
    const session = new SigningSession(id, "BUYER", hash)
    session.challenge = challenge
    http.post.mockRejectedValueOnce(failure(503, "OTP_EMAIL_FAILED"))
    await expect(session.initiate(consent, true)).rejects.toThrow()
    expect(session.challenge).toBeNull()
    expect(http.post).toHaveBeenCalledTimes(1)
  })
  it("resend gets new challenge and cooldown rejects immediate repeat", async () => {
    const session = new SigningSession(id, "BUYER", hash)
    session.challenge = challenge
    http.post.mockResolvedValueOnce({
      data: { ...challenge, otpId: "30000000-0000-4000-8000-000000000003" },
    })
    await session.initiate(consent, true)
    expect(session.challenge?.otpId).not.toBe(challenge.otpId)
    await expect(session.initiate(consent, true)).rejects.toThrow()
    expect(http.post).toHaveBeenCalledTimes(1)
  })
})

describe("lifecycle, permissions and precision", () => {
  it("resend retry reuses its logical key and cooldown cannot be bypassed by initiate", async () => {
    const session = new SigningSession(id, "BUYER", hash)
    session.challenge = challenge
    http.post.mockRejectedValueOnce(new AxiosError("network"))
    await expect(session.initiate(consent, true)).rejects.toThrow()
    http.post.mockResolvedValueOnce({ data: challenge })
    await session.initiate(consent, true)
    expect(http.post.mock.calls[0][2]).toEqual(http.post.mock.calls[1][2])
    await expect(session.initiate(consent)).rejects.toThrow()
    expect(http.post).toHaveBeenCalledTimes(2)
  })
  it("local deadline prevents verify even when the browser timer was throttled", async () => {
    const session = new SigningSession(id, "BUYER", hash)
    session.challenge = { ...challenge, expiresAt: "2000-01-01T00:00:00Z" }
    await expect(session.verify("123456")).rejects.toThrow("hết hạn")
    expect(session.challenge).toBeNull()
    expect(http.post).not.toHaveBeenCalled()
  })
  it("SIGNED stays waiting; only ACTIVE reports activation", () => {
    expect(activationMessage("SIGNED")).toContain("chưa được kích hoạt")
    expect(activationMessage("ACTIVE")).toContain("Đã kích hoạt")
    expect(activationMessage("OFFERED")).toContain("Chưa thể xác định")
  })
  it("PENDING PDF never has a download permission", () => {
    expect(canDownloadDocument("PENDING")).toBe(false)
    expect(canDownloadDocument()).toBe(false)
    expect(canDownloadDocument("READY")).toBe(true)
    expect(documentMessage("PENDING")).toContain("đang được tạo")
  })
  it("bounds polling and stops on error, activation or timeout", () => {
    expect(pollInterval("SIGNED", 0, 119999, false)).toBe(5000)
    expect(pollInterval("SIGNED", 0, 120000, false)).toBe(false)
    expect(pollInterval("ACTIVE", 0, 1, false)).toBe(false)
    expect(pollInterval("SIGNED", 0, 1, true)).toBe(false)
  })
  it("pending and wrong participant role cannot sign; verified matching role passes profile guard", () => {
    const profile = {
      userId: "buyer-test",
      role: "BUYER",
      verificationStatus: "PENDING",
    }
    expect(canUseContractActions(profile, ["BUYER"])).toBe(false)
    expect(
      canUseContractActions({ ...profile, verificationStatus: "VERIFIED" }, [
        "BUYER",
      ])
    ).toBe(true)
    const contract = mapContract(coffee, "buyer-test")
    expect(participantRole(contract, "buyer-test", ["SELLER"])).toBeNull()
    expect(participantRole(contract, "buyer-test", ["BUYER"])).toBe("BUYER")
  })
  it("preserves large decimal money and rejects already-lossy legacy numbers", () => {
    expect(formatDecimalVnd("9999999999999999.99")).toBe(
      "9.999.999.999.999.999,99"
    )
    expect(decimalMoney(Number("9999999999999999"))).toBeNull()
    expect(decimalMoney(0.1 + 0.2)).toBeNull()
    expect(decimalMoney(95000)).toBe("95000")
  })
  it("displays UTC in Vietnam without altering date-only deadline", () => {
    expect(vietnamTime("2026-11-09T00:00:00Z")).toContain("07:00")
    expect(vietnamTime("2026-11-09")).toBe("09/11/2026")
    expect(vietnamTime("2026-11-09T00:00:00")).toContain("chưa xác định")
  })
})
