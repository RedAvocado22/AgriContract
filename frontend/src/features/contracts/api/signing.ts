import { z } from "zod"
import { apiClient } from "@/shared/api/client"
import type { Challenge, Consent, ParticipantRole } from "../types/contract"

export function validConsent(
  consent: Consent | null,
  id: string,
  hash: string | null,
  role: ParticipantRole | null
) {
  return !!(
    role &&
    hash &&
    /^[a-f0-9]{64}$/.test(hash) &&
    consent?.contractId === id &&
    consent.contentHash === hash &&
    consent.action === `SIGN_CONTRACT_AS_${role}` &&
    consent.version.trim() &&
    consent.text.trim()
  )
}
export async function initiateSigning(
  id: string,
  role: ParticipantRole,
  hash: string,
  consent: Consent | null,
  key: string
): Promise<Challenge> {
  if (!validConsent(consent, id, hash, role))
    throw new Error("Thiếu cam kết hợp lệ cho phiên bản hợp đồng này.")
  const response = await apiClient.post(
    `/api/v1/contracts/${encodeURIComponent(id)}/sign/initiate`,
    {
      signerRole: role,
      consentStatementVersion: consent!.version,
      presentedContentHash: hash,
    },
    { headers: { "Idempotency-Key": key } }
  )
  return z
    .object({
      otpId: z.string().uuid(),
      expiresAt: z.iso.datetime({ offset: true }),
    })
    .parse(response.data)
}
export async function verifySigning(
  id: string,
  challenge: Challenge,
  otpCode: string,
  key: string
) {
  if (Date.parse(challenge.expiresAt) <= Date.now())
    throw new Error("Mã OTP đã hết hạn. Vui lòng gửi mã mới.")
  if (!/^\d{6}$/.test(otpCode)) throw new Error("Nhập đủ 6 chữ số OTP.")
  const response = await apiClient.post(
    `/api/v1/contracts/${encodeURIComponent(id)}/sign/verify`,
    {
      otpId: challenge.otpId,
      otpCode,
    },
    { headers: { "Idempotency-Key": key } }
  )
  if (response.status !== 204)
    throw new Error(
      "Chưa nhận được xác nhận chữ ký từ máy chủ. Vui lòng tải lại hợp đồng."
    )
}
