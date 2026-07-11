import { Fragment } from "react"

import { cn } from "@/shared/lib/utils"

interface VerticalStepperProps {
  steps: string[]
  /** 1-indexed. */
  currentStep: number
  /** Badge cạnh label bước hiện tại, vd "Đang nhập". */
  currentBadgeLabel?: string
  className?: string
}

/** Thay Stepper ngang trên mobile — 5 bước ngang tràn khỏi 390px (brief Mobile §6). */
function VerticalStepper({ steps, currentStep, currentBadgeLabel = "Đang nhập", className }: VerticalStepperProps) {
  return (
    <div className={cn("flex gap-3.5", className)}>
      <div className="flex flex-col items-center">
        {steps.map((_, index) => {
          const stepNumber = index + 1
          const isDone = stepNumber < currentStep
          const isCurrent = stepNumber === currentStep
          const isLast = stepNumber === steps.length

          return (
            <Fragment key={stepNumber}>
              <div
                className={cn(
                  "flex size-[26px] shrink-0 items-center justify-center rounded-full text-[13px] font-medium",
                  isDone
                    ? "bg-primary text-white"
                    : isCurrent
                      ? "border-[1.5px] border-primary bg-surface text-primary"
                      : "border-[1.5px] border-border-strong bg-surface text-text-muted"
                )}
              >
                {isDone ? <span className="ms text-base">check</span> : stepNumber}
              </div>
              {!isLast ? (
                <div
                  className={cn("h-[26px] w-[1.5px]", stepNumber < currentStep ? "bg-primary" : "bg-border")}
                />
              ) : null}
            </Fragment>
          )
        })}
      </div>
      <div className="flex flex-1 flex-col">
        {steps.map((label, index) => {
          const stepNumber = index + 1
          const isDone = stepNumber < currentStep
          const isCurrent = stepNumber === currentStep
          const isLast = stepNumber === steps.length

          return (
            <Fragment key={label}>
              <div className="flex h-[26px] items-center gap-2">
                <span
                  className={cn(
                    "text-sm",
                    isCurrent ? "font-medium text-primary" : isDone ? "text-text-primary" : "text-text-muted"
                  )}
                >
                  {label}
                </span>
                {isCurrent ? (
                  <span className="rounded-full bg-primary-tint px-2 py-0.5 text-[11.5px] text-primary">
                    {currentBadgeLabel}
                  </span>
                ) : null}
              </div>
              {!isLast ? <div className="h-[26px]" /> : null}
            </Fragment>
          )
        })}
      </div>
    </div>
  )
}

export { VerticalStepper }
