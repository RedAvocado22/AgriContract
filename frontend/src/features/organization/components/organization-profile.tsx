import { ROLE_LABELS, type AppRole } from "@/shared/auth/auth-store"
import { StatusBadge } from "@/shared/ui/status-badge"
import { EmptyState } from "@/shared/ui/empty-state"
import { VerificationBanner } from "./verification-banner"
import type { OrganizationProfile as Profile } from "../api/profile"

const verificationLabels: Record<string, string> = {
  PENDING: "Chờ xác minh",
  VERIFIED: "Đã xác minh",
  REJECTED: "Chưa được chấp thuận",
}

export function OrganizationProfile({ profile }: { profile: Profile }) {
  const values = [
    ["Tên doanh nghiệp", profile.organizationName],
    ["Email", profile.email],
    ["Vai trò", ROLE_LABELS[profile.role as AppRole] || null],
    ["Số điện thoại", profile.phone],
    ["Địa chỉ", profile.address],
    [
      "Trạng thái xác minh",
      verificationLabels[profile.verificationStatus ?? ""] || "Chưa xác định",
    ],
  ]
  return (
    <>
      <VerificationBanner status={profile.verificationStatus} />
      <section
        id="organization-profile"
        className="space-y-5 rounded-md border border-border bg-surface p-5 sm:p-6"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base">Hồ sơ doanh nghiệp</h2>
          <StatusBadge
            variant={
              profile.verificationStatus === "VERIFIED"
                ? "success"
                : profile.verificationStatus === "REJECTED"
                  ? "danger"
                  : "warning"
            }
          >
            {verificationLabels[profile.verificationStatus ?? ""] ||
              "Chưa xác định"}
          </StatusBadge>
        </div>
        <p className="text-sm text-text-secondary">
          Thông tin hồ sơ được cung cấp bởi hệ thống. Liên hệ Operator nếu cần
          cập nhật.
        </p>
        {!profile.organizationName && (
          <EmptyState
            title="Hồ sơ chưa đủ thông tin"
            description="Hệ thống đã có hồ sơ nhưng chưa cung cấp tên tổ chức. Vui lòng liên hệ Operator; không cần đăng ký lại."
          />
        )}
        <dl className="grid gap-4 sm:grid-cols-2">
          {values.map(([label, value]) => (
            <div
              key={label}
              className={label === "Địa chỉ" ? "sm:col-span-2" : ""}
            >
              <dt className="mb-1.5 text-xs text-text-secondary">{label}</dt>
              <dd className="min-h-11 break-words rounded-sm border border-border bg-page-bg px-3 py-2.5 text-sm">
                {value || "Chưa có thông tin"}
              </dd>
            </div>
          ))}
        </dl>
      </section>
    </>
  )
}
