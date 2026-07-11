import { Fragment } from "react"

import { cn } from "@/shared/lib/utils"

interface StepperProps {
  steps: string[]
  /** 1-indexed. Các bước <= currentStep hiện đầy màu xanh (đã qua hoặc đang ở đó). */
  currentStep: number
  className?: string
}

function Stepper({ steps, currentStep, className }: StepperProps) {
  return (
    <div data-slot="stepper" className={cn("flex items-center", className)}>
      {steps.map((label, index) => {
        const stepNumber = index + 1
        const isReached = stepNumber <= currentStep
        const isLast = stepNumber === steps.length

        return (
          <Fragment key={label}>
            <div className="flex flex-col items-center gap-1.5">
              <div
                className={cn(
                  "flex size-7 items-center justify-center rounded-full text-[13px] font-medium",
                  isReached
                    ? "bg-primary text-white"
                    : "border-[1.5px] border-border-strong bg-surface text-text-muted"
                )}
              >
                {stepNumber}
              </div>
              <div
                className={cn(
                  "text-[13px] whitespace-nowrap",
                  isReached ? "text-text-primary" : "text-text-muted"
                )}
              >
                {label}
              </div>
            </div>
            {!isLast ? (
              <div
                className={cn(
                  "mx-2 mb-[22px] h-px flex-1",
                  stepNumber < currentStep ? "bg-primary" : "bg-border"
                )}
              />
            ) : null}
          </Fragment>
        )
      })}
    </div>
  )
}

export { Stepper }
