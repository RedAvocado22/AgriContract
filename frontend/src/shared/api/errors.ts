import axios from "axios"

export interface ApiError {
  status: number
  code: string
  message: string
}

export function isEnvelope(
  value: unknown
): value is { success: boolean; data?: unknown; error?: unknown } {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    typeof value.success === "boolean" &&
    ("data" in value || "error" in value)
  )
}

export function parseApiError(error: unknown): ApiError {
  const response = axios.isAxiosError(error) ? error.response : undefined
  const body = response?.data
  const status = response?.status ?? 0
  if (body && typeof body === "object") {
    const raw =
      typeof body.error === "string" ? body.error : body.message ?? body.detail
    if (typeof raw === "string") {
      const colon = raw.indexOf(":")
      const prefix = colon < 0 ? "" : raw.slice(0, colon).trim()
      const code =
        typeof body.code === "string"
          ? body.code
          : /^[A-Z][A-Z0-9_]+$/.test(prefix)
            ? prefix
            : `HTTP_${status}`
      return {
        status,
        code,
        message: code === prefix ? raw.slice(colon + 1).trim() : raw,
      }
    }
    if (typeof body.code === "string")
      return { status, code: body.code, message: "Yêu cầu không thành công." }
  }
  return {
    status,
    code: status ? `HTTP_${status}` : "NETWORK_ERROR",
    message: status
      ? "Yêu cầu không thành công. Vui lòng thử lại."
      : "Không kết nối được máy chủ. Vui lòng kiểm tra kết nối và thử lại.",
  }
}

export function unwrapApiResponse<T>(
  body: T | { success: boolean; data?: T; error?: unknown }
): T {
  if (!isEnvelope(body)) return body as T
  if (!body.success)
    throw new Error(
      typeof body.error === "string" ? body.error : "Yêu cầu không thành công."
    )
  return body.data as T
}
