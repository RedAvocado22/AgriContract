import { useState } from "react"

import { Banner } from "@/shared/ui/banner"
import { BottomSheet } from "@/shared/ui/bottom-sheet"
import { BottomTabBar } from "@/shared/ui/bottom-tab-bar"
import { BulkActionBar } from "@/shared/ui/bulk-action-bar"
import { Button } from "@/shared/ui/button"
import { DeltaComparisonBlock } from "@/shared/ui/delta-comparison-block"
import { FilterBar } from "@/shared/ui/filter-bar"
import { HorizontalBarList } from "@/shared/ui/horizontal-bar-list"
import { MobileHeader } from "@/shared/ui/mobile-header"
import { MobileStickyCta } from "@/shared/ui/mobile-sticky-cta"
import { MobileTopBar } from "@/shared/ui/mobile-top-bar"
import { MoneyDisplay } from "@/shared/ui/money-display"
import { NegotiationTermCard } from "@/shared/ui/negotiation-term-card"
import { OTPInput } from "@/shared/ui/otp-input"
import { PublicMobileNav } from "@/shared/ui/public-mobile-nav"
import { RecordCard } from "@/shared/ui/record-card"
import { StatusBadge } from "@/shared/ui/status-badge"
import { TabsUnderline } from "@/shared/ui/tabs-underline"
import { VerticalStepper } from "@/shared/ui/vertical-stepper"

function Frame({ label, note, children }: { label: string; note: string; children: React.ReactNode }) {
  return (
    <div className="w-[390px]">
      <div className="mb-0.5 text-[15px] font-medium text-text-primary">{label}</div>
      <div className="mb-3.5 text-[13px] leading-relaxed text-text-secondary">{note}</div>
      <div className="w-[390px] overflow-hidden rounded-[30px] border-[0.5px] border-border-strong bg-page-bg shadow-[0_14px_44px_rgba(15,23,42,0.14)]">
        {children}
      </div>
    </div>
  )
}

function MobileComponentShowcase() {
  const [sheetOpen, setSheetOpen] = useState(false)
  const [navOpen, setNavOpen] = useState(false)
  const [otp, setOtp] = useState("427")
  const [sliderValue, setSliderValue] = useState(2)
  const [ctaExpanded, setCtaExpanded] = useState(false)
  const [selected, setSelected] = useState<string[]>(["a", "c"])

  return (
    <div className="mx-auto max-w-[1440px] px-14 py-12">
      <div className="mb-11 max-w-2xl">
        <div className="mb-1.5 text-[13px] text-text-muted">AgriContract — Design System</div>
        <div className="text-[22px] font-medium">Component cho mobile</div>
        <div className="mt-1.5 text-sm text-text-secondary">
          Những component không thể responsive-collapse từ bản desktop — phải đổi hẳn hình thái.
          Không phải shared component thường, page riêng theo{" "}
          <span className="font-mono">Mobile components.dc.html</span>. Khung 390px.
        </div>
        <a href="/components" className="mt-2 inline-block text-sm text-primary">
          ← Về component library desktop
        </a>
      </div>

      <div className="flex flex-wrap items-start gap-11">
        <Frame label="1 · MobileTopBar + BottomTabBar" note="Sidebar 240px → top bar có ☰ + tab dưới đáy.">
          <MobileTopBar title="Tổng quan" hasUnreadNotifications />
          <div className="flex flex-col gap-3 p-4">
            <div className="rounded-xl bg-surface-muted px-3.5 py-3">
              <div className="mb-1.5 text-[13px] text-text-secondary">Hợp đồng đang hoạt động</div>
              <div className="text-2xl font-medium">14</div>
            </div>
          </div>
          <BottomTabBar
            items={[
              { key: "overview", label: "Tổng quan", icon: "dashboard", active: true },
              { key: "contracts", label: "Hợp đồng", icon: "description" },
              { key: "escrow", label: "Ký quỹ", icon: "account_balance_wallet" },
              { key: "reputation", label: "Uy tín", icon: "verified" },
            ]}
          />
        </Frame>

        <Frame label="2 · RecordCard — bảng → thẻ" note="5 cột không vừa 390px. Mã HĐ + trạng thái nổi đầu, còn lại thành nhãn–giá trị.">
          <div className="p-4">
            <div className="mb-3 text-[13px] text-text-muted">Danh sách hợp đồng</div>
            <div className="flex flex-col gap-3">
              <RecordCard
                code="HĐ-2026-0142"
                badge={<StatusBadge variant="info">Đang thực hiện</StatusBadge>}
                rows={[
                  { label: "Đối tác", value: "HTX Ea Kar" },
                  { label: "Mặt hàng", value: "Cà phê Robusta" },
                  { label: "Giá trị", value: <MoneyDisplay amount={5_000_000_000} sign="neutral" /> },
                ]}
              />
            </div>
          </div>
        </Frame>

        <Frame label="3 · RecordCard — ledger → thẻ" note="Số tiền là thông tin chính nên đưa lên to bằng prop highlight.">
          <div className="p-4">
            <div className="mb-3 text-[13px] text-text-muted">Sổ cái ký quỹ</div>
            <RecordCard
              code={
                <span className="inline-flex items-center gap-1.5">
                  <span className="ms text-sm text-text-muted">lock</span> LE-8f3a91c2
                </span>
              }
              badge={<StatusBadge variant="success">Đã ghi</StatusBadge>}
              highlight={<MoneyDisplay amount={2_500_000_000} sign="positive" emphasis="large" />}
              rows={[
                { label: "Loại", value: "Giải ngân bên bán" },
                { label: "Bên", value: "Bên bán" },
                { label: "Số dư sau", value: <MoneyDisplay amount={2_500_000_000} sign="neutral" /> },
              ]}
            />
          </div>
        </Frame>

        <Frame label="4 · HorizontalBarList" note="Cột dọc ép nhãn xoay/cắt chữ. Xoay ngang: nhãn 1 dòng, thanh kéo theo bề rộng có sẵn.">
          <div className="p-4">
            <div className="mb-0.5 text-[15px] font-medium">Tỷ lệ bẻ kèo theo ngành hàng</div>
            <div className="mb-4.5 text-[12.5px] text-text-secondary">Thanh cam = vượt ngưỡng an toàn.</div>
            <HorizontalBarList
              scaleLabels={["0%", "3%", "6%", "9%"]}
              items={[
                { label: "Cà phê", value: 45.6, displayValue: "4,1%" },
                { label: "Gạo", value: 25.6, displayValue: "2,3%" },
                { label: "Cao su", value: 75.6, displayValue: "6,8%", warning: true },
                { label: "Điều", value: 38.9, displayValue: "3,5%" },
              ]}
            />
          </div>
        </Frame>

        <Frame label="6 · VerticalStepper" note="5 bước ngang tràn màn. Xoay dọc: đường nối chạy dọc, nhãn dài thoải mái.">
          <div className="p-5">
            <div className="mb-4 text-[13px] text-text-muted">Tạo hợp đồng · bước 3/5</div>
            <VerticalStepper
              currentStep={3}
              steps={[
                "Thông tin chung",
                "Mặt hàng & khối lượng",
                "Cột mốc & giao hàng",
                "Điều khoản ký quỹ",
                "Xem lại & ký",
              ]}
            />
          </div>
        </Frame>

        <Frame label="7 · TabsUnderline scrollable" note="6 tab không xuống dòng — cuộn ngang, mép phải hé lộ còn tab nữa.">
          <div className="pt-4 pb-5">
            <div className="mb-3.5 px-4 text-[13px] text-text-muted">Chi tiết hợp đồng</div>
            <TabsUnderline
              scrollable
              value="overview"
              className="px-4"
              tabs={[
                { key: "overview", label: "Tổng quan" },
                { key: "milestones", label: "Cột mốc" },
                { key: "escrow", label: "Ký quỹ" },
                { key: "inspection", label: "Kiểm định" },
                { key: "documents", label: "Chứng từ" },
                { key: "audit", label: "Nhật ký" },
              ]}
            />
          </div>
        </Frame>

        <Frame label="8 · DeltaComparisonBlock layout=&quot;cards&quot;" note="Lưới 4 cột không chịu được 390px. Mỗi delta 1 thẻ, chênh lệch nổi bật.">
          <div className="p-4">
            <div className="mb-3 text-[13px] text-text-muted">Đối chiếu khối lượng</div>
            <DeltaComparisonBlock
              layout="cards"
              rows={[
                {
                  label: "Chênh lệch cam kết",
                  before: "20.000 kg",
                  after: "19.680 kg",
                  delta: "− 320 kg",
                  consequence: "danger",
                  consequenceLabel: "Có thể tính phạt",
                },
                {
                  label: "Hao hụt vận chuyển",
                  before: "19.680 kg",
                  after: "19.550 kg",
                  delta: "− 130 kg",
                  consequence: "neutral",
                  consequenceLabel: "Không tính phạt",
                },
              ]}
            />
          </div>
        </Frame>

        <Frame label="9 · BottomSheet (bấm để mở thật)" note="Modal 560px giữa màn chật trên điện thoại. Neo đáy có thanh kéo.">
          <div className="flex min-h-56 flex-col items-center justify-center p-5">
            <Button onClick={() => setSheetOpen(true)}>Mở bottom sheet</Button>
          </div>
          <BottomSheet
            open={sheetOpen}
            onOpenChange={setSheetOpen}
            title="Xác nhận ký hợp đồng"
            description="Nhập mã OTP gửi tới số điện thoại đăng ký để hoàn tất ký hợp đồng."
          >
            <OTPInput value={otp} onChange={setOtp} className="mb-5" />
            <Button className="mb-2 w-full">Xác nhận</Button>
            <Button variant="ghost" className="w-full" onClick={() => setSheetOpen(false)}>
              Huỷ
            </Button>
          </BottomSheet>
        </Frame>

        <Frame label="10 · Banner responsive (thu hẹp khung xem thử)" note="Desktop đặt nút bên phải. Dưới breakpoint nút xuống hàng, full-width.">
          <div className="p-4">
            <div className="mb-3 text-[13px] text-text-muted">Banner cảnh báo</div>
            <Banner variant="danger" actionLabel="Thử khoá lại">
              Hợp đồng đã ký nhưng chưa khoá được ký quỹ.
            </Banner>
          </div>
        </Frame>

        <Frame label="11 · MobileHeader" note="Chuỗi breadcrumb 3 cấp không vừa 1 dòng — rút thành nút Quay lại + dải rút gọn.">
          <MobileHeader
            title="Cột mốc 2"
            crumbs={[
              { label: "Hợp đồng" },
              { label: "…", collapsed: true },
              { label: "Cột mốc 2" },
            ]}
          />
          <div className="flex flex-col gap-2 p-4">
            <div className="flex justify-between text-sm">
              <span className="text-text-secondary">Trạng thái</span>
              <span>Chờ xác nhận</span>
            </div>
          </div>
        </Frame>

        <Frame label="12 · FilterBar" note="5 dropdown 1 hàng không vừa. Gom vào nút Bộ lọc, lọc đang chọn hiện thành chip cuộn.">
          <div className="p-4">
            <div className="mb-3 text-[13px] text-text-muted">Tìm nguồn hàng</div>
            <FilterBar
              activeFilterCount={2}
              chips={[
                { key: "coffee", label: "Cà phê", removable: true },
                { key: "rep80", label: "Uy tín ≥ 80", removable: true },
                { key: "eudr", label: "Rủi ro EUDR" },
              ]}
              resultsSummary="6 nguồn hàng · sắp xếp theo Uy tín cao nhất"
            />
          </div>
        </Frame>

        <Frame label="13 · MobileStickyCta" note="Cột tóm tắt 320px đẩy nút Tiếp tục khỏi tầm nhìn. Ghim CTA đáy, tóm tắt gập được.">
          <div className="flex flex-col">
            <div className="p-4">
              <div className="mb-3 text-[13px] text-text-muted">Tạo hợp đồng · bước 2/5</div>
              <div className="rounded-xl bg-surface-muted px-4 py-3.5">
                <div className="text-sm text-text-secondary">Tổng giá trị</div>
                <MoneyDisplay amount={2_400_000_000} sign="neutral" emphasis="large" />
              </div>
            </div>
            <MobileStickyCta
              summaryLabel="Tóm tắt hợp đồng"
              summaryValue={<MoneyDisplay amount={2_400_000_000} sign="neutral" />}
              expanded={ctaExpanded}
              onToggle={() => setCtaExpanded((v) => !v)}
              secondaryLabel="Quay lại"
              primaryLabel="Tiếp tục"
            />
          </div>
        </Frame>

        <Frame label="15 · NegotiationTermCard" note="Bảng 2 cột 'Bạn | Đối tác' ép chật. Mỗi điều khoản 1 thẻ, slider full-width.">
          <div className="flex flex-col gap-3 p-4">
            <NegotiationTermCard
              termLabel="Ngưỡng hao hụt vận chuyển"
              changed
              changeDirection="up"
              yourValue="2,0%"
              sliderMin={0}
              sliderMax={10}
              sliderValue={sliderValue}
              onSliderChange={setSliderValue}
              floorLabel="0% — sàn"
              ceilingLabel="10% — trần"
              partnerValue="3,0%"
              partnerPreviousValue="2,0%"
            />
          </div>
        </Frame>

        <Frame label="16 · PublicMobileNav (bấm để mở thật)" note="Drawer app chỉ dành cho sidebar 240px — trang marketing cần ☰ riêng.">
          <div className="flex h-14 items-center justify-between border-b-[0.5px] border-border bg-surface px-4">
            <span className="text-[17px] font-medium text-primary">AgriContract</span>
            <button
              type="button"
              onClick={() => setNavOpen(true)}
              className="flex size-10 items-center justify-center rounded-[10px] border-[0.5px] border-border"
            >
              <span className="ms text-2xl">menu</span>
            </button>
          </div>
          <div className="p-4 text-[13px] text-text-muted">Nội dung trang chủ...</div>
          <PublicMobileNav
            open={navOpen}
            onOpenChange={setNavOpen}
            links={[
              { key: "market", label: "Sàn nguồn hàng" },
              { key: "prices", label: "Bảng giá tham khảo" },
              { key: "how", label: "Cách hoạt động" },
              { key: "escrow", label: "Về ký quỹ" },
            ]}
          />
        </Frame>

        <Frame label="18 · BulkActionBar" note="Bảng chọn hàng loạt → thẻ có checkbox; thanh hành động trượt lên từ đáy khi có mục chọn.">
          <div className="flex flex-col gap-2.5 p-4">
            {[
              { key: "a", label: "buyer1@mail.com" },
              { key: "b", label: "buyer2@mail.com" },
              { key: "c", label: "buyer3@mail.com" },
            ].map((row) => (
              <RecordCard
                key={row.key}
                selected={selected.includes(row.key)}
                onClick={() =>
                  setSelected((prev) =>
                    prev.includes(row.key) ? prev.filter((k) => k !== row.key) : [...prev, row.key]
                  )
                }
                checkbox={
                  <span
                    className={`ms text-xl ${selected.includes(row.key) ? "text-primary" : "text-border-strong"}`}
                  >
                    {selected.includes(row.key) ? "check_box" : "check_box_outline_blank"}
                  </span>
                }
                subtitle={row.label}
              />
            ))}
          </div>
          {selected.length > 0 ? (
            <BulkActionBar
              selectedCount={selected.length}
              actions={[
                { key: "assign", label: "Gắn mặt hàng" },
                { key: "retry", label: "Xử lý lại", emphasis: true },
              ]}
            />
          ) : null}
        </Frame>
      </div>
    </div>
  )
}

export { MobileComponentShowcase }
