import type { ReactNode } from "react"
import { Navigate, Outlet } from "react-router-dom"
import { useOrganizationProfile } from "../hooks/use-organization-profile"
import { useOrganizationPermissions } from "../hooks/use-organization-permissions"
import { ProfileError, ProfileLoading } from "./profile-state"
import { Banner } from "@/shared/ui/banner"
import { OrganizationLayout } from "./organization-layout"

// Mounted inside Phase B RequireAuth. Pending/rejected profiles may still view pages.
export function RequireOrganizationProfile() {
  const profile = useOrganizationProfile()
  if (profile.isPending)
    return (
      <OrganizationLayout title="Cài đặt tài khoản">
        <ProfileLoading />
      </OrganizationLayout>
    )
  if (profile.isError && !profile.data)
    return (
      <OrganizationLayout title="Cài đặt tài khoản">
        <ProfileError
          error={profile.error}
          retry={() => void profile.refetch()}
        />
      </OrganizationLayout>
    )
  if (profile.data === null) return <Navigate to="/onboarding" replace />
  return <Outlet />
}

// For future create/sign routes and actions only, never blanket-block read routes.
export function RequireVerifiedOrganization({
  children,
}: {
  children: ReactNode
}) {
  const { canCreateOrSign, profile } = useOrganizationPermissions()
  if (profile.isPending) return <ProfileLoading />
  if (!canCreateOrSign)
    return (
      <Banner variant="warning">
        Chỉ tài khoản có hồ sơ đã xác minh và vai trò phù hợp mới được tạo hoặc
        ký hợp đồng.
      </Banner>
    )
  return children
}
