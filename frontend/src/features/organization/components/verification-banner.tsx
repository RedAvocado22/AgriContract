import { Banner } from "@/shared/ui/banner"

export function VerificationBanner({ status }: { status: string | null }) {
  if (status === "VERIFIED") return null
  return (
    <Banner variant={status === "REJECTED" ? "danger" : "warning"}>
      {status === "PENDING"
        ? "Hồ sơ đang chờ Operator xác minh. Bạn vẫn có thể xem thông tin, nhưng chưa được tạo hoặc ký hợp đồng."
        : status === "REJECTED"
          ? "Hồ sơ chưa được chấp thuận. Vui lòng liên hệ Operator để được hướng dẫn. Bạn vẫn có thể xem thông tin, nhưng chưa được tạo hoặc ký hợp đồng."
          : "Chưa xác định được trạng thái xác minh. Bạn vẫn có thể xem thông tin; chức năng tạo và ký hợp đồng tạm thời bị khóa."}
    </Banner>
  )
}
