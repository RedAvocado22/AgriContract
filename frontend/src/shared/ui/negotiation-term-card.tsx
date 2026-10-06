import { cn } from "@/shared/lib/utils"

interface NegotiationTermCardProps {
  termLabel: string
  changed?: boolean
  changeDirection?: "up" | "down"
  yourValue: string
  sliderMin: number
  sliderMax: number
  sliderStep?: number
  sliderValue: number
  onSliderChange: (value: number) => void
  floorLabel: string
  ceilingLabel: string
  partnerValue: string
  partnerPreviousValue?: string
  className?: string
}

/** Thay bảng đàm phán 2 cột trên mobile — mỗi điều khoản 1 thẻ (brief Mobile §15). */
function NegotiationTermCard({
  termLabel,
  changed,
  changeDirection = "up",
  yourValue,
  sliderMin,
  sliderMax,
  sliderStep = 0.5,
  sliderValue,
  onSliderChange,
  floorLabel,
  ceilingLabel,
  partnerValue,
  partnerPreviousValue,
  className,
}: NegotiationTermCardProps) {
  return (
    <div
      className={cn(
        "rounded-[14px] border-[0.5px] p-4",
        changed ? "border-[#FDE68A] bg-[#FEFCE8]" : "border-border bg-surface",
        className
      )}
    >
      <div className="mb-3.5 flex items-center gap-2">
        <span className="text-sm font-medium text-text-primary">{termLabel}</span>
        {changed ? (
          <span className="ml-auto inline-flex items-center gap-1 text-xs whitespace-nowrap text-warning">
            <span className="ms text-sm">
              {changeDirection === "up" ? "arrow_upward" : "arrow_downward"}
            </span>
            đối tác {changeDirection === "up" ? "tăng" : "giảm"}
          </span>
        ) : null}
      </div>
      <div>
        <div className="mb-2 flex items-baseline justify-between">
          <span className="inline-flex items-center gap-1.5 text-[13px] font-medium text-primary">
            <span className="ms text-[15px]">person</span> Bạn đề xuất
          </span>
          <span className="font-mono text-lg font-medium text-text-primary">{yourValue}</span>
        </div>
        <input
          type="range"
          min={sliderMin}
          max={sliderMax}
          step={sliderStep}
          value={sliderValue}
          onChange={(event) => onSliderChange(Number(event.target.value))}
          className="w-full accent-primary"
        />
        <div className="mt-1 flex justify-between font-mono text-[11px] text-text-muted">
          <span>{floorLabel}</span>
          <span>{ceilingLabel}</span>
        </div>
      </div>
      <div
        className={cn(
          "mt-3.5 flex items-baseline justify-between border-t-[0.5px] pt-3",
          changed ? "border-[#FDE68A]" : "border-border"
        )}
      >
        <span className="inline-flex items-center gap-1.5 text-[13px] text-text-secondary">
          <span className="ms text-[15px]">handshake</span> Đối tác đề xuất
        </span>
        <span className="text-right">
          <span
            className={cn(
              "font-mono text-lg font-medium",
              changed ? "text-warning-strong" : "text-text-primary"
            )}
          >
            {partnerValue}
          </span>
          {partnerPreviousValue ? (
            <span className="block text-[11.5px] text-text-muted">
              {changed ? `trước đó ${partnerPreviousValue}` : partnerPreviousValue}
            </span>
          ) : null}
        </span>
      </div>
    </div>
  )
}

export { NegotiationTermCard }
