import { useState } from "react"

import { AppSidebar } from "@/shared/ui/app-sidebar"
import { Avatar } from "@/shared/ui/avatar"
import { Banner } from "@/shared/ui/banner"
import { BarChartCard } from "@/shared/ui/bar-chart-card"
import { BottomTabBar } from "@/shared/ui/bottom-tab-bar"
import { Breadcrumb } from "@/shared/ui/breadcrumb"
import { Button } from "@/shared/ui/button"
import { Checkbox } from "@/shared/ui/checkbox"
import { ContentHashDisplay } from "@/shared/ui/content-hash-display"
import { Countdown } from "@/shared/ui/countdown"
import { DangerConfirmDialog } from "@/shared/ui/danger-confirm-dialog"
import { DataTable } from "@/shared/ui/data-table"
import { DatePicker } from "@/shared/ui/date-picker"
import { DateRangePicker } from "@/shared/ui/date-range-picker"
import { DeadlinePill } from "@/shared/ui/deadline-pill"
import { DeltaComparisonBlock } from "@/shared/ui/delta-comparison-block"
import { Drawer } from "@/shared/ui/drawer"
import { DropdownMenu } from "@/shared/ui/dropdown-menu"
import { EmptyState } from "@/shared/ui/empty-state"
import { FileUpload } from "@/shared/ui/file-upload"
import { IconButton } from "@/shared/ui/icon-button"
import { KeyValueList } from "@/shared/ui/key-value-list"
import { LineChartCard } from "@/shared/ui/line-chart-card"
import { MetricCard } from "@/shared/ui/metric-card"
import { MilestoneTimeline } from "@/shared/ui/milestone-timeline"
import { Modal } from "@/shared/ui/modal"
import { MoneyDisplay } from "@/shared/ui/money-display"
import { MoneyInput } from "@/shared/ui/money-input"
import { MultiSelect } from "@/shared/ui/multi-select"
import { NotificationItem } from "@/shared/ui/notification-item"
import { OTPInput } from "@/shared/ui/otp-input"
import { PageLayout } from "@/shared/ui/page-layout"
import { Pagination } from "@/shared/ui/pagination"
import { PartnerCard } from "@/shared/ui/partner-card"
import { ProgressBar } from "@/shared/ui/progress-bar"
import { QueueListWithPanel } from "@/shared/ui/queue-list-with-panel"
import { RadioButton, RadioGroup } from "@/shared/ui/radio-group"
import { RepeatableFieldGroup } from "@/shared/ui/repeatable-field-group"
import { ReputationDonut } from "@/shared/ui/reputation-donut"
import { SearchInput } from "@/shared/ui/search-input"
import { SegmentedControl } from "@/shared/ui/segmented-control"
import { SelectDropdown } from "@/shared/ui/select-dropdown"
import { Skeleton } from "@/shared/ui/skeleton"
import { Spinner } from "@/shared/ui/spinner"
import { StatusBadge } from "@/shared/ui/status-badge"
import { Stepper } from "@/shared/ui/stepper"
import { StickyFormSummary } from "@/shared/ui/sticky-form-summary"
import { SystemFreezeAlert } from "@/shared/ui/system-freeze-alert"
import { TabsUnderline } from "@/shared/ui/tabs-underline"
import { TextInput } from "@/shared/ui/text-input"
import { Textarea } from "@/shared/ui/textarea"
import { Toggle } from "@/shared/ui/toggle"
import { Tooltip } from "@/shared/ui/tooltip"
import { TopBar } from "@/shared/ui/top-bar"
import { useToast } from "@/shared/ui/toast"
import { VerifyChip } from "@/shared/ui/verify-chip"

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-14">
      <h2 className="mb-5 border-b-[0.5px] border-border pb-3 text-lg font-medium">{title}</h2>
      {children}
    </section>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="mb-6">
      <div className="mb-2.5 text-[13px] text-text-muted">{label}</div>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  )
}

const sidebarItems = [
  { label: "Tổng quan", icon: "dashboard", active: true },
  { label: "Hợp đồng", icon: "description" },
  { label: "Cột mốc", icon: "flag" },
  { label: "Ký quỹ", icon: "account_balance" },
  { label: "Kiểm định", icon: "verified" },
  { label: "Uy tín", icon: "workspace_premium" },
  { label: "Nhật ký", icon: "history" },
  { label: "Cài đặt", icon: "settings" },
]

const adminSidebarItems = [
  { label: "Tổng quan vận hành", icon: "dashboard", section: "Vận hành", active: true },
  { label: "Duyệt tài khoản", icon: "how_to_reg", section: "Vận hành" },
  { label: "Duyệt danh mục", icon: "category", section: "Vận hành" },
  { label: "Xử tranh chấp Level 1", icon: "gavel", section: "Tranh chấp" },
  { label: "Duyệt bất khả kháng", icon: "report", section: "Tranh chấp" },
  { label: "Sổ cái toàn hệ thống", icon: "account_balance", section: "Tài chính" },
  { label: "Cảnh báo AML", icon: "warning", section: "Tài chính" },
]

interface ContractRow {
  id: string
  partner: string
  commodity: string
  value: number
  status: "active" | "settled"
}

const contractRows: ContractRow[] = [
  { id: "HĐ-2026-0142", partner: "HTX Ea Kar", commodity: "Cà phê Robusta", value: 5_000_000_000, status: "active" },
  { id: "HĐ-2026-0139", partner: "HTX Tân Lập", commodity: "Gạo ST25", value: 1_850_000_000, status: "settled" },
]

const provinceOptions = [
  { value: "dak-lak", label: "Đắk Lắk" },
  { value: "gia-lai", label: "Gia Lai" },
  { value: "dak-nong", label: "Đắk Nông" },
  { value: "lam-dong", label: "Lâm Đồng" },
]

const commodityOptions = [
  { value: "coffee", label: "Cà phê" },
  { value: "rice", label: "Gạo" },
  { value: "rubber", label: "Cao su" },
  { value: "cashew", label: "Điều" },
]

function ComponentShowcase() {
  const [tab, setTab] = useState("overview")
  const [segment, setSegment] = useState("all")
  const [otp, setOtp] = useState("")
  const [modalOpen, setModalOpen] = useState(false)
  const [dangerOpen, setDangerOpen] = useState(false)
  const [name, setName] = useState("Giao hàng đợt 2")
  const [deposit, setDeposit] = useState(500_000_000)
  const [sellerDeposit, setSellerDeposit] = useState(0)
  const [commodity, setCommodity] = useState<string | null>("coffee")
  const [provinces, setProvinces] = useState<string[]>(["dak-lak", "gia-lai"])
  const [note, setNote] = useState("")
  const [agreed, setAgreed] = useState(true)
  const [role, setRole] = useState("buyer")
  const [autoRelease, setAutoRelease] = useState(true)
  const [milestones, setMilestones] = useState([
    { key: "m1", label: "Cột mốc 1 — 10 tấn" },
    { key: "m2", label: "Cột mốc 2 — 10 tấn" },
  ])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [activeQueueKey, setActiveQueueKey] = useState("case-1")
  const [page, setPage] = useState(2)
  const [rangeStart, setRangeStart] = useState("2026-05-01")
  const [rangeEnd, setRangeEnd] = useState("2026-05-31")
  const toast = useToast()

  return (
    <div className="mx-auto max-w-[1440px] px-14 py-12">
      <div className="mb-11">
        <div className="mb-1.5 text-[13px] text-text-muted">AgriContract — Design System</div>
        <div className="text-[22px] font-medium">Shared components (batch 1 → 6, đầy đủ)</div>
        <div className="mt-1.5 max-w-xl text-sm text-text-secondary">
          55 component nền tảng, dựng theo Component Library.dc.html §6.1–6.6. Dùng để mày duyệt
          trước khi ráp vào màn thật.
        </div>
        <a href="/?mobile" className="mt-2 inline-block text-sm text-primary">
          Xem component cho mobile →
        </a>
      </div>

      <Section title="Form inputs (TextInput, MoneyInput, Select, MultiSelect, DatePicker, Textarea)">
        <div className="grid max-w-3xl grid-cols-3 gap-5">
          <TextInput
            id="name"
            label="Tên cột mốc"
            required
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
          <TextInput id="weight" label="Khối lượng" defaultValue="20.000" unit="kg" />
          <MoneyInput
            id="deposit"
            label="Tiền cọc bên mua"
            required
            value={deposit}
            onChange={setDeposit}
          />
          <MoneyInput
            id="seller-deposit"
            label="Tiền cọc bên bán (tuỳ chọn)"
            value={sellerDeposit}
            onChange={setSellerDeposit}
            hint={
              sellerDeposit === 0
                ? "Để 0 sẽ giảm gánh nặng vốn cho bên bán — đây là mặc định hợp lệ, không phải chưa điền."
                : undefined
            }
          />
          <SelectDropdown
            id="commodity"
            label="Mặt hàng"
            required
            options={commodityOptions}
            value={commodity}
            onChange={setCommodity}
          />
          <DatePicker id="delivery-date" label="Ngày giao dự kiến" required />
          <div className="col-span-2">
            <MultiSelect
              id="provinces"
              label="Tỉnh"
              options={provinceOptions}
              value={provinces}
              onChange={setProvinces}
            />
          </div>
        </div>
        <div className="mt-5 max-w-3xl">
          <Textarea
            id="note"
            label="Ghi chú"
            placeholder="Nhập mô tả..."
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </div>
      </Section>

      <Section title="Checkbox / RadioGroup / Toggle">
        <div className="flex flex-wrap items-center gap-8">
          <Checkbox
            checked={agreed}
            onCheckedChange={(checked) => setAgreed(checked === true)}
            label="Tôi đồng ý xuất hồ sơ này"
          />
          <RadioGroup value={role} onValueChange={(v) => setRole(v as string)}>
            <RadioButton value="buyer" label="Bên mua" />
            <RadioButton value="seller" label="Bên bán" />
          </RadioGroup>
          <Row label="Tự động giải ngân khi hết hạn xác nhận">
            <Toggle checked={autoRelease} onCheckedChange={setAutoRelease} />
          </Row>
        </div>
      </Section>

      <Section title="FileUpload — 3 trạng thái">
        <div className="max-w-md">
          <FileUpload
            files={[
              { name: "can-hang-01.jpg", sizeLabel: "2,1 MB", status: "processing" },
              { name: "can-hang-02.jpg", sizeLabel: "1,8 MB", status: "ready" },
              {
                name: "can-hang-03.jpg",
                status: "failed",
                failureReason: "phát hiện virus",
                onRetry: () => {},
              },
            ]}
          />
        </div>
      </Section>

      <Section title="SystemFreezeAlert">
        <SystemFreezeAlert />
      </Section>

      <Section title="Spinner">
        <Row label="Kích thước tuỳ chỉnh">
          <Spinner />
          <Spinner size={24} />
          <span className="flex items-center gap-1.5 text-sm text-text-secondary">
            <Spinner size={14} /> Đang xử lý...
          </span>
        </Row>
      </Section>

      <Section title="Toast — bấm để bắn thật (tự tắt 5s)">
        <Row label="4 biến thể">
          <Button
            size="sm"
            onClick={() =>
              toast.add({
                type: "success",
                title: "Đã khoá ký quỹ 5.000.000.000 ₫",
                description: "Hợp đồng bắt đầu thực hiện.",
              })
            }
          >
            Success
          </Button>
          <Button
            size="sm"
            variant="destructive"
            onClick={() =>
              toast.add({
                type: "danger",
                title: "Không khoá được ký quỹ",
                description: "Kiểm tra số dư rồi thử lại.",
              })
            }
          >
            Danger
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() =>
              toast.add({
                type: "info",
                title: "Hợp đồng đã được ký",
                description: "Cả hai bên đã ký hợp đồng HĐ-2026-0142.",
              })
            }
          >
            Info
          </Button>
        </Row>
      </Section>

      <Section title="SearchInput">
        <div className="max-w-sm">
          <SearchInput placeholder="Tìm hợp đồng, đối tác..." />
        </div>
      </Section>

      <Section title="DateRangePicker">
        <div className="max-w-md">
          <DateRangePicker
            label="Khoảng thời gian"
            startValue={rangeStart}
            endValue={rangeEnd}
            onStartChange={setRangeStart}
            onEndChange={setRangeEnd}
          />
        </div>
      </Section>

      <Section title="BottomTabBar (mobile — thay hẳn AppSidebar, không phải bản responsive-collapse)">
        <div className="max-w-sm rounded-md border-[0.5px] border-border">
          <BottomTabBar
            items={[
              { key: "overview", label: "Tổng quan", icon: "dashboard", active: true },
              { key: "contracts", label: "Hợp đồng", icon: "description" },
              { key: "milestones", label: "Cột mốc", icon: "flag" },
              { key: "notifications", label: "Thông báo", icon: "notifications" },
              { key: "account", label: "Tài khoản", icon: "person" },
            ]}
          />
        </div>
      </Section>

      <Section title="RepeatableFieldGroup">
        <div className="max-w-md">
          <RepeatableFieldGroup
            items={milestones}
            onRemove={(key) => setMilestones((prev) => prev.filter((m) => m.key !== key))}
            onAdd={() =>
              setMilestones((prev) => [
                ...prev,
                { key: `m${prev.length + 1}`, label: `Cột mốc ${prev.length + 1} — 10 tấn` },
              ])
            }
          />
        </div>
      </Section>

      <Section title="Avatar">
        <Row label="Ảnh thật / fallback initials">
          <Avatar name="HTX Nông nghiệp Ea Kar" />
          <Avatar name="Công ty Tây Nguyên" size={48} />
        </Row>
      </Section>

      <Section title="Drawer + DropdownMenu">
        <Row label="Bấm để mở thật">
          <Button variant="outline" onClick={() => setDrawerOpen(true)}>
            Mở drawer chi tiết vùng trồng
          </Button>
          <DropdownMenu
            triggerLabel="Thao tác khác"
            items={[
              { key: "view", label: "Xem chi tiết", icon: "visibility" },
              { key: "export", label: "Xuất CSV", icon: "download" },
              { key: "cancel", label: "Huỷ hợp đồng", icon: "cancel", danger: true },
            ]}
          />
        </Row>
        <Drawer
          open={drawerOpen}
          onOpenChange={setDrawerOpen}
          title="Vùng trồng — Ea Kar, Đắk Lắk"
          footer={
            <Button size="sm" onClick={() => setDrawerOpen(false)}>
              Đóng
            </Button>
          }
        >
          POLYGON · Tự khai · Đã kiểm tra · Rủi ro thấp. Diện tích 2,4 ha, toạ độ trung tâm
          12.6963°N 108.0378°E.
        </Drawer>
      </Section>

      <Section title="StickyFormSummary">
        <div className="max-w-xs">
          <StickyFormSummary
            title="Tóm tắt hợp đồng"
            items={[
              { label: "Mặt hàng", value: "Cà phê Robusta" },
              { label: "Khối lượng", value: "20 tấn" },
              { label: "Đơn giá", value: "250.000.000 ₫/tấn" },
            ]}
            footer={<MoneyDisplay amount={5_000_000_000} sign="neutral" emphasis="large" />}
          />
        </div>
      </Section>

      <Section title="QueueListWithPanel — pattern Admin workflow">
        <QueueListWithPanel
          items={[
            {
              key: "case-1",
              title: "HĐ-2026-0142 · Cột mốc 2",
              subtitle: "Bất khả kháng — thiên tai",
              badge: <StatusBadge variant="warning">Chờ duyệt</StatusBadge>,
            },
            {
              key: "case-2",
              title: "HĐ-2026-0139 · Cột mốc 1",
              subtitle: "Bất khả kháng — dịch bệnh",
              badge: <StatusBadge variant="warning">Chờ duyệt</StatusBadge>,
            },
          ]}
          activeKey={activeQueueKey}
          onSelect={setActiveQueueKey}
          detail={
            <div className="text-sm text-text-secondary">
              Chi tiết vụ <span className="font-mono text-text-primary">{activeQueueKey}</span> —
              panel này sticky, không cuộn theo list bên trái.
            </div>
          }
        />
      </Section>

      <Section title="NotificationItem">
        <div className="max-w-lg divide-y divide-border rounded-md border-[0.5px] border-border bg-surface">
          <NotificationItem
            variant="danger"
            title="Không khoá được ký quỹ"
            description="Hợp đồng HĐ-2026-0142 đang chờ, chưa thực hiện."
            relativeTime="2 giờ trước"
            actionLabel="Thử khoá lại"
            unread
          />
          <NotificationItem
            variant="success"
            title="Đã khoá ký quỹ 5.000.000.000 ₫"
            description="Hợp đồng bắt đầu thực hiện."
            relativeTime="1 ngày trước"
            actionLabel="Xem ký quỹ"
          />
        </div>
      </Section>

      <Section title="Pagination">
        <div className="max-w-xs">
          <Pagination page={page} totalPages={5} onPageChange={setPage} />
        </div>
      </Section>

      <Section title="LineChartCard + BarChartCard (Recharts)">
        <div className="grid grid-cols-2 gap-4">
          <LineChartCard
            title="Điểm uy tín — 24 tháng"
            currentValue={92}
            averageValue={85.6}
            trendLabel="+14"
            data={[
              { label: "T7/24", value: 78 },
              { label: "T9/24", value: 81 },
              { label: "T11/24", value: 84 },
              { label: "T1/25", value: 83 },
              { label: "T3/25", value: 89 },
              { label: "T5/25", value: 90 },
              { label: "T7/25", value: 92 },
            ]}
          />
          <BarChartCard
            title="Tỷ lệ bẻ kèo theo ngành hàng"
            description="Cột cam = vượt ngưỡng an toàn (>5%)."
            averageValue={4.2}
            thresholdValue={5}
            data={[
              { label: "Cà phê", value: 4.1 },
              { label: "Gạo", value: 2.3 },
              { label: "Cao su", value: 6.8 },
              { label: "Điều", value: 3.5 },
            ]}
          />
        </div>
      </Section>

      <Section title="AppSidebar — User vs Admin (grouped)">
        <div className="flex flex-wrap items-start gap-6">
          <AppSidebar items={sidebarItems} />
          <AppSidebar items={adminSidebarItems} />
        </div>
      </Section>

      <Section title="TopBar">
        <TopBar reputationScore={88} hasUnreadNotifications />
      </Section>

      <Section title="PageLayout (shell đầy đủ, khung xem trước cao cố định)">
        <div className="h-[420px] overflow-hidden rounded-md border-[0.5px] border-border">
          <PageLayout
            sidebar={<AppSidebar items={sidebarItems} />}
            topBar={<TopBar reputationScore={88} hasUnreadNotifications />}
          >
            <div className="rounded-md border-[0.5px] border-border bg-surface p-5 text-sm text-text-secondary">
              Content area — max-width 1440px, 12 cột.
            </div>
          </PageLayout>
        </div>
      </Section>

      <Section title="Breadcrumb">
        <Breadcrumb
          items={[
            { label: "Hợp đồng", href: "#" },
            { label: "HĐ-2026-0142", href: "#", mono: true },
            { label: "Cột mốc 2" },
          ]}
        />
      </Section>

      <Section title="TabsUnderline">
        <TabsUnderline
          tabs={[
            { key: "overview", label: "Tổng quan" },
            { key: "milestones", label: "Cột mốc" },
            { key: "escrow", label: "Ký quỹ" },
            { key: "inspection", label: "Kiểm định" },
            { key: "documents", label: "Chứng từ" },
            { key: "audit", label: "Nhật ký" },
          ]}
          value={tab}
          onChange={setTab}
        />
      </Section>

      <Section title="SegmentedControl">
        <SegmentedControl
          options={[
            { key: "all", label: "Tất cả" },
            { key: "buyer", label: "Bên mua" },
            { key: "seller", label: "Bên bán" },
          ]}
          value={segment}
          onChange={setSegment}
        />
      </Section>

      <Section title="Stepper — wizard 5 bước (đang ở bước 2)">
        <Stepper
          steps={[
            "Thông tin chung",
            "Mặt hàng & khối lượng",
            "Cột mốc & giao hàng",
            "Điều khoản ký quỹ",
            "Xem lại & ký",
          ]}
          currentStep={2}
        />
      </Section>

      <Section title="IconButton">
        <Row label="36×36, luôn có aria-label">
          <IconButton icon="content_copy" aria-label="Sao chép" />
          <IconButton icon="download" aria-label="Tải xuống" />
          <IconButton icon="more_horiz" aria-label="Thao tác khác" />
        </Row>
      </Section>

      <Section title="Skeleton">
        <div className="max-w-sm space-y-2 rounded-md border-[0.5px] border-border bg-surface p-5">
          <Skeleton className="h-3.5 w-3/4" />
          <Skeleton className="h-3.5 w-1/2" />
        </div>
      </Section>

      <Section title="Tooltip">
        <Row label="Mở bằng hover hoặc focus (tap trên mobile)">
          <span className="flex items-center gap-1.5 text-sm text-text-secondary">
            Hao hụt vận chuyển (Delta 2)
            <Tooltip content="Chênh lệch giữa khối lượng seller cân và buyer cân lại — ngoài kiểm soát cả hai bên, không tính phạt.">
              <span className="ms text-base">help</span>
            </Tooltip>
          </span>
        </Row>
      </Section>

      <Section title="MilestoneTimeline">
        <div className="max-w-md rounded-md border-[0.5px] border-border bg-surface p-5">
          <MilestoneTimeline
            nodes={[
              {
                title: "Cột mốc 1 — Giao hàng đợt 1",
                meta: "28/05/2026 · Đã tất toán · 2.500.000.000 ₫",
                status: "done",
              },
              {
                title: "Cột mốc 2 — Giao hàng đợt 2",
                meta: "20/06/2026 · Chờ xác nhận · 2.500.000.000 ₫",
                status: "active",
              },
              { title: "Hoàn tất hợp đồng", meta: "Chưa tới", status: "upcoming" },
            ]}
          />
        </div>
      </Section>

      <Section title="PartnerCard + ReputationDonut">
        <div className="flex flex-wrap items-start gap-4">
          <PartnerCard name="HTX Nông nghiệp Ea Kar" orgType="Hợp tác xã" reputationScore={88} />
          <div className="flex items-center gap-4 rounded-md border-[0.5px] border-border bg-surface px-5 py-4">
            <ReputationDonut score={88} label="Uy tín tốt" />
          </div>
        </div>
      </Section>

      <Section title="DeltaComparisonBlock — Delta 1 & Delta 2 không gộp">
        <div className="max-w-3xl">
          <DeltaComparisonBlock
            rows={[
              {
                label: "Chênh lệch cam kết (Delta 1)",
                before: "20.000 kg",
                after: "19.680 kg",
                delta: "− 320 kg",
                consequence: "danger",
                consequenceLabel: "Có thể tính phạt",
              },
              {
                label: "Hao hụt vận chuyển (Delta 2)",
                before: "19.680 kg",
                after: "19.550 kg",
                delta: "− 130 kg",
                consequence: "neutral",
                consequenceLabel: "Không tính phạt",
              },
            ]}
          />
        </div>
      </Section>

      <Section title="ContentHashDisplay">
        <ContentHashDisplay hash="a3f9e1d4b7c8f2913e0da5c6b1298f74c21b7f80" />
      </Section>

      <Section title="DataTable — dùng chung cho ledger table">
        <DataTable<ContractRow>
          rowKey={(row) => row.id}
          columns={[
            { key: "id", header: "Mã HĐ", width: "1.2fr", render: (row) => <span className="font-mono">{row.id}</span> },
            { key: "partner", header: "Đối tác", render: (row) => row.partner },
            { key: "commodity", header: "Mặt hàng", render: (row) => row.commodity },
            {
              key: "value",
              header: "Giá trị",
              align: "right",
              render: (row) => <MoneyDisplay amount={row.value} sign="neutral" />,
            },
            {
              key: "status",
              header: "Trạng thái",
              align: "right",
              render: (row) => (
                <StatusBadge variant={row.status === "active" ? "info" : "success"}>
                  {row.status === "active" ? "Đang thực hiện" : "Đã tất toán"}
                </StatusBadge>
              ),
            },
          ]}
          rows={contractRows}
        />
      </Section>

      <Section title="OTPInput">
        <Row label={`Giá trị hiện tại: "${otp}"`}>
          <OTPInput value={otp} onChange={setOtp} />
        </Row>
      </Section>

      <Section title="Modal + DangerConfirmDialog">
        <Row label="Bấm để mở thật">
          <Button onClick={() => setModalOpen(true)}>Mở modal xác nhận ký</Button>
          <Button variant="destructive" onClick={() => setDangerOpen(true)}>
            Mở modal đóng băng hệ thống
          </Button>
        </Row>
        <Modal
          open={modalOpen}
          onOpenChange={setModalOpen}
          title="Xác nhận ký hợp đồng"
          footer={
            <>
              <Button variant="outline" size="sm" onClick={() => setModalOpen(false)}>
                Huỷ
              </Button>
              <Button size="sm" onClick={() => setModalOpen(false)}>
                Xác nhận
              </Button>
            </>
          }
        >
          Nhập mã OTP gửi tới số điện thoại đăng ký để hoàn tất ký hợp đồng.
        </Modal>
        <DangerConfirmDialog
          open={dangerOpen}
          onOpenChange={setDangerOpen}
          title="Đóng băng hệ thống"
          description="Mọi thao tác tiền trên toàn hệ thống sẽ tạm dừng ngay lập tức."
          confirmKeyword="ĐÓNG BĂNG"
          confirmLabel="Đóng băng hệ thống"
          onConfirm={() => {}}
        />
      </Section>

      <Section title="StatusBadge — 6 màu semantic">
        <Row label="Trạng thái cột mốc (§3.3)">
          <StatusBadge variant="neutral">Chưa bắt đầu</StatusBadge>
          <StatusBadge variant="info">Đang chuẩn bị hàng</StatusBadge>
          <StatusBadge variant="warning">Đã nhận, chờ xác nhận</StatusBadge>
          <StatusBadge variant="danger">Đang tranh chấp</StatusBadge>
          <StatusBadge variant="success">Đã tất toán</StatusBadge>
          <StatusBadge variant="primary">Đang thực hiện</StatusBadge>
        </Row>
      </Section>

      <Section title="DeadlinePill">
        <Row label="3 trạng thái hạn">
          <DeadlinePill status="due-soon" days={2} />
          <DeadlinePill status="overdue" days={1} />
          <DeadlinePill status="on-time" />
        </Row>
      </Section>

      <Section title="VerifyChip">
        <Row label="Xác thực chữ ký / nguồn báo cáo">
          <VerifyChip variant="verified" />
          <VerifyChip variant="unverified" />
          <VerifyChip variant="verified" label="Đã xác thực chữ ký — Vinacontrol" />
        </Row>
      </Section>

      <Section title="Banner — 4 biến thể">
        <div className="flex flex-col gap-2.5">
          <Banner variant="info">Hợp đồng HĐ-2026-0142 đã được ký bởi cả hai bên.</Banner>
          <Banner variant="warning">Cột mốc đang chờ rà soát bổ sung trước khi tất toán.</Banner>
          <Banner variant="danger" actionLabel="Thử khoá lại">
            Hợp đồng đã ký nhưng chưa khoá được ký quỹ.
          </Banner>
          <Banner variant="success">
            Đã khoá ký quỹ <MoneyDisplay amount={5_000_000_000} sign="neutral" /> . Hợp đồng bắt
            đầu thực hiện.
          </Banner>
        </div>
      </Section>

      <Section title="MoneyDisplay">
        <Row label="Sign + emphasis">
          <MoneyDisplay amount={2_500_000_000} sign="positive" />
          <MoneyDisplay amount={400_000_000} sign="negative" />
          <MoneyDisplay amount={500_000_000} sign="neutral" />
          <MoneyDisplay amount={5_000_000_000} sign="neutral" emphasis="large" />
        </Row>
      </Section>

      <Section title="MetricCard">
        <div className="grid grid-cols-4 gap-4">
          <MetricCard label="Hợp đồng đang hoạt động" value="14" />
          <MetricCard
            label="Tổng giá trị ký quỹ đang khoá"
            value={<MoneyDisplay amount={32_400_000_000} sign="neutral" emphasis="large" />}
          />
          <MetricCard label="Việc cần xử lý" value="3" valueClassName="text-warning" />
          <MetricCard label="Điểm uy tín" value="92" valueClassName="text-primary" />
        </div>
      </Section>

      <Section title="KeyValueList">
        <div className="max-w-md">
          <KeyValueList
            items={[
              { label: "Tiêu chuẩn chất lượng", value: "TCVN 4193:2014" },
              { label: "Dung sai khối lượng", value: "± 2%" },
              {
                label: "Hash hợp đồng",
                value: (
                  <span className="flex items-center gap-1.5 font-mono">
                    a3f9e1d4…c21b7f80
                    <span className="ms cursor-pointer text-base text-text-muted">
                      content_copy
                    </span>
                  </span>
                ),
              },
            ]}
          />
        </div>
      </Section>

      <Section title="ProgressBar — đơn sắc & xếp chồng">
        <div className="grid max-w-md grid-cols-1 gap-5">
          <ProgressBar segments={[{ value: 60, color: "bg-primary" }]} />
          <ProgressBar
            segments={[
              { value: 55, color: "bg-info" },
              { value: 35, color: "bg-success" },
              { value: 10, color: "bg-warning" },
            ]}
          />
        </div>
      </Section>

      <Section title="EmptyState / ErrorState">
        <div className="grid grid-cols-2 gap-4">
          <EmptyState
            title="Chưa có hợp đồng nào"
            description="Bắt đầu bằng cách tìm nguồn hàng phù hợp."
            actionLabel="Tìm nguồn hàng"
          />
          <EmptyState
            variant="error"
            title="Không tải được dữ liệu"
            description="Kiểm tra kết nối rồi thử lại."
            actionLabel="Thử lại"
          />
        </div>
      </Section>

      <Section title="Countdown">
        <Row label="OTP 05:00 (đang chạy thật, mở devtools mà chờ)">
          <Countdown seconds={292} />
        </Row>
      </Section>

      <Section title="Button (đã có sẵn — tham chiếu để so variant)">
        <Row label="4 variant">
          <Button>Ký hợp đồng</Button>
          <Button variant="outline">Xem chi tiết</Button>
          <Button variant="ghost">Tính lại</Button>
          <Button variant="destructive">Huỷ hợp đồng</Button>
        </Row>
      </Section>
    </div>
  )
}

export { ComponentShowcase }
