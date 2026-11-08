import axios from "axios"
import { z } from "zod"
import { apiClient } from "@/shared/api/client"
import { parseApiError } from "@/shared/api/errors"
import type { AppRole } from "@/shared/auth/auth-store"

const optionalText = z
  .string()
  .nullish()
  .transform((value) => value?.trim() || null)
const profileSchema = z.object({
  userId: z.string().min(1),
  organizationName: optionalText,
  email: optionalText,
  phone: optionalText,
  address: optionalText,
  role: optionalText,
  verificationStatus: optionalText,
  authorizationExpiresAt: optionalText,
})
export type OrganizationProfile = z.infer<typeof profileSchema>
export const organizationFormSchema = z.object({
  role: z.enum(["BUYER", "SELLER"], { error: "Vui lòng chọn loại tài khoản." }),
  organizationName: z.string().trim().min(1, "Vui lòng nhập tên doanh nghiệp."),
  phone: z.string().trim(),
  address: z.string().trim(),
})
export type OrganizationFormValues = z.infer<typeof organizationFormSchema>

export class ProfileResponseError extends Error {}
export class RegistrationRoleError extends Error {}

export function parseProfile(
  data: unknown,
  userId: string
): OrganizationProfile {
  const result = profileSchema.safeParse(data)
  if (!result.success || result.data.userId !== userId) {
    throw new ProfileResponseError(
      "Phản hồi hồ sơ không đúng định dạng hoặc không thuộc tài khoản hiện tại."
    )
  }
  return result.data
}

export function isMissingProfile(error: unknown, userId: string): boolean {
  if (!axios.isAxiosError(error)) return false
  const { response, config } = error
  // Exact user-service signature, bound to this endpoint and authenticated actor.
  // A generic Gateway/HTML 404 must remain an error, never trigger registration.
  return (
    config?.url === "/api/v1/users/me" &&
    config.method?.toLowerCase() === "get" &&
    response?.status === 404 &&
    response.data?.success === false &&
    response.data?.data == null &&
    response.data?.error === `User not found: ${userId}`
  )
}

export async function getOrganizationProfile(
  userId: string,
  signal?: AbortSignal
): Promise<OrganizationProfile | null> {
  try {
    const { data } = await apiClient.get("/api/v1/users/me", { signal })
    return parseProfile(data, userId)
  } catch (error) {
    if (isMissingProfile(error, userId)) return null
    throw error
  }
}

export function registrationRoleProblem(
  roles: readonly AppRole[],
  selected?: string
) {
  // Backend picks the first recognized role; multiple roles are ambiguous.
  if (roles.length !== 1 || !["BUYER", "SELLER"].includes(roles[0])) {
    return "Tài khoản cần được quản trị viên cấp đúng một vai trò Bên mua hoặc Bên bán trước khi tạo hồ sơ. Lựa chọn trên form không tự cấp quyền."
  }
  if (selected && selected !== roles[0])
    return "Loại tài khoản đã chọn không khớp vai trò đăng nhập. Vui lòng chọn đúng vai trò hoặc liên hệ quản trị viên."
  return null
}

export function registrationRequest(
  values: OrganizationFormValues,
  roles: readonly AppRole[]
) {
  const parsed = organizationFormSchema.parse(values)
  const problem = registrationRoleProblem(roles, parsed.role)
  if (problem) throw new RegistrationRoleError(problem)
  // RegisterUserRequest accepts these three fields only. Role comes from JWT.
  return {
    organizationName: parsed.organizationName,
    phone: parsed.phone || null,
    address: parsed.address || null,
  }
}

export async function registerOrganization(
  values: OrganizationFormValues,
  userId: string,
  roles: readonly AppRole[]
) {
  const { data } = await apiClient.post(
    "/api/v1/users/register",
    registrationRequest(values, roles)
  )
  return parseProfile(data, userId)
}

export function organizationErrorMessage(error: unknown) {
  if (
    error instanceof ProfileResponseError ||
    error instanceof RegistrationRoleError
  )
    return error.message
  const parsed = parseApiError(error)
  if (parsed.status === 401)
    return "Phiên đăng nhập cần được xác thực lại. Đang chuyển đến hệ thống đăng nhập."
  if (parsed.status === 403)
    return "Tài khoản chưa được phép thực hiện yêu cầu này. Vui lòng liên hệ quản trị viên."
  if (parsed.status === 404)
    return "Không xác định được nguồn lỗi không tìm thấy. Chưa thể kết luận tài khoản thiếu hồ sơ. Vui lòng thử lại hoặc liên hệ quản trị viên."
  if (parsed.status === 400)
    return "Máy chủ chưa chấp nhận thông tin hồ sơ. Vui lòng kiểm tra dữ liệu và vai trò tài khoản."
  if (parsed.status === 409)
    return "Hồ sơ có thay đổi hoặc xung đột. Vui lòng tải lại thông tin trước khi tiếp tục."
  if (parsed.status >= 500)
    return "Dịch vụ hồ sơ tạm thời không khả dụng. Vui lòng thử lại sau."
  return parsed.message
}
