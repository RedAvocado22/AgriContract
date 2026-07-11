import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts"

import { cn } from "@/shared/lib/utils"
import type { ChartPoint } from "@/shared/ui/line-chart-card"

interface BarChartCardProps {
  title: string
  description?: string
  data: ChartPoint[]
  averageValue?: number
  /** Cột có value vượt ngưỡng này tô màu warning thay vì primary. */
  thresholdValue?: number
  height?: number
  className?: string
}

function BarChartCard({
  title,
  description,
  data,
  averageValue,
  thresholdValue,
  height = 220,
  className,
}: BarChartCardProps) {
  return (
    <div className={cn("rounded-md border-[0.5px] border-border bg-surface px-6 py-5", className)}>
      <div className="mb-1 text-[15px] font-medium text-text-primary">{title}</div>
      {description ? <div className="mb-3.5 text-[13px] text-text-secondary">{description}</div> : null}
      <ResponsiveContainer width="100%" height={height}>
        <BarChart data={data} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke="var(--color-surface-muted)" />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 12, fill: "var(--color-text-secondary)" }}
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
          <Bar dataKey="value" radius={[4, 4, 0, 0]}>
            {data.map((point) => (
              <Cell
                key={point.label}
                fill={
                  thresholdValue !== undefined && point.value > thresholdValue
                    ? "var(--color-warning)"
                    : "var(--color-primary)"
                }
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

export { BarChartCard }
