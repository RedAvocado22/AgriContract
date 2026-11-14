import type { ReactNode } from "react"
import { Link, useLocation } from "react-router-dom"
import { AppSidebar } from "@/shared/ui/app-sidebar"
import { TopBar } from "@/shared/ui/top-bar"
import { AccountMenu } from "@/features/auth/components/account-menu"
import { useOrganizationProfile } from "@/features/organization/hooks/use-organization-profile"

export function ContractsLayout({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  const profile = useOrganizationProfile()
  const { pathname } = useLocation()
  return (
    <div className="mx-auto flex min-h-screen max-w-[1440px] flex-col gap-4 p-3 md:flex-row md:gap-6 md:p-6">
      <AppSidebar
        className="w-full md:w-60"
        organizationName={profile.data?.organizationName ?? undefined}
        items={[
          {
            label: "Hợp đồng của tôi",
            icon: "description",
            href: "/contracts",
            active: pathname.startsWith("/contracts"),
          },
          { label: "Sàn nguồn hàng", icon: "storefront", href: "/listing" },
          { label: "Hồ sơ doanh nghiệp", icon: "domain", href: "/account" },
        ]}
      />
      <div className="min-w-0 flex-1 space-y-5">
        <TopBar
          avatar={<AccountMenu />}
          searchPlaceholder={
            profile.data?.organizationName ?? "Hợp đồng của bạn"
          }
          className="h-auto min-h-16 flex-wrap py-3"
        />
        <main className="min-w-0 space-y-5">
          <Link to="/contracts" className="text-sm text-primary">
            Hợp đồng
          </Link>
          <h1 className="text-2xl">{title}</h1>
          {children}
        </main>
      </div>
    </div>
  )
}
