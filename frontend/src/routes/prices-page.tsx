import { useState } from "react"
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"

import { cn } from "@/shared/lib/utils"
import { Footer } from "@/shared/ui/footer"
import { PublicNav } from "@/shared/ui/public-nav"
import { SegmentedControl } from "@/shared/ui/segmented-control"
import { SelectDropdown } from "@/shared/ui/select-dropdown"

// ─── Mock Data ───────────────────────────────────────────────────────────────

const PRICE_DATA = {
  currentPrice: "118.500 ₫/kg",
  date: "08/07/2026",
  trend: "+2,1% (90 ngày)",
  source: "Nguồn: VNSAT",
  average: 116200,
  chart: [
    { label: "Th4", value: 116400 },
    { label: "Th5", value: 117000 },
    { label: "Th6", value: 116800 },
    { label: "Th7", value: 118500 },
  ],
  table: [
    { date: "08/07", price: "118.500", change: "+0,8%", isPositive: true },
    { date: "07/07", price: "117.600", change: "+0,3%", isPositive: true },
    { date: "06/07", price: "117.200", change: "−0,5%", isPositive: false },
    { date: "05/07", price: "117.800", change: "+1,2%", isPositive: true },
    { date: "04/07", price: "116.400", change: "0,0%", isPositive: null },
  ],
}

// ─── Component ───────────────────────────────────────────────────────────────

function PricesPage() {
  const [commodity, setCommodity] = useState<string | null>("coffee_robusta")
  const [province, setProvince] = useState<string | null>("dak_lak")
  const [timeRange, setTimeRange] = useState<string>("90")

  const data = PRICE_DATA

  return (
    <div className="min-h-screen bg-page-bg text-text-primary flex flex-col">
      <title>Bảng giá tham khảo — AgriContract</title>
      <PublicNav />

      <div className="mx-auto w-full max-w-[1200px] px-8 pt-7 pb-20 flex-1">
        <div className="mb-1 text-[22px] font-medium">Bảng giá tham khảo</div>
        <div className="mb-5 text-sm text-text-secondary">
          Giá tham khảo, không phải giá giao dịch trên nền tảng.
        </div>

        {/* Selectors */}
        <div className="mb-5 flex flex-wrap items-center gap-2.5">
          <div className="flex items-center gap-2">
            <span className="text-sm text-text-secondary">Mặt hàng:</span>
            <SelectDropdown
              value={commodity}
              onChange={setCommodity}
              options={[
                { value: "coffee_robusta", label: "Cà phê Robusta" },
                { value: "coffee_arabica", label: "Cà phê Arabica" },
                { value: "rubber", label: "Mủ cao su" },
              ]}
              className="w-[180px]"
            />
          </div>
          <div className="flex items-center gap-2 ml-2">
            <span className="text-sm text-text-secondary">Tỉnh:</span>
            <SelectDropdown
              value={province}
              onChange={setProvince}
              options={[
                { value: "dak_lak", label: "Đắk Lắk" },
                { value: "lam_dong", label: "Lâm Đồng" },
                { value: "dak_nong", label: "Đắk Nông" },
              ]}
              className="w-[140px]"
            />
          </div>
          <div className="flex-1" />
          <SegmentedControl
            value={timeRange}
            onChange={setTimeRange}
            options={[
              { key: "30", label: "30 ngày" },
              { key: "90", label: "90 ngày" },
              { key: "365", label: "365 ngày" },
            ]}
          />
        </div>

        {/* Layout */}
        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[1.7fr_1fr]">
          {/* Chart Card */}
          <div className="rounded-xl border-[0.5px] border-border bg-surface p-6">
            <div className="mb-5 flex items-end justify-between">
              <div>
                <div className="mb-1.5 text-[13px] text-text-secondary">
                  Giá mới nhất · {data.date}
                </div>
                <div className="flex items-baseline gap-2.5">
                  <div className="font-mono text-[26px] font-medium leading-none">
                    {data.currentPrice}
                  </div>
                  <span className="inline-flex items-center gap-1 text-[13px] font-medium text-success">
                    <span className="ms text-[16px]">arrow_upward</span> {data.trend}
                  </span>
                </div>
              </div>
              <span className="rounded-full bg-surface-muted px-2.5 py-1 text-[13px] font-medium text-text-secondary">
                {data.source}
              </span>
            </div>

            {/* Recharts Area */}
            <div className="h-[260px] w-full -ml-4">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={data.chart} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                  <defs>
                    <linearGradient id="priceGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--color-primary)" stopOpacity={0.16} />
                      <stop offset="100%" stopColor="var(--color-primary)" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--color-surface-muted)" />
                  <XAxis
                    dataKey="label"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 11, fill: "var(--color-text-muted)" }}
                    dy={10}
                  />
                  <YAxis
                    domain={["dataMin - 2000", "dataMax + 2000"]}
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 11, fill: "var(--color-text-muted)", fontFamily: "monospace" }}
                    tickFormatter={(val) => `${val / 1000}k`}
                  />
                  <Tooltip
                    contentStyle={{ borderRadius: "8px", border: "0.5px solid var(--color-border)", fontSize: "13px", boxShadow: "0 4px 6px -1px rgb(0 0 0 / 0.1)" }}
                    itemStyle={{ color: "var(--color-primary)", fontWeight: 500 }}
                    formatter={(val: number) => [`${val.toLocaleString("vi-VN")} ₫/kg`, "Giá"]}
                  />
                  <ReferenceLine y={data.average} stroke="var(--color-text-muted)" strokeDasharray="4 4" />
                  <Area
                    type="monotone"
                    dataKey="value"
                    stroke="var(--color-primary)"
                    strokeWidth={2.5}
                    fill="url(#priceGradient)"
                    activeDot={{ r: 5, fill: "var(--color-primary)", stroke: "var(--color-surface)", strokeWidth: 2 }}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>

            <div className="mt-3 border-t-[0.5px] border-border pt-3 text-[12px] text-text-muted">
              Với gạo, có thêm bộ lọc <span className="text-text-secondary">giống lúa</span> (OM 18 ·
              ST25 · IR 50404) — cùng tỉnh cùng ngày, khác giống thì giá khác hẳn.
            </div>
          </div>

          {/* Table Card */}
          <div className="overflow-hidden rounded-xl border-[0.5px] border-border bg-surface">
            <div className="border-b-[0.5px] border-border px-5 py-3.5 text-[15px] font-medium">
              Giá theo ngày
            </div>
            {/* Header */}
            <div className="grid grid-cols-[1fr_1.2fr_0.9fr] gap-2 bg-surface-muted px-5 py-2.5 text-[13px] text-text-secondary">
              <div>Ngày</div>
              <div className="text-right">Giá (₫/kg)</div>
              <div className="text-right">Thay đổi</div>
            </div>
            {/* Rows */}
            <div className="flex flex-col">
              {data.table.map((row, i) => (
                <div
                  key={i}
                  className={cn(
                    "grid grid-cols-[1fr_1.2fr_0.9fr] items-center gap-2 border-t-[0.5px] border-border px-5 py-[11px] text-sm",
                    i % 2 === 1 ? "bg-page-bg/50" : ""
                  )}
                >
                  <div className="font-mono text-[13px]">{row.date}</div>
                  <div className="text-right font-mono">{row.price}</div>
                  <div
                    className={cn(
                      "text-right text-[13px]",
                      row.isPositive === true && "text-success",
                      row.isPositive === false && "text-destructive",
                      row.isPositive === null && "text-text-secondary"
                    )}
                  >
                    {row.change}
                  </div>
                </div>
              ))}
            </div>
            {/* Source */}
            <div className="flex items-center gap-2 border-t-[0.5px] border-border px-5 py-[11px]">
              <span className="rounded-full bg-surface-muted px-2.5 py-1 text-xs font-medium text-text-secondary">
                VNSAT
              </span>
              <span className="text-xs text-text-muted">Cà phê thu thập tự động theo ngày</span>
            </div>
          </div>
        </div>

        <div className="mt-5 flex items-center gap-1.5 text-[12.5px] text-text-muted">
          <span className="ms text-[16px]">info</span> Giá tham khảo, không phải giá giao dịch trên
          nền tảng. Cao su và điều dùng giá nhập tay của quản trị viên.
        </div>
      </div>

      <Footer />
    </div>
  )
}

export { PricesPage }
