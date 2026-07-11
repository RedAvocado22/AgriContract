import { Link } from "react-router-dom"

function PendingApprovalCard() {
  return (
    <div className="w-[520px] bg-surface rounded-auth-card shadow-auth-card p-11">
      {/* Icon */}
      <div className="mb-5 flex size-14 items-center justify-center rounded-[14px] bg-warning-tint">
        <span className="ms text-[30px] text-warning">hourglass_top</span>
      </div>

      <div className="mb-2 text-[22px] font-medium text-text-primary">Hồ sơ đang chờ duyệt</div>
      <div className="mb-6 text-sm leading-[1.65] text-text-secondary">
        Quản trị viên đang xem xét hồ sơ của bạn. Tài khoản chưa được duyệt nên chưa thể tìm
        nguồn hàng, tạo hợp đồng hay nạp ký quỹ. Bạn sẽ nhận thông báo qua email và số điện
        thoại khi hồ sơ được duyệt.
      </div>

      {/* 3-step tracker */}
      <div className="mb-6 flex flex-col gap-3">
        <div className="flex items-center gap-2.5">
          <span className="ms text-[20px] text-success">check_circle</span>
          <span className="text-[13.5px] text-text-secondary">Đã nhận hồ sơ đăng ký</span>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="ms text-[20px] text-warning">radio_button_checked</span>
          <span className="text-[13.5px] text-text-secondary">Đang chờ quản trị viên duyệt</span>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="ms text-[20px] text-text-muted">radio_button_unchecked</span>
          <span className="text-[13.5px] text-text-muted">Kích hoạt tài khoản</span>
        </div>
      </div>

      {/* Hint */}
      <div className="mb-6 flex items-start gap-2.5 rounded-[9px] bg-surface-muted px-3.5 py-3">
        <span className="ms mt-px text-[19px] text-text-secondary">mail</span>
        <span className="text-[12.5px] leading-[1.5] text-text-secondary">
          Cần bổ sung giấy tờ? Chúng tôi sẽ liên hệ trực tiếp. Bạn có thể đăng xuất và quay
          lại sau.
        </span>
      </div>

      <div className="text-center text-sm">
        <Link to="/" className="text-primary hover:underline cursor-pointer">
          ← Quay lại trang chủ
        </Link>
      </div>
    </div>
  )
}

export { PendingApprovalCard }
