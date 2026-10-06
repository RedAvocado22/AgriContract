import { Link, useParams } from "react-router-dom"

import { Footer } from "@/shared/ui/footer"
import { LineChartCard } from "@/shared/ui/line-chart-card"
import { PublicNav } from "@/shared/ui/public-nav"

// ─── Mock Data ───────────────────────────────────────────────────────────────

const SELLER_DATA = {
  id: "htx-ea-kar",
  name: "HTX Nông nghiệp Ea Kar",
  type: "Hợp tác xã",
  province: "Đắk Lắk",
  role: "Bên bán",
  reputation: 88,
  reputationLabel: "Uy tín tốt",
  kycVerified: true,
  metrics: {
    completedContracts: 32,
    onTimeRate: "94%",
    violations: 0,
    memberSince: 2023,
  },
  chart: {
    current: 90,
    average: 85.3,
    trend: "+12",
    data: [
      { label: "T7/24", value: 78 },
      { label: "T10/24", value: 80 },
      { label: "T1/25", value: 82 },
      { label: "T4/25", value: 83 },
      { label: "T7/25", value: 85 },
      { label: "T10/25", value: 86 },
      { label: "T1/26", value: 87 },
      { label: "T3/26", value: 88 },
      { label: "T5/26", value: 89 },
      { label: "T6/26", value: 90 },
      { label: "T7/26", value: 90 },
    ],
  },
  events: [
    { title: "Tất toán hợp đồng cà phê", date: "28/05/2026", delta: "+2" },
    { title: "Giao dịch đúng hạn 6 tháng", date: "01/04/2026", delta: "+3" },
    { title: "Hoàn thành khai vùng trồng EUDR", date: "18/02/2026", delta: "+1" },
    { title: "Tất toán hợp đồng cà phê", date: "03/11/2025", delta: "+2" },
  ],
}

// ─── Component ───────────────────────────────────────────────────────────────

function PublicReputationPage() {
  const { userId } = useParams()
  // Trong thực tế sẽ fetch data bằng userId, hiện tại dùng mock
  const data = SELLER_DATA

  return (
    <div className="min-h-screen bg-page-bg text-text-primary flex flex-col">
      <title>Hồ sơ uy tín — AgriContract</title>
      <PublicNav />

      <div className="mx-auto w-full max-w-[1000px] px-10 pt-6 pb-20 flex-1">
        {/* Breadcrumb */}
        <div className="mb-[18px] text-[13px] text-text-secondary">
          <Link to="/listing" className="hover:text-text-primary hover:underline">
            Sàn nguồn hàng
          </Link>{" "}
          <span className="mx-1.5 text-border-strong">/</span> Hồ sơ uy tín công khai
        </div>

        {/* Public notice */}
        <div className="mb-5 flex items-center gap-2.5 rounded-[10px] bg-surface-muted px-4 py-[11px]">
          <span className="ms text-[18px] text-text-secondary">public</span>
          <span className="text-[13px] text-text-secondary">
            Bản tóm tắt công khai — mọi đối tác đều xem được trước khi ký. Số liệu chi tiết chỉ
            hiển thị cho chủ hồ sơ.
          </span>
        </div>

        {/* Header Card */}
        <div className="mb-5 flex items-center gap-8 rounded-xl border-[0.5px] border-border bg-surface p-7">
          {/* Custom large donut for 120px */}
          <div className="relative shrink-0 w-[120px] h-[120px]">
            <div
              className="flex h-[120px] w-[120px] items-center justify-center rounded-full"
              style={{
                background: `conic-gradient(var(--color-primary) 0% ${data.reputation}%, var(--color-surface-muted) ${data.reputation}% 100%)`,
              }}
            >
              <div className="flex h-[92px] w-[92px] flex-col items-center justify-center rounded-full bg-surface">
                <div className="text-[32px] font-medium leading-none">{data.reputation}</div>
                <div className="text-xs text-text-muted">/ 100</div>
              </div>
            </div>
          </div>

          <div className="flex-1">
            <div className="mb-1 text-[20px] font-medium">{data.name}</div>
            <div className="mb-3 text-sm text-text-secondary">
              {data.type} · {data.province} · {data.role}
            </div>
            <div className="flex items-center gap-2">
              <span className="inline-flex rounded-full bg-primary-tint px-3.5 py-1.5 text-sm font-medium text-primary">
                {data.reputationLabel}
              </span>
              {data.kycVerified && (
                <span className="inline-flex items-center gap-1.5 rounded-full bg-surface-muted px-3 py-1.5 text-[13px] text-text-secondary">
                  <span className="ms text-[15px]">verified</span> Đã xác minh KYC
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Metrics Row */}
        <div className="mb-5 grid grid-cols-4 gap-4">
          <div className="rounded-xl bg-surface-muted px-5 py-4">
            <div className="mb-2 text-[13px] text-text-secondary">Hợp đồng hoàn thành</div>
            <div className="text-2xl font-medium">{data.metrics.completedContracts}</div>
          </div>
          <div className="rounded-xl bg-surface-muted px-5 py-4">
            <div className="mb-2 text-[13px] text-text-secondary">Tỷ lệ đúng hạn</div>
            <div className="text-2xl font-medium">{data.metrics.onTimeRate}</div>
          </div>
          <div className="rounded-xl bg-surface-muted px-5 py-4">
            <div className="mb-2 text-[13px] text-text-secondary">Số lần vi phạm</div>
            <div className="text-2xl font-medium">{data.metrics.violations}</div>
          </div>
          <div className="rounded-xl bg-surface-muted px-5 py-4">
            <div className="mb-2 text-[13px] text-text-secondary">Thành viên từ</div>
            <div className="text-2xl font-medium">{data.metrics.memberSince}</div>
          </div>
        </div>

        {/* Two Cards Layout */}
        <div className="mb-4 grid grid-cols-[1.3fr_1fr] items-start gap-5">
          {/* Line Chart */}
          <LineChartCard
            title="Điểm uy tín — 24 tháng"
            currentValue={data.chart.current}
            averageValue={data.chart.average}
            trendLabel={data.chart.trend}
            data={data.chart.data}
            className="rounded-xl p-6"
            height={240}
          />

          {/* Event Timeline */}
          <div className="rounded-xl border-[0.5px] border-border bg-surface p-6">
            <div className="mb-4 text-base font-medium">Sự kiện uy tín gần đây</div>
            <div className="flex flex-col">
              {data.events.map((ev, i) => (
                <div
                  key={i}
                  className={`flex items-start justify-between py-2.5 ${
                    i !== data.events.length - 1 ? "border-b-[0.5px] border-border" : ""
                  }`}
                >
                  <div>
                    <div className="text-sm">{ev.title}</div>
                    <div className="text-[13px] text-text-muted">{ev.date}</div>
                  </div>
                  <span className="font-mono text-sm text-success">{ev.delta}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Disclaimer */}
        <div className="text-[13px] text-text-muted">
          Vi phạm được tính trong cửa sổ 24 tháng. Uy tín hai chiều — bên bán cũng xem được uy tín
          bên mua trước khi ký.
        </div>
      </div>

      <Footer />
    </div>
  )
}

export { PublicReputationPage }
