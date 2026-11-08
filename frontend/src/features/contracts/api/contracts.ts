import { z } from "zod"
import { apiClient } from "@/shared/api/client"
import { unwrapApiResponse } from "@/shared/api/errors"
import type {
  Contract,
  ContractPage,
  Fields,
  ParticipantRole,
} from "../types/contract"

const fields = z.record(z.string(), z.unknown())
const wire = z.object({
  contractId: z.string().min(1),
  listingId: z.string().min(1),
  buyerId: z.string().min(1),
  sellerId: z.string().min(1),
  status: z.string().min(1),
  terms: fields.nullable(),
  buyerOrgName: z.string().nullish(),
  sellerOrgName: z.string().nullish(),
  productName: z.string().nullish(),
  signedContentHash: z
    .string()
    .regex(/^[a-f0-9]{64}$/)
    .nullish(),
  reservedQuantity: z.union([z.number().finite(), z.string()]).nullish(),
  milestones: z.array(fields).optional(),
})

export function mapContract(value: unknown, userId: string): Contract {
  const raw = unwrapApiResponse(value)
  const data = wire.parse(raw)
  if (data.buyerId !== userId && data.sellerId !== userId)
    throw new Error("Bạn không phải là một bên của hợp đồng này.")
  const coffee = Object.prototype.hasOwnProperty.call(raw, "signedContentHash")
  return {
    id: data.contractId,
    listingId: data.listingId,
    buyerId: data.buyerId,
    sellerId: data.sellerId,
    buyerName: data.buyerOrgName || undefined,
    sellerName: data.sellerOrgName || undefined,
    productName: data.productName || undefined,
    status: data.status,
    generation: coffee ? "coffee" : "legacy",
    terms: data.terms ?? {},
    hash: data.signedContentHash ?? null,
    reservedQuantity:
      data.reservedQuantity == null ? undefined : String(data.reservedQuantity),
    milestones: data.milestones ?? [],
    editability: "unknown",
  }
}
export function mapContractPage(
  value: unknown,
  userId: string,
  role: ParticipantRole
): ContractPage {
  const data = z
    .object({
      content: z.array(z.unknown()),
      page: z.number().int().nonnegative(),
      totalPages: z.number().int().nonnegative(),
      totalElements: z.number().int().nonnegative(),
    })
    .parse(unwrapApiResponse(value))
  const content = data.content.map((item) => mapContract(item, userId))
  if (
    content.some(
      (item) => (role === "BUYER" ? item.buyerId : item.sellerId) !== userId
    )
  )
    throw new Error("Danh sách trả về không khớp vai trò đang chọn.")
  return { ...data, content }
}
const path = (id: string) => `/api/v1/contracts/${encodeURIComponent(id)}`
export async function listContracts(
  userId: string,
  role: ParticipantRole,
  status: string,
  page: number,
  signal?: AbortSignal
) {
  const response = await apiClient.get("/api/v1/contracts", {
    params: { role, status: status || undefined, page, size: 20 },
    signal,
  })
  return mapContractPage(response.data, userId, role)
}
export async function getContract(
  id: string,
  userId: string,
  signal?: AbortSignal
) {
  const response = await apiClient.get(path(id), { signal })
  const contract = mapContract(response.data, userId)
  if (contract.id !== id)
    throw new Error("Phản hồi không khớp mã hợp đồng yêu cầu.")
  return contract
}

// Exact allowlist from ContractTermsInput. Never send derived/template fields.
export const inputKeys = [
  "agreedPrice",
  "currency",
  "milestoneSchedule",
  "qualityTerms",
  "deliveryTerms",
  "inspectionTerms",
  "priceBand",
  "priceAdjustmentRule",
  "toleranceRate",
  "shortfallPenaltyThreshold",
  "buyerPenaltyRate",
  "sellerPenaltyRate",
  "forceMajeureReportWindowDays",
  "buyerConfirmWindowDays",
  "buyerDepositRate",
  "disputeFloorReleaseRate",
  "sellerDepositRate",
  "level2InspectorOrg",
  "legalProfile",
] as const
export function termsInput(snapshot: Fields, agreedPrice: string): Fields {
  const input: Fields = {}
  for (const key of inputKeys) {
    if (!(key in snapshot) && key !== "level2InspectorOrg")
      throw new Error("Điều khoản chưa đầy đủ; vui lòng tải lại.")
    input[key] = snapshot[key] ?? null
  }
  input.agreedPrice = agreedPrice
  return input
}
// Transport prepared for BE permission + stale-write precondition support; runtime UI stays blocked.
export async function patchTerms(
  id: string,
  input: Fields,
  key: string,
  userId: string
) {
  if (
    Object.keys(input).some(
      (field) => !inputKeys.includes(field as (typeof inputKeys)[number])
    )
  )
    throw new Error("Không được gửi trường do máy chủ quản lý.")
  const response = await apiClient.patch(`${path(id)}/terms`, input, {
    headers: { "Idempotency-Key": key },
  })
  const contract = mapContract(response.data, userId)
  if (contract.id !== id) throw new Error("Phản hồi không khớp hợp đồng.")
  return contract
}
