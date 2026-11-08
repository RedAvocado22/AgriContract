import { newIdempotencyKey } from "@/shared/api/client"
import { parseApiError } from "@/shared/api/errors"
import { initiateSigning, verifySigning } from "../api/signing"
import type { Challenge, Consent, ParticipantRole } from "../types/contract"

/** One mounted signing session. Secrets/challenges never leave memory; no automatic retries. */
export class SigningSession {
  challenge: Challenge | null = null
  complete = false
  busy = false
  resendAfter = 0
  private initiateKey: string | null = null
  private verifyOperation: { code: string; key: string } | null = null
  readonly id: string
  readonly role: ParticipantRole
  readonly hash: string
  constructor(id: string, role: ParticipantRole, hash: string) {
    this.id = id
    this.role = role
    this.hash = hash
  }

  async initiate(consent: Consent | null, resend = false) {
    if (this.busy || this.complete)
      throw new Error("Thao tác đang xử lý hoặc chữ ký đã được xác nhận.")
    if (Date.now() < this.resendAfter)
      throw new Error("Vui lòng chờ trước khi gửi lại mã.")
    this.busy = true
    // On resend invalidate local old challenge BEFORE the request, including ambiguous network failure.
    if (resend || this.challenge) {
      this.challenge = null
      this.verifyOperation = null
      // An ambiguous resend retry must retain its logical key.
      // A resolved previous operation already cleared initiateKey.
    }
    this.initiateKey ??= newIdempotencyKey()
    try {
      this.challenge = await initiateSigning(
        this.id,
        this.role,
        this.hash,
        consent,
        this.initiateKey
      )
      this.initiateKey = null
      this.resendAfter = Date.now() + 60_000
    } catch (error) {
      const parsed = parseApiError(error)
      // Keep the logical key only for ambiguous transport/server failures.
      if (parsed.status && parsed.status < 500) this.initiateKey = null
      if (parsed.code === "OTP_RESEND_TOO_SOON")
        this.resendAfter = Date.now() + 60_000
      if (parsed.code === "OTP_RESEND_LIMIT_REACHED")
        this.resendAfter = Date.now() + 3_600_000
      throw error
    } finally {
      this.busy = false
    }
  }

  async verify(code: string) {
    if (this.busy || this.complete)
      throw new Error("Thao tác đang xử lý hoặc chữ ký đã được xác nhận.")
    if (!this.challenge) throw new Error("Bạn cần gửi mã OTP mới.")
    if (Date.parse(this.challenge.expiresAt) <= Date.now()) {
      this.challenge = null
      this.verifyOperation = null
      throw new Error("Mã OTP đã hết hạn. Vui lòng gửi mã mới.")
    }
    this.busy = true
    if (this.verifyOperation?.code !== code)
      this.verifyOperation = { code, key: newIdempotencyKey() }
    try {
      await verifySigning(
        this.id,
        this.challenge,
        code,
        this.verifyOperation.key
      )
      this.complete = true
      this.challenge = null
      this.verifyOperation = null
    } catch (error) {
      const { code: errorCode, status } = parseApiError(error)
      if (status && status < 500) this.verifyOperation = null
      if (
        [
          "OTP_EXPIRED",
          "OTP_ATTEMPTS_EXHAUSTED",
          "OTP_CHALLENGE_NOT_ACTIVE",
          "OTP_TERMS_CHANGED",
          "PRESENTED_CONTENT_HASH_MISMATCH",
          "CONSENT_STATEMENT_OUTDATED",
        ].includes(errorCode ?? "")
      )
        this.challenge = null
      throw error
    } finally {
      this.busy = false
    }
  }
}
