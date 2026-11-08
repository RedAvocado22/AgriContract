import { useState } from "react"
import { useLocation } from "react-router-dom"
import { useAuthStore, ROLE_LABELS } from "@/shared/auth/auth-store"
import { useMyProfile } from "@/features/auth/hooks/use-my-profile"
import { useFreshLogin } from "@/features/auth/hooks/use-fresh-login"
import { AccountMenu } from "@/features/auth/components/account-menu"
import { parseApiError } from "@/shared/api/errors"
import { TopBar } from "@/shared/ui/top-bar"
import { AppSidebar, type SidebarItem } from "@/shared/ui/app-sidebar"
import { Banner } from "@/shared/ui/banner"
import { Button } from "@/shared/ui/button"

export function AccountPage() {
  const { email, roles, authTime } = useAuthStore()
  const profile = useMyProfile()
  const { pathname } = useLocation()
  const { ensureFreshLogin, pendingIntent, consumeIntent } = useFreshLogin()
  const [confirmed, setConfirmed] = useState(false)
  const [error, setError] = useState(false)
  const items: SidebarItem[] = [
    {
      label: "Tài khoản đăng nhập",
      icon: "person",
      href: "/account",
      active: pathname === "/account",
    },
  ]
  if (roles.length)
    items.push({
      label: "Không gian làm việc",
      icon: "dashboard",
      href: "/workspace",
      active: pathname === "/workspace",
    })
  if (roles.some((role) => role === "BUYER" || role === "SELLER"))
    items.push({
      label: "Sàn nguồn hàng",
      icon: "storefront",
      href: "/listing",
    })
  items.push({ label: "Trang chủ", icon: "home", href: "/" })
  return (
    <div className="mx-auto flex min-h-screen max-w-[1440px] flex-col gap-4 p-4 md:flex-row md:p-6">
      <AppSidebar
        items={items}
        organizationName={profile.data?.organizationName}
        className="w-full md:w-60"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        <TopBar
          avatar={<AccountMenu />}
          searchPlaceholder={
            profile.data?.organizationName || "Tài khoản AgriContract"
          }
          className="h-auto min-h-16 flex-wrap py-3"
        />
        <main className="flex flex-col gap-4 rounded-md border border-border bg-surface p-5">
          <h1 className="text-xl">
            {pathname === "/workspace"
              ? "Không gian làm việc"
              : "Tài khoản đăng nhập"}
          </h1>
          <p className="break-all">
            {email || "Tài khoản chưa có email trong phiên đăng nhập."}
          </p>
          <p>
            Vai trò:{" "}
            {roles.map((role) => ROLE_LABELS[role]).join(", ") ||
              "Chưa được cấp vai trò"}
          </p>
          {profile.isPending && <p role="status">Đang tải tên tổ chức…</p>}
          {profile.isError && (
            <Banner
              variant="warning"
              actionLabel="Tải lại"
              onAction={() => void profile.refetch()}
            >
              {parseApiError(profile.error).status === 404
                ? "Tài khoản chưa có hồ sơ tổ chức."
                : "Chưa tải được hồ sơ tổ chức từ máy chủ."}
            </Banner>
          )}
          {!authTime && (
            <Banner variant="warning">
              Phiên hiện tại thiếu thời điểm xác thực. Cần đăng nhập lại trước
              thao tác yêu cầu xác thực mới.
            </Banner>
          )}
          {pathname === "/workspace" && (
            <p className="text-sm text-text-secondary">
              Các chức năng nghiệp vụ sẽ được bổ sung ở giai đoạn tiếp theo.
            </p>
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
        </main>
      </div>
    </div>
  )
}
