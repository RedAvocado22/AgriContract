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

interface ChartPoint {
  label: string
  value: number
}

interface LineChartCardProps {
  title: string
  currentValue?: number
  averageValue?: number
  /** vd "+14" — pill trend góc phải header. */
  trendLabel?: string
  data: ChartPoint[]
  height?: number
  className?: string
}

function LineChartCard({
  title,
  currentValue,
  averageValue,
  trendLabel,
  data,
  height = 220,
  className,
}: LineChartCardProps) {
  return (
    <div className={cn("rounded-md border-[0.5px] border-border bg-surface px-6 py-5", className)}>
      <div className="mb-1 flex items-baseline justify-between">
        <div className="text-[15px] font-medium text-text-primary">{title}</div>
        {trendLabel ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-primary-tint px-2.5 py-0.5 text-xs font-medium text-primary">
            <span className="ms text-sm">trending_up</span>
            {trendLabel}
          </span>
        ) : null}
      </div>
      {currentValue !== undefined || averageValue !== undefined ? (
        <div className="mb-3.5 text-[13px] text-text-secondary">
          {currentValue !== undefined ? (
            <>
              Hiện tại <span className="font-mono text-text-primary">{currentValue}</span>
            </>
          ) : null}
          {averageValue !== undefined ? (
            <> · trung bình kỳ <span className="font-mono">{averageValue}</span></>
          ) : null}
        </div>
      ) : null}
      <ResponsiveContainer width="100%" height={height}>
        <AreaChart data={data} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
          <defs>
            <linearGradient id="lineChartCardFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--color-primary)" stopOpacity={0.18} />
              <stop offset="100%" stopColor="var(--color-primary)" stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid vertical={false} stroke="var(--color-surface-muted)" />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 11, fill: "var(--color-text-muted)" }}
            axisLine={{ stroke: "var(--color-border)" }}
            tickLine={false}
          />
          <YAxis
            tick={{ fontSize: 11, fill: "var(--color-text-muted)" }}
            axisLine={false}
            tickLine={false}
            width={36}
          />
          {averageValue !== undefined ? (
            <ReferenceLine y={averageValue} stroke="var(--color-text-muted)" strokeDasharray="4 4" />
          ) : null}
          <Tooltip
            contentStyle={{
              fontSize: 12,
              borderRadius: 8,
              border: "0.5px solid var(--color-border)",
            }}
          />
          <Area
            type="monotone"
            dataKey="value"
            stroke="var(--color-primary)"
            strokeWidth={2.5}
            fill="url(#lineChartCardFill)"
            dot={{ r: 3, fill: "var(--color-surface)", stroke: "var(--color-primary)", strokeWidth: 2 }}
            activeDot={{ r: 5 }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}

export { LineChartCard }
export type { ChartPoint }
