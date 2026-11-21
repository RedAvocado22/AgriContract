// Compatibility entry point for Phase B consumers.
import { useAuthStore } from "@/shared/auth/auth-store"
import { getOrganizationProfile } from "@/features/organization/api/profile"
export type { OrganizationProfile as MyProfile } from "@/features/organization/api/profile"
export function getMyProfile(signal?: AbortSignal) {
  const userId = useAuthStore.getState().userId
  if (!userId) throw new Error("Bạn cần đăng nhập để đọc hồ sơ.")
  return getOrganizationProfile(userId, signal)
}
