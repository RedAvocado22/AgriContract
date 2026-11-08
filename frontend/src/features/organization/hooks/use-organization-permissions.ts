import { useAuthStore, type AppRole } from "@/shared/auth/auth-store"
import { useOrganizationProfile } from "./use-organization-profile"
import type { OrganizationProfile } from "../api/profile"

export function canUseContractActions(
  profile: OrganizationProfile | null | undefined,
  roles: readonly AppRole[]
) {
  return (
    profile?.verificationStatus === "VERIFIED" &&
    (profile.role === "BUYER" || profile.role === "SELLER") &&
    roles.includes(profile.role)
  )
}

export function useOrganizationPermissions() {
  const profile = useOrganizationProfile()
  const { roles, authenticated } = useAuthStore()
  return {
    canCreateOrSign:
      authenticated &&
      !profile.isError &&
      !profile.isFetching &&
      canUseContractActions(profile.data, roles),
    profile,
  }
}
