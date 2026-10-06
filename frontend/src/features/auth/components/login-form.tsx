import { useState } from "react"
import { useForm, Controller } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"

import { Button } from "@/shared/ui/button"
import { Checkbox } from "@/shared/ui/checkbox"
import { TextInput } from "@/shared/ui/text-input"
import {
  MOCK_PASSWORD_ERROR,
  MOCK_PASSWORD_PENDING,
  MOCK_RESPONSE_DELAY_MS,
} from "@/features/auth/lib/mock-auth"

type LoginError = "invalid_credentials" | "pending_approval" | null

interface LoginFormProps {
  onSuccess?: () => void
  onRegister?: () => void
  onForgotPassword?: () => void
}

const loginSchema = z.object({
  identifier: z.string().min(1, "Vui lòng nhập số điện thoại hoặc email."),
  password: z.string().min(1, "Vui lòng nhập mật khẩu."),
  rememberMe: z.boolean(),
})

type LoginFormData = z.infer<typeof loginSchema>

function LoginForm({ onSuccess, onRegister, onForgotPassword }: LoginFormProps) {
  const [error, setError] = useState<LoginError>(null)
  const [isLoading, setIsLoading] = useState(false)

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      identifier: "",
      password: "",
      rememberMe: false,
    },
  })

  function onSubmit(data: LoginFormData) {
    setError(null)
    setIsLoading(true)

    // TODO: Replace with Keycloak integration
    setTimeout(() => {
      setIsLoading(false)
      if (data.password === MOCK_PASSWORD_PENDING) {
        setError("pending_approval")
      } else if (data.password === MOCK_PASSWORD_ERROR) {
        setError("invalid_credentials")
      } else {
        onSuccess?.()
      }
    }, MOCK_RESPONSE_DELAY_MS)
  }

  const isPending = error === "pending_approval"

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex flex-1 flex-col justify-center px-14 py-[52px]">
      <div className="mb-1 text-[22px] font-medium text-text-primary">Đăng nhập</div>
      <div className="mb-7 text-sm text-text-secondary">
        Chào mừng trở lại. Đăng nhập để quản lý hợp đồng của bạn.
      </div>

      {/* Error banners */}
      {error === "invalid_credentials" && (
        <div className="mb-5 flex items-start gap-2.5 rounded-[9px] bg-danger-tint px-3.5 py-3">
          <span className="ms mt-px text-[18px] text-danger">error</span>
          <span className="text-[12.5px] leading-[1.5] text-danger-strong">
            Số điện thoại hoặc mật khẩu không đúng. Kiểm tra lại rồi thử lại.
          </span>
        </div>
      )}
      {error === "pending_approval" && (
        <div className="mb-5 flex items-start gap-2.5 rounded-[9px] bg-warning-tint px-3.5 py-3">
          <span className="ms mt-px text-[18px] text-warning">hourglass_top</span>
          <span className="text-[12.5px] leading-[1.5] text-warning-strong">
            Tài khoản đang chờ quản trị viên duyệt. Bạn đăng nhập được sau khi hồ sơ được duyệt.
          </span>
        </div>
      )}

      {/* Identifier */}
      <TextInput
        id="login-identifier"
        label="Số điện thoại hoặc email"
        placeholder="0912 345 678"
        autoComplete="username"
        containerClassName="mb-4"
        error={errors.identifier?.message || (error === "invalid_credentials" ? " " : undefined)}
        {...register("identifier")}
      />

      {/* Password */}
      <div className="mb-4 flex flex-col">
        <div className="mb-1.5 flex items-center justify-between">
          <label htmlFor="login-password" className="text-[13px] text-text-primary">
            Mật khẩu
          </label>
          <button
            type="button"
            onClick={onForgotPassword}
            className="text-[13px] text-primary hover:underline"
          >
            Quên mật khẩu?
          </button>
        </div>
        <TextInput
          id="login-password"
          type="password"
          placeholder="••••••••"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register("password")}
        />
      </div>

      {/* Remember me */}
      <div className="mb-6">
        <Controller
          name="rememberMe"
          control={control}
          render={({ field }) => (
            <Checkbox
              checked={field.value}
              onCheckedChange={field.onChange}
              label="Ghi nhớ đăng nhập trên thiết bị này"
            />
          )}
        />
      </div>

      {/* Submit */}
      <Button
        type="submit"
        disabled={isLoading || isPending}
        className="mb-4 h-[46px] w-full rounded-[9px] text-[15px]"
      >
        {isLoading ? (
          <span className="flex items-center gap-2">
            <span className="ms animate-spin text-base">progress_activity</span>
            Đang xử lý...
          </span>
        ) : (
          "Đăng nhập"
        )}
      </Button>

      {isPending && (
        <div className="mb-4 text-center text-[12.5px] text-text-muted">
          Chưa thể đăng nhập cho tới khi được duyệt
        </div>
      )}

      {/* Divider */}
      <div className="mb-4 flex items-center gap-3">
        <div className="h-px flex-1 bg-border" />
        <span className="text-xs text-text-muted">hoặc</span>
        <div className="h-px flex-1 bg-border" />
      </div>

      {/* VNeID */}
      <Button
        type="button"
        variant="outline"
        className="mb-7 h-11 w-full gap-2.5 rounded-[9px] text-sm"
      >
        <span className="ms text-[19px] text-primary">fingerprint</span>
        Đăng nhập bằng VNeID
      </Button>

      {/* Register link */}
      <div className="text-center text-[13.5px] text-text-secondary">
        Chưa có tài khoản?{" "}
        <button
          type="button"
          onClick={onRegister}
          className="text-primary hover:underline"
        >
          Đăng ký ngay
        </button>
      </div>
    </form>
  )
}

export { LoginForm }
