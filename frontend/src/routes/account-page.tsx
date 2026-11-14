import { useState } from "react"
import { useLocation } from "react-router-dom"
import { useAuthStore } from "@/shared/auth/auth-store"
import { useFreshLogin } from "@/features/auth/hooks/use-fresh-login"
import { useOrganizationProfile } from "@/features/organization/hooks/use-organization-profile"
import { OrganizationLayout } from "@/features/organization/components/organization-layout"
import { OrganizationProfile } from "@/features/organization/components/organization-profile"
import { OrganizationDocuments } from "@/features/organization/components/organization-documents"
import {
  ProfileError,
  ProfileLoading,
} from "@/features/organization/components/profile-state"
import { Banner } from "@/shared/ui/banner"
import { Button } from "@/shared/ui/button"

export function AccountPage() {
  const { authTime } = useAuthStore()
  const profile = useOrganizationProfile()
  const { pathname } = useLocation()
  const { ensureFreshLogin, pendingIntent, consumeIntent } = useFreshLogin()
  const [confirmed, setConfirmed] = useState(false)
  const [error, setError] = useState(false)
  return (
    <OrganizationLayout
      title={
        pathname === "/workspace" ? "Không gian làm việc" : "Cài đặt tài khoản"
      }
    >
      {profile.isPending && <ProfileLoading />}
      {profile.isError && (
        <ProfileError
          error={profile.error}
          retry={() => void profile.refetch()}
        />
      )}
      {profile.data && <OrganizationProfile profile={profile.data} />}
      {pathname === "/workspace" && (
        <p className="text-sm text-text-secondary">
          Các chức năng nghiệp vụ sẽ được bổ sung ở giai đoạn tiếp theo.
        </p>
      )}
      <OrganizationDocuments />
      <section
        id="account-security"
        className="flex flex-col gap-4 rounded-md border border-border bg-surface p-5 sm:p-6"
      >
        <h2 className="text-base">Bảo mật tài khoản</h2>
        {!authTime && (
          <Banner variant="warning">
            Phiên hiện tại thiếu thời điểm xác thực. Cần đăng nhập lại trước
            thao tác yêu cầu xác thực mới.
          </Banner>
        )}
        {pendingIntent?.action === "confirm-session" ? (
          <Banner
            variant="success"
            actionLabel="Hoàn tất"
            onAction={() => {
              consumeIntent("confirm-session")
              setConfirmed(true)
            }}
          >
            Đã xác thực lại. Bạn có thể tiếp tục bước xác nhận phiên.
          </Banner>
        ) : (
          <Button
            className="self-start"
            variant="outline"
            onClick={() =>
              void ensureFreshLogin(
                { action: "confirm-session", step: "confirm" },
                true
              ).catch(() => setError(true))
            }
          >
            Xác thực lại
          </Button>
        )}
        {confirmed && (
          <p role="status">Đã hoàn tất xác nhận phiên đăng nhập.</p>
        )}
        {error && (
          <Banner variant="danger">
            Không thể xác thực lại. Vui lòng thử lại.
          </Banner>
        )}
      </section>
    </OrganizationLayout>
  )
}
