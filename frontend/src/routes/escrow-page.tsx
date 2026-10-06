import { Link } from "react-router-dom"

import { cn } from "@/shared/lib/utils"
import { buttonVariants } from "@/shared/ui/button"
import { Footer } from "@/shared/ui/footer"
import { PublicNav } from "@/shared/ui/public-nav"

function EscrowPage() {
  return (
    <div className="flex min-h-screen flex-col bg-page-bg text-text-primary">
      <title>Về ký quỹ — AgriContract</title>
      <PublicNav />

      {/* HERO */}
      <div className="border-b-[0.5px] border-border bg-surface">
        <div className="mx-auto max-w-[1160px] px-10 py-16 pb-[60px]">
          <div className="mb-3.5 text-[13px] font-medium uppercase tracking-[0.04em] text-primary">
            Về ký quỹ
          </div>
          <div className="mb-4 max-w-[820px] text-balance text-[40px] font-medium leading-[1.2] tracking-[-0.015em]">
            Ký quỹ tự thực thi là gì?
          </div>
          <div className="max-w-[720px] text-[17px] leading-[1.65] text-text-secondary">
            Tiền được khoá <b className="font-medium text-text-primary">trước</b> khi giao hàng và
            giải ngân tự động theo từng cột mốc. Không bên nào rút được ngoài quy tắc đã ký — quy
            tắc tự chạy, không cần ai bấm nút thủ công hay đặt niềm tin vào bên còn lại.
          </div>
        </div>
      </div>

      {/* MONEY-FLOW DIAGRAM */}
      <div className="mx-auto max-w-[1160px] px-10 pt-[60px] pb-6">
        <div className="mb-2.5 text-[13px] font-medium uppercase tracking-[0.04em] text-primary">
          Dòng tiền
        </div>
        <div className="mb-2 text-[24px] font-medium">Tiền đi đâu, ai giữ, khi nào chuyển</div>
        <div className="mb-9 max-w-[680px] text-[15px] leading-[1.6] text-text-secondary">
          Từ lúc khoá cho đến lúc tất toán, tiền luôn nằm ở một nơi trung lập và chỉ dịch chuyển
          theo quy tắc đã ký.
        </div>

        <div className="rounded-2xl border-[0.5px] border-border bg-surface px-8 py-9">
          <div className="grid grid-cols-[1fr_auto_1.15fr_auto_1.15fr] items-center gap-0">
            {/* COL 1: INPUTS */}
            <div className="flex flex-col gap-3.5">
              <div className="rounded-xl border-[0.5px] border-border bg-page-bg px-[18px] py-4">
                <div className="mb-2 flex items-center gap-2.5">
                  <span className="ms text-[20px] text-primary">storefront</span>
                  <span className="text-[14.5px] font-medium">Bên mua</span>
                </div>
                <div className="text-[12.5px] leading-[1.55] text-text-secondary">
                  Khoá <b className="font-medium text-text-primary">100% tiền hàng</b> vào ký quỹ
                  ngay khi hợp đồng có hiệu lực.
                </div>
              </div>
              <div className="rounded-xl border-[0.5px] border-dashed border-[#CBD5E1] bg-surface px-[18px] py-4">
                <div className="mb-2 flex items-center justify-between">
                  <div className="flex items-center gap-2.5">
                    <span className="ms text-[20px] text-text-secondary">agriculture</span>
                    <span className="text-[14.5px] font-medium">Bên bán</span>
                  </div>
                  <span className="rounded-full bg-surface-muted px-2 py-0.5 text-[11px] text-text-secondary">
                    Tuỳ chọn
                  </span>
                </div>
                <div className="text-[12.5px] leading-[1.55] text-text-secondary">
                  Khoá thêm một khoản <b className="font-medium text-text-primary">cọc đảm bảo</b>{" "}
                  để tăng độ tin cậy.
                </div>
              </div>
            </div>

            {/* ARROW 1 */}
            <div className="flex items-center justify-center px-3.5">
              <span className="ms text-[26px] text-[#CBD5E1]">arrow_forward</span>
            </div>

            {/* COL 2: VAULT */}
            <div className="rounded-2xl border-[1.5px] border-primary bg-primary-tint/20 px-5 py-[22px]">
              <div className="mb-3 flex items-center gap-2.5">
                <span className="flex h-[38px] w-[38px] items-center justify-center rounded-lg bg-primary">
                  <span className="ms text-[22px] text-white">lock</span>
                </span>
                <div>
                  <div className="text-[15.5px] font-semibold">Tài khoản trung gian</div>
                  <div className="font-mono text-[12px] text-primary">FBO / omnibus</div>
                </div>
              </div>
              <div className="mb-3.5 text-[13px] leading-[1.6] text-text-secondary">
                Tiền nằm ở một tài khoản trung lập —{" "}
                <b className="font-medium text-text-primary">không thuộc túi bên nào</b>. Không có
                tài khoản riêng cho từng hợp đồng.
              </div>
              <div className="flex items-start gap-2 rounded-lg border-[0.5px] border-[#BBF7D0] bg-surface px-3 py-2.5">
                <span className="ms mt-0.5 text-[16px] text-primary">receipt_long</span>
                <span className="text-[12px] leading-[1.5] text-text-secondary">
                  Sổ cái chỉ ghi thêm. Số dư luôn tính lại từ bút toán, không lưu sẵn.
                </span>
              </div>
            </div>

            {/* ARROW 2 */}
            <div className="flex items-center justify-center px-3.5">
              <span className="ms text-[26px] text-[#CBD5E1]">arrow_forward</span>
            </div>

            {/* COL 3: OUTPUTS */}
            <div className="flex flex-col gap-3">
              <div className="rounded-xl border-[0.5px] border-[#BBF7D0] bg-primary-tint/20 px-4 py-3.5">
                <div className="mb-1.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="ms text-[19px] text-primary">payments</span>
                    <span className="text-[14px] font-medium">Giải ngân từng đợt</span>
                  </div>
                  <span className="whitespace-nowrap rounded-full bg-primary-tint px-2 py-0.5 text-[11px] text-primary">
                    → Bên bán
                  </span>
                </div>
                <div className="text-[12.5px] leading-[1.55] text-text-secondary">
                  Mỗi cột mốc giao xong, phần tiền tương ứng tự động chuyển cho bên bán.
                </div>
              </div>
              <div className="rounded-xl border-[0.5px] border-[#FDE68A] bg-[#FFFBEB] px-4 py-3.5">
                <div className="mb-1.5 flex items-center gap-2">
                  <span className="ms text-[19px] text-[#D97706]">gavel</span>
                  <span className="text-[14px] font-medium">Tịch thu phạt nếu vi phạm</span>
                </div>
                <div className="text-[12.5px] leading-[1.55] text-text-secondary">
                  Nếu một bên vi phạm, phần phạt được trích theo đúng mức đã ký từ trước.
                </div>
              </div>
              <div className="rounded-xl border-[0.5px] border-border bg-page-bg px-4 py-3.5">
                <div className="mb-1.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="ms text-[19px] text-text-secondary">restart_alt</span>
                    <span className="text-[14px] font-medium">Hoàn phần còn lại</span>
                  </div>
                  <span className="whitespace-nowrap rounded-full bg-surface-muted px-2 py-0.5 text-[11px] text-text-secondary">
                    → Bên mua
                  </span>
                </div>
                <div className="text-[12.5px] leading-[1.55] text-text-secondary">
                  Khi tất toán, phần chưa dùng đến được trả lại đúng bên, tính pro-rata.
                </div>
              </div>
            </div>
          </div>

          {/* SEQUENCE CAPTION */}
          <div className="mt-7 grid grid-cols-[1fr_auto_1fr_auto_1fr_auto_1fr] items-center border-t-[0.5px] border-border pt-6">
            <span className="inline-flex items-center justify-center gap-2 text-[13px] text-text-secondary">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary font-mono text-[11px] text-white">
                1
              </span>{" "}
              Khoá tiền
            </span>
            <span className="ms px-2.5 text-[16px] text-[#CBD5E1]">arrow_forward</span>
            <span className="inline-flex items-center justify-center gap-2 text-[13px] text-text-secondary">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary font-mono text-[11px] text-white">
                2
              </span>{" "}
              Giữ ở tài khoản trung gian
            </span>
            <span className="ms px-2.5 text-[16px] text-[#CBD5E1]">arrow_forward</span>
            <span className="inline-flex items-center justify-center gap-2 text-[13px] text-text-secondary">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary font-mono text-[11px] text-white">
                3
              </span>{" "}
              Giải ngân theo cột mốc
            </span>
            <span className="ms px-2.5 text-[16px] text-[#CBD5E1]">arrow_forward</span>
            <span className="inline-flex items-center justify-center gap-2 text-[13px] text-text-secondary">
              <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary font-mono text-[11px] text-white">
                4
              </span>{" "}
              Tất toán & hoàn phần còn lại
            </span>
          </div>
        </div>
      </div>

      {/* FAQ */}
      <div className="mt-10 border-y-[0.5px] border-border bg-surface-muted">
        <div className="mx-auto max-w-[1160px] px-10 py-14">
          <div className="mb-7 text-[24px] font-medium">Câu hỏi thường gặp</div>
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
            <div className="rounded-xl border-[0.5px] border-border bg-surface p-6">
              <div className="mb-2.5 flex items-start gap-3">
                <span className="ms mt-0.5 text-[22px] text-primary">help</span>
                <div className="text-[16.5px] font-medium leading-[1.4]">
                  Vì sao cả hai bên đều phải đặt cọc?
                </div>
              </div>
              <div className="text-[14px] leading-[1.65] text-text-secondary">
                Bên mua khoá tiền hàng để bên bán yên tâm giao. Bên bán khoá cọc đảm bảo để bên mua
                yên tâm hàng sẽ đến đúng cam kết. Cọc hai chiều khiến việc bỏ kèo trở nên đắt cho cả
                hai — nên gần như không ai muốn.
              </div>
            </div>

            <div className="rounded-xl border-[0.5px] border-border bg-surface p-6">
              <div className="mb-2.5 flex items-start gap-3">
                <span className="ms mt-0.5 text-[22px] text-primary">help</span>
                <div className="text-[16.5px] font-medium leading-[1.4]">
                  Sổ cái "chỉ ghi thêm" nghĩa là gì, sao lại quan trọng?
                </div>
              </div>
              <div className="text-[14px] leading-[1.65] text-text-secondary">
                Mọi bút toán được ghi nối tiếp, không có nút sửa hay xoá ở bất kỳ đâu. Muốn điều
                chỉnh thì phải ghi một bút toán mới, để lại dấu vết. Nhờ vậy không ai âm thầm thay
                đổi lịch sử tiền — số dư luôn tính lại được từ đầu.
              </div>
            </div>

            <div className="rounded-xl border-[0.5px] border-border bg-surface p-6">
              <div className="mb-2.5 flex items-start gap-3">
                <span className="ms mt-0.5 text-[22px] text-primary">help</span>
                <div className="text-[16.5px] font-medium leading-[1.4]">
                  Giải ngân từng đợt khác gì thanh toán một lần cuối hợp đồng?
                </div>
              </div>
              <div className="mb-3.5 text-[14px] leading-[1.65] text-text-secondary">
                Bên bán nhận tiền cho từng đợt ngay khi giao xong, thay vì đợi đến cột mốc cuối. Vốn
                lưu động được giải phóng sớm hơn nhiều.
              </div>
              <div className="flex items-start gap-2 rounded-lg border-[0.5px] border-[#BBF7D0] bg-primary-tint/20 px-3 py-2.5">
                <span className="ms mt-0.5 text-[17px] text-primary">insights</span>
                <span className="text-[12.5px] leading-[1.55] text-text-secondary">
                  Đối chiếu "tổng tiền đã khoá" với "tiền thực nhận" theo tháng cho thấy khoảng cách
                  thu hẹp rõ rệt khi chia nhỏ đợt giao.
                </span>
              </div>
            </div>

            <div className="rounded-xl border-[0.5px] border-border bg-surface p-6">
              <div className="mb-2.5 flex items-start gap-3">
                <span className="ms mt-0.5 text-[22px] text-primary">help</span>
                <div className="text-[16.5px] font-medium leading-[1.4]">
                  Nếu có tranh chấp thì tiền xử lý ra sao?
                </div>
              </div>
              <div className="text-[14px] leading-[1.65] text-text-secondary">
                Phần tiền đang tranh chấp bị giữ lại trong ký quỹ — không giải ngân cũng không hoàn
                — cho tới khi có kết luận. Giám định độc lập phân xử, rồi hệ thống giải ngân hoặc
                hoàn theo đúng phán quyết. Trong lúc chờ, con số hiển thị là tạm tính và ghi rõ có
                thể điều chỉnh.
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* CTA */}
      <div className="mx-auto max-w-[1160px] px-10 py-16 text-center">
        <div className="mb-3 text-[28px] font-medium">Đã rõ cơ chế giữ tiền?</div>
        <div className="mb-7 text-[15px] text-text-secondary">
          Xem toàn bộ vòng đời hợp đồng, hoặc mở tài khoản để bắt đầu.
        </div>
        <div className="flex justify-center gap-3">
          <Link
            to="/register"
            className={cn(buttonVariants({ size: "cta" }))}
          >
            Đăng ký doanh nghiệp
          </Link>
          <Link
            to="/how-it-works"
            className={cn(buttonVariants({ variant: "outline", size: "cta" }), "gap-2 border-[0.5px]")}
          >
            Xem cách hoạt động <span className="ms text-[18px] text-primary">arrow_forward</span>
          </Link>
        </div>
      </div>

      <Footer />
    </div>
  )
}

export { EscrowPage }
