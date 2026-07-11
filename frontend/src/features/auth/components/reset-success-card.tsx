import { Button } from "@/shared/ui/button"

function ResetSuccessCard({ onLogin }: { onLogin: () => void }) {
  return (
    <div className="w-[480px] bg-surface rounded-auth-card shadow-auth-card p-11">
      {/* Icon */}
      <div className="mb-5 flex size-14 items-center justify-center rounded-[14px] bg-[#DCFCE7]">
        <span className="ms text-[30px] text-[#16A34A]">check</span>
      </div>

      <div className="mb-2 text-[22px] font-medium text-text-primary">Đã đặt lại mật khẩu</div>
      <div className="mb-[22px] text-sm leading-[1.65] text-text-secondary">
        Mật khẩu của bạn đã được cập nhật. Mọi phiên đăng nhập cũ đã bị đăng xuất để bảo vệ tài
        khoản. Đăng nhập lại bằng mật khẩu mới.
      </div>

      <Button
        type="button"
        onClick={onLogin}
        className="h-[46px] w-full rounded-[9px] text-[15px]"
      >
        Đăng nhập
      </Button>
    </div>
  )
}

export { ResetSuccessCard }
