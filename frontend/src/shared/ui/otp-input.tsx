import { useRef, type ClipboardEvent, type KeyboardEvent } from "react"

import { cn } from "@/shared/lib/utils"

interface OTPInputProps {
  length?: number
  value: string
  onChange: (value: string) => void
  className?: string
}

function OTPInput({ length = 6, value, onChange, className }: OTPInputProps) {
  const inputsRef = useRef<(HTMLInputElement | null)[]>([])

  const handleChange = (index: number, raw: string) => {
    const digit = raw.replace(/\D/g, "").slice(-1)
    const chars = value.split("")
    chars[index] = digit
    onChange(chars.join("").slice(0, length))
    if (digit && index < length - 1) {
      inputsRef.current[index + 1]?.focus()
    }
  }

  const handleKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Backspace" && !value[index] && index > 0) {
      inputsRef.current[index - 1]?.focus()
    }
  }

  const handlePaste = (event: ClipboardEvent<HTMLInputElement>) => {
    const pasted = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, length)
    if (!pasted) return
    event.preventDefault()
    onChange(pasted)
    inputsRef.current[Math.min(pasted.length, length - 1)]?.focus()
  }

  return (
    <div data-slot="otp-input" className={cn("flex gap-2", className)}>
      {Array.from({ length }).map((_, index) => (
        <input
          key={index}
          ref={(el) => {
            inputsRef.current[index] = el
          }}
          value={value[index] ?? ""}
          onChange={(event) => handleChange(index, event.target.value)}
          onKeyDown={(event) => handleKeyDown(index, event)}
          onPaste={handlePaste}
          inputMode="numeric"
          maxLength={1}
          className="h-11 w-10 rounded-sm border-[0.5px] border-border text-center font-mono text-base text-text-primary focus:border-[1.5px] focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none"
        />
      ))}
    </div>
  )
}

export { OTPInput }
