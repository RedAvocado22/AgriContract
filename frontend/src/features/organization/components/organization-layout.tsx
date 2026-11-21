import type { ReactNode } from "react"
import { useLocation } from "react-router-dom"
import { useAuthStore } from "@/shared/auth/auth-store"
import { AccountMenu } from "@/features/auth/components/account-menu"
import { AppSidebar, type SidebarItem } from "@/shared/ui/app-sidebar"
import { TopBar } from "@/shared/ui/top-bar"
import { useOrganizationProfile } from "../hooks/use-organization-profile"

export function OrganizationLayout({
  title,
  children,
  onboarding = false,
}: {
  title: string
  children: ReactNode
  onboarding?: boolean
}) {
  const { roles } = useAuthStore()
  const { pathname } = useLocation()
  const profile = useOrganizationProfile()
  const items: SidebarItem[] = [
    {
      label: "Hồ sơ doanh nghiệp",
      icon: "domain",
      href: "/account",
      active: pathname === "/account" || onboarding,
    },
    ...(roles.length
      ? [
          {
            label: "Không gian làm việc",
            icon: "dashboard",
            href: "/workspace",
            active: pathname === "/workspace",
          },
        ]
      : []),
    ...(roles.some((role) => role === "BUYER" || role === "SELLER")
      ? [
          { label: "Hợp đồng của tôi", icon: "description", href: "/contracts" },
          { label: "Sàn nguồn hàng", icon: "storefront", href: "/listing" },
        ]
      : []),
    { label: "Trang chủ", icon: "home", href: "/" },
  ]
  return (
    <div className="mx-auto flex min-h-screen max-w-[1440px] flex-col gap-4 p-4 md:flex-row md:gap-6 md:p-6">
      <AppSidebar
        items={items}
        organizationName={profile.data?.organizationName ?? undefined}
        className="w-full md:w-60"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-6">
        <TopBar
          avatar={<AccountMenu />}
          searchPlaceholder={
            profile.data?.organizationName || "Tài khoản AgriContract"
          }
          className="h-auto min-h-16 flex-wrap py-3"
        />
        <main className="min-w-0">
          <h1 className="text-[22px]">{title}</h1>
          <p className="mt-1 mb-6 text-sm text-text-secondary">
            {onboarding
              ? "Bổ sung thông tin tổ chức để gửi hồ sơ xác minh."
              : profile.data?.organizationName ||
                "Thông tin hồ sơ và tài khoản của bạn."}
          </p>
          <div className="grid items-start gap-5 xl:grid-cols-[190px_minmax(0,1fr)]">
            <nav
              aria-label="Cài đặt tài khoản"
              className="flex flex-wrap gap-2 xl:flex-col"
            >
              <a
                href="#organization-profile"
                className="rounded-sm bg-primary-tint px-3 py-2.5 text-sm font-medium text-primary"
              >
                Hồ sơ &amp; pháp lý
              </a>
              <a
                href="#organization-documents-title"
                className="rounded-sm px-3 py-2.5 text-sm text-text-secondary"
              >
                Tài liệu doanh nghiệp
              </a>
              {!onboarding && (
                <a
                  href="#account-security"
                  className="rounded-sm px-3 py-2.5 text-sm text-text-secondary"
                >
                  Bảo mật
                </a>
              )}
            </nav>
            <div className="min-w-0 space-y-5">{children}</div>
          </div>
        </main>
      </div>
    </div>
  )
}
