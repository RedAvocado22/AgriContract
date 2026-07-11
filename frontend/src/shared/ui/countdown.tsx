import { useEffect, useRef, useState, type ComponentProps } from "react"

import { cn } from "@/shared/lib/utils"

interface CountdownProps extends Omit<ComponentProps<"span">, "children"> {
  /** Tổng số giây đếm ngược. Đổi giá trị này sẽ reset đồng hồ (vd gửi lại OTP). */
  seconds: number
  onExpire?: () => void
}

function formatTime(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`
}

function Countdown({ seconds, onExpire, className, ...props }: CountdownProps) {
  const [remaining, setRemaining] = useState(seconds)
  const expiredRef = useRef(false)

  useEffect(() => {
    setRemaining(seconds)
    expiredRef.current = false
  }, [seconds])

  useEffect(() => {
    const timer = setInterval(() => {
      setRemaining((prev) => (prev > 0 ? prev - 1 : 0))
    }, 1000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    if (remaining === 0 && !expiredRef.current) {
      expiredRef.current = true
      onExpire?.()
    }
  }, [remaining, onExpire])

  return (
    <span
      data-slot="countdown"
      className={cn("font-mono text-xl font-medium tabular-nums text-text-primary", className)}
      {...props}
    >
      {formatTime(remaining)}
    </span>
  )
}

export { Countdown }
