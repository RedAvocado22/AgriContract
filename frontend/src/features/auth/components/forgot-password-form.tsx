import { useEffect, useState } from "react"
import { useForm, Controller } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"

import { Button } from "@/shared/ui/button"
import { OTPInput } from "@/shared/ui/otp-input"
import { TextInput } from "@/shared/ui/text-input"
import { getPasswordStrength, strengthColors, strengthLabels } from "@/features/auth/lib/password"
import { MOCK_OTP_CODE } from "@/features/auth/lib/mock-auth"

// ─── ZOD SCHEMAS ─────────────────────────────────────────────────────────────

const requestSchema = z.object({
  identifier: z.string().min(1, "Vui lòng nhập số điện thoại hoặc email."),
})

type RequestFormData = z.infer<typeof requestSchema>

const resetSchema = z
  .object({
    code: z.string().length(6, "Mã xác nhận gồm 6 chữ số."),
    password: z.string().min(8, "Mật khẩu tối thiểu 8 ký tự."),
    confirmPassword: z.string().min(1, "Vui lòng nhập lại mật khẩu mới."),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Mật khẩu nhập lại không khớp.",
    path: ["confirmPassword"],
  })

type ResetFormData = z.infer<typeof resetSchema>

// ─── Step 1: Request reset ────────────────────────────────────────────────────

function Step1Request({
  onNext,
  onLogin,
}: {
  onNext: (identifier: string) => void
  onLogin: () => void
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RequestFormData>({
    resolver: zodResolver(requestSchema),
    defaultValues: { identifier: "" },
  })

  function onSubmit(data: RequestFormData) {
    onNext(data.identifier)
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-1 flex-col justify-center px-14 py-[52px]">
      <div className="mb-1 text-[22px] font-medium text-text-primary">Quên mật khẩu</div>
      <div className="mb-7 text-sm leading-[1.6] text-text-secondary">
        Nhập số điện thoại hoặc email đã đăng ký. Chúng tôi sẽ gửi mã xác nhận 6 số để đặt lại mật khẩu.
      </div>

      <TextInput
        id="forgot-identifier"
        label="Số điện thoại hoặc email"
        placeholder="0912 345 678"
        containerClassName="mb-6"
        error={errors.identifier?.message}
        {...register("identifier")}
      />

      <Button type="submit" className="mb-4 h-[46px] w-full rounded-[9px] text-[15px]">
        Gửi mã xác nhận
      </Button>
      <div className="text-center text-sm">
        <button type="button" onClick={onLogin} className="text-primary hover:underline cursor-pointer">
          ← Quay lại đăng nhập
        </button>
      </div>
    </form>
  )
}

// ─── Step 2: Input code & new password ───────────────────────────────────────

function Step2Reset({
  onSuccess,
  onResend,
  onLogin,
}: {
  onSuccess: () => void
  onResend: () => void
  onLogin: () => void
}) {
  const [countdown, setCountdown] = useState(300) // 5:00
  const [resetKey, setResetKey] = useState(0) // Trigger reset interval
  const [otpError, setOtpError] = useState<string | null>(null)
  const [attemptsRemaining, setAttemptsRemaining] = useState(3)

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors },
  } = useForm<ResetFormData>({
    resolver: zodResolver(resetSchema),
    defaultValues: { code: "", password: "", confirmPassword: "" },
  })

  const passwordVal = watch("password") || ""
  const strength = getPasswordStrength(passwordVal)

  // Countdown timer: Chỉ khởi tạo lại khi resetKey thay đổi, không chạy lại mỗi giây
  useEffect(() => {
    setCountdown(300)
    setAttemptsRemaining(3)
    setOtpError(null)

    const timer = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) {
          clearInterval(timer)
          return 0
        }
        return c - 1
      })
    }, 1000)

    return () => clearInterval(timer)
  }, [resetKey])

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`
  }

  function onSubmit(data: ResetFormData) {
    setOtpError(null)

    // TODO: Connect to backend OTP verification API
    if (data.code !== MOCK_OTP_CODE) {
      const nextAttempts = attemptsRemaining - 1
      setAttemptsRemaining(nextAttempts)
      if (nextAttempts <= 0) {
        setOtpError("Đã hết số lần thử. Vui lòng yêu cầu lại mã.")
      } else {
        setOtpError(`Mã xác nhận không đúng. Còn ${nextAttempts} lần thử.`)
      }
      return
    }

    onSuccess()
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-1 flex-col justify-center px-14 py-11">
      <div className="mb-1 text-[22px] font-medium text-text-primary">Đặt lại mật khẩu</div>
      <div className="mb-6 text-sm text-text-secondary">
        Nhập mã xác nhận vừa gửi và chọn mật khẩu mới.
      </div>

      {/* OTP Error banner */}
      {otpError && (
        <div className="mb-5 flex items-start gap-2.5 rounded-[9px] bg-danger-tint px-3.5 py-3">
          <span className="ms mt-px text-[18px] text-danger">error</span>
          <span className="text-[12.5px] leading-[1.5] text-danger-strong">{otpError}</span>
        </div>
      )}

      {/* OTP Input and Countdown */}
      <div className="mb-4">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-[13px] font-medium text-text-primary">Mã xác nhận</span>
          <span className="font-mono text-[13px] text-text-secondary">{formatTime(countdown)}</span>
        </div>
        <Controller
          name="code"
          control={control}
          render={({ field }) => (
            <OTPInput
              value={field.value}
              onChange={field.onChange}
              className={otpError ? "[&_input]:border-danger" : ""}
            />
          )}
        />
        {errors.code && <div className="mt-1.5 text-[13px] text-danger">{errors.code.message}</div>}
      </div>

      {/* New Password */}
      <div className="mb-4 flex flex-col">
        <TextInput
          id="new-password"
          label="Mật khẩu mới"
          type="password"
          placeholder="••••••••••"
          error={errors.password?.message}
          {...register("password")}
        />
        {/* Strength Bar */}
        <div className="mt-2.5 flex gap-1.5">
          {[1, 2, 3, 4].map((seg) => (
            <div
              key={seg}
              className="h-1 flex-1 rounded-sm transition-colors"
              style={{ background: seg <= strength ? strengthColors[strength] : "#E2E8F0" }}
            />
          ))}
        </div>
        {passwordVal && (
          <div className="mt-1 text-[11px] text-text-muted">{strengthLabels[strength]}</div>
        )}
      </div>

      {/* Confirm Password */}
      <div className="mb-6">
        <TextInput
          id="confirm-password"
          label="Nhập lại mật khẩu mới"
          type="password"
          placeholder="••••••••••"
          error={errors.confirmPassword?.message}
          {...register("confirmPassword")}
        />
      </div>

      <Button
        type="submit"
        disabled={attemptsRemaining <= 0}
        className="mb-4 h-[46px] w-full rounded-[9px] text-[15px]"
      >
        Đặt lại mật khẩu
      </Button>

      <div className="text-center text-[13px] text-text-secondary">
        Chưa nhận được mã?{" "}
        <button
          type="button"
          onClick={() => {
            setResetKey((k) => k + 1)
            onResend()
          }}
          className="text-primary hover:underline cursor-pointer"
        >
          Gửi lại
        </button>
      </div>

      <div className="mt-4 text-center text-sm">
        <button type="button" onClick={onLogin} className="text-text-muted hover:text-text-primary cursor-pointer">
          ← Quay lại đăng nhập
        </button>
      </div>
    </form>
  )
}

// ─── Main ForgotPasswordForm ──────────────────────────────────────────────────

interface ForgotPasswordFormProps {
  step: number
  onStepChange: (step: number) => void
  onIdentifierChange: (id: string) => void
  onSuccess: () => void
  onLogin: () => void
}

function ForgotPasswordForm({
  step,
  onStepChange,
  onIdentifierChange,
  onSuccess,
  onLogin,
}: ForgotPasswordFormProps) {
  function handleRequestSuccess(id: string) {
    onIdentifierChange(id)
    onStepChange(2)
  }

  function handleResetSuccess() {
    onSuccess()
  }

  return (
    <>
      {step === 1 ? (
        <Step1Request onNext={handleRequestSuccess} onLogin={onLogin} />
      ) : (
        <Step2Reset
          onSuccess={handleResetSuccess}
          onResend={() => {
            // Mock re-sent notification
          }}
          onLogin={onLogin}
        />
      )}
    </>
  )
}

export { ForgotPasswordForm }
export type { ForgotPasswordFormProps }
