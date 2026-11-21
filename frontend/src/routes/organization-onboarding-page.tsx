import { Navigate } from "react-router-dom"
import { OrganizationLayout } from "@/features/organization/components/organization-layout"
import { OrganizationForm } from "@/features/organization/components/organization-form"
import {
  ProfileError,
  ProfileLoading,
} from "@/features/organization/components/profile-state"
import { useOrganizationProfile } from "@/features/organization/hooks/use-organization-profile"

export function OrganizationOnboardingPage() {
  const profile = useOrganizationProfile()
  if (profile.data) return <Navigate to="/account" replace />
  return (
    <OrganizationLayout title="Hoàn tất hồ sơ doanh nghiệp" onboarding>
      {profile.isPending ? (
        <ProfileLoading />
      ) : profile.isError ? (
        <ProfileError
          error={profile.error}
          retry={() => void profile.refetch()}
        />
      ) : profile.data === null ? (
        <OrganizationForm />
      ) : null}
    </OrganizationLayout>
  )
}
