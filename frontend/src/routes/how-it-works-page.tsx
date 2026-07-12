import { Link } from "react-router-dom"

import { cn } from "@/shared/lib/utils"
import { buttonVariants } from "@/shared/ui/button"
import { Footer } from "@/shared/ui/footer"
import { PublicNav } from "@/shared/ui/public-nav"

function HowItWorksPage() {
  return (
    <div className="flex min-h-screen flex-col bg-page-bg text-text-primary">
      <title>Cách hoạt động — AgriContract</title>
      <PublicNav />

      {/* HERO */}
      <div className="border-b-[0.5px] border-border bg-surface">
        <div className="mx-auto max-w-[1160px] px-10 py-16">
          <div className="mb-3.5 text-[13px] font-medium uppercase tracking-[0.04em] text-primary">
            Cách hoạt động
          </div>
          <div className="mb-4 max-w-[820px] text-balance text-[40px] font-medium leading-[1.2] tracking-[-0.015em]">
            Sáu bước từ lúc bắt tay đến khi tiền về tài khoản
          </div>
          <div className="max-w-[680px] text-[17px] leading-[1.65] text-text-secondary">
            Không có thuật ngữ khó hiểu. Mỗi hợp đồng đi qua một vòng đời rõ ràng, và ở mỗi bước
            đều có bằng chứng — nên không ai có thể lật kèo giữa chừng.
          </div>
        </div>
      </div>

      {/* 6 STEPS */}
      <div className="mx-auto max-w-[1160px] px-10 pt-14 pb-5 w-full">
        <div className="mb-6 text-[22px] font-medium">Vòng đời một hợp đồng, theo đúng thứ tự</div>
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
          {/* CARD 1 */}
          <div className="relative overflow-hidden rounded-2xl border-[0.5px] border-border bg-surface px-6 py-6">
            <div className="absolute right-5 top-3.5 font-mono text-[56px] font-semibold leading-none tracking-[-0.02em] text-[#EAF7EF]">
              01
            </div>
            <div className="relative mb-4 flex h-[46px] w-[46px] items-center justify-center rounded-xl bg-primary-tint">
              <span className="ms text-[24px] text-primary">forum</span>
            </div>
            <div className="relative mb-2 text-[18px] font-medium">Thoả thuận điều khoản</div>
            <div className="relative text-sm leading-[1.65] text-text-secondary">
              Hai bên đàm phán khối lượng, giá và mức phạt vi phạm ngay trên nền tảng. Có dải giới
              hạn cho từng con số, nên không bên nào ép giá được bên kia.
            </div>
          </div>

          {/* CARD 2 */}
          <div className="relative overflow-hidden rounded-2xl border-[0.5px] border-border bg-surface px-6 py-6">
            <div className="absolute right-5 top-3.5 font-mono text-[56px] font-semibold leading-none tracking-[-0.02em] text-[#EAF7EF]">
              02
            </div>
            <div className="relative mb-4 flex h-[46px] w-[46px] items-center justify-center rounded-xl bg-primary-tint">
              <span className="ms text-[24px] text-primary">lock</span>
            </div>
            <div className="relative mb-2 text-[18px] font-medium">Ký & khoá tiền</div>
            <div className="relative mb-3.5 text-sm leading-[1.65] text-text-secondary">
              Ký điện tử xác thực bằng OTP. Tiền của bên mua chuyển vào tài khoản trung gian độc
              lập — không vào túi bên nào, chỉ giải ngân theo quy tắc đã ký.
            </div>
            <div className="relative inline-flex items-center gap-2 rounded-lg bg-surface-muted px-3 py-2 text-[12.5px] text-text-secondary">
              <span className="ms text-[15px] text-primary">shield</span> Tiền được khoá{" "}
              <b className="font-medium text-text-primary">trước</b> khi giao hàng
            </div>
          </div>

          {/* CARD 3 */}
          <div className="relative overflow-hidden rounded-2xl border-[0.5px] border-border bg-surface px-6 py-6">
            <div className="absolute right-5 top-3.5 font-mono text-[56px] font-semibold leading-none tracking-[-0.02em] text-[#EAF7EF]">
              03
            </div>
            <div className="relative mb-4 flex h-[46px] w-[46px] items-center justify-center rounded-xl bg-primary-tint">
              <span className="ms text-[24px] text-primary">local_shipping</span>
            </div>
            <div className="relative mb-2 text-[18px] font-medium">Giao hàng theo từng đợt</div>
            <div className="relative text-sm leading-[1.65] text-text-secondary">
              Mỗi đợt giao đều được cân đo và chụp ảnh làm bằng chứng. Không phải chờ đến cuối hợp
              đồng mới xác nhận — giao đến đâu, ghi nhận đến đó.
            </div>
          </div>

          {/* CARD 4 */}
          <div className="relative overflow-hidden rounded-2xl border-[0.5px] border-border bg-surface px-6 py-6">
            <div className="absolute right-5 top-3.5 font-mono text-[56px] font-semibold leading-none tracking-[-0.02em] text-[#EAF7EF]">
              04
            </div>
            <div className="relative mb-4 flex h-[46px] w-[46px] items-center justify-center rounded-xl bg-primary-tint">
              <span className="ms text-[24px] text-primary">verified</span>
            </div>
            <div className="relative mb-2 text-[18px] font-medium">Kiểm định độc lập khi cần</div>
            <div className="relative text-sm leading-[1.65] text-text-secondary">
              Khi hai bên không thống nhất về chất lượng, một đơn vị giám định thứ ba (Vinacontrol,
              SGS…) vào cuộc xác nhận. Không bên nào tự phán kết quả.
            </div>
          </div>

          {/* CARD 5 */}
          <div className="relative overflow-hidden rounded-2xl border-[0.5px] border-border bg-surface px-6 py-6">
            <div className="absolute right-5 top-3.5 font-mono text-[56px] font-semibold leading-none tracking-[-0.02em] text-[#EAF7EF]">
              05
            </div>
            <div className="relative mb-4 flex h-[46px] w-[46px] items-center justify-center rounded-xl bg-primary-tint">
              <span className="ms text-[24px] text-primary">payments</span>
            </div>
            <div className="relative mb-2 text-[18px] font-medium">Giải ngân ngay khi xác nhận</div>
            <div className="relative text-sm leading-[1.65] text-text-secondary">
              Tiền chuyển cho bên bán tự động ngay khi cột mốc hoàn thành, tính đúng phần đã giao —
              không phải đợi hết hợp đồng mới được thanh toán.
            </div>
          </div>

          {/* CARD 6 */}
          <div className="relative overflow-hidden rounded-2xl border-[0.5px] border-border bg-surface px-6 py-6">
            <div className="absolute right-5 top-3.5 font-mono text-[56px] font-semibold leading-none tracking-[-0.02em] text-[#EAF7EF]">
              06
            </div>
            <div className="relative mb-4 flex h-[46px] w-[46px] items-center justify-center rounded-xl bg-primary-tint">
              <span className="ms text-[24px] text-primary">gavel</span>
            </div>
            <div className="relative mb-2 text-[18px] font-medium">
              Tranh chấp có lối thoát rõ ràng
            </div>
            <div className="relative mb-3.5 text-sm leading-[1.65] text-text-secondary">
              Luôn có nơi phân xử, không rơi vào bế tắc. Tranh chấp leo qua ba tầng, tầng sau độc
              lập hơn tầng trước:
            </div>
            <div className="relative flex flex-col gap-2">
              <div className="flex items-center gap-2.5 text-[13px] text-text-primary">
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-surface-muted font-mono text-[11px] text-text-secondary">
                  1
                </span>{" "}
                Xử lý nội tuyến
              </div>
              <div className="flex items-center gap-2.5 text-[13px] text-text-primary">
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-surface-muted font-mono text-[11px] text-text-secondary">
                  2
                </span>{" "}
                Vinacontrol / Quatest
              </div>
              <div className="flex items-center gap-2.5 text-[13px] text-text-primary">
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-surface-muted font-mono text-[11px] text-text-secondary">
                  3
                </span>{" "}
                SGS / Bureau Veritas
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* TRUST STRIP */}
      <div className="border-y-[0.5px] border-border bg-surface-muted mt-8">
        <div className="mx-auto grid max-w-[1160px] grid-cols-1 gap-6 px-10 py-12 md:grid-cols-3">
          <div className="flex items-start gap-3.5">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border-[0.5px] border-border bg-surface">
              <span className="ms text-[22px] text-primary">receipt_long</span>
            </span>
            <div>
              <div className="mb-1 text-[15.5px] font-medium">Sổ cái minh bạch không sửa được</div>
              <div className="text-[13.5px] leading-[1.6] text-text-secondary">
                Mọi bút toán chỉ ghi thêm — không có nút sửa hay xoá ở bất kỳ đâu.
              </div>
            </div>
          </div>
          <div className="flex items-start gap-3.5">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border-[0.5px] border-border bg-surface">
              <span className="ms text-[22px] text-primary">sync_alt</span>
            </span>
            <div>
              <div className="mb-1 text-[15.5px] font-medium">Uy tín 2 chiều</div>
              <div className="text-[13.5px] leading-[1.6] text-text-secondary">
                Cả bên mua và bên bán đều có lịch sử công khai để đối tác xem trước khi ký.
              </div>
            </div>
          </div>
          <div className="flex items-start gap-3.5">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border-[0.5px] border-border bg-surface">
              <span className="ms text-[22px] text-primary">eco</span>
            </span>
            <div>
              <div className="mb-1 text-[15.5px] font-medium">Tuân thủ EUDR</div>
              <div className="text-[13.5px] leading-[1.6] text-text-secondary">
                Cà phê và cao su khai vùng trồng, sẵn sàng cho quy định chống phá rừng của EU.
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* CTA */}
      <div className="mx-auto max-w-[1160px] px-10 py-16 text-center">
        <div className="mb-3 text-[28px] font-medium">Sẵn sàng giao dịch có bảo chứng?</div>
        <div className="mb-7 text-[15px] text-text-secondary">
          Mở tài khoản miễn phí. Quản trị viên duyệt hồ sơ trước khi kích hoạt.
        </div>
        <div className="flex justify-center gap-3">
          <Link
            to="/register"
            className={cn(buttonVariants({ size: "cta" }))}
          >
            Bắt đầu miễn phí
          </Link>
          <Link
            to="/escrow"
            className={cn(buttonVariants({ variant: "outline", size: "cta" }), "gap-2 border-[0.5px]")}
          >
            Tìm hiểu về ký quỹ <span className="ms text-[18px] text-primary">arrow_forward</span>
          </Link>
        </div>
      </div>

      <Footer />
    </div>
  )
}

export { HowItWorksPage }
