import { useRef, useState } from "react"
import { useForm, FormProvider, useFormContext, Controller } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"

import { Button } from "@/shared/ui/button"
import { Checkbox } from "@/shared/ui/checkbox"
import { FileUpload, type FileUploadItem } from "@/shared/ui/file-upload"
import { Stepper } from "@/shared/ui/stepper"
import { TextInput } from "@/shared/ui/text-input"
import { getPasswordStrength, strengthColors, strengthLabels } from "@/features/auth/lib/password"
import { MOCK_OTP_RESPONSE_DELAY_MS } from "@/features/auth/lib/mock-auth"

// ─── ZOD SCHEMAS ─────────────────────────────────────────────────────────────

const step2Schema = z.object({
  fullName: z.string().min(1, "Vui lòng nhập họ và tên."),
  phone: z
    .string()
    .min(1, "Vui lòng nhập số điện thoại.")
    .refine(
      (val) => /^(0|\+84)[0-9]{8,10}$/.test(val.replace(/\s/g, "")),
      "Số điện thoại không hợp lệ."
    ),
  email: z.string().min(1, "Vui lòng nhập email.").email("Email không hợp lệ."),
  password: z.string().min(8, "Mật khẩu tối thiểu 8 ký tự."),
  agreed: z.boolean().refine((val) => val === true, "Bạn cần đồng ý với điều khoản để tiếp tục."),
})

const fileUploadItemSchema = z.object({
  key: z.string().optional(),
  name: z.string().min(1, "Tên file không hợp lệ."),
  sizeLabel: z.string().optional(),
  status: z.enum(["processing", "ready", "failed"]),
  failureReason: z.string().optional(),
})

const registerSchema = z.object({
  role: z.enum(["SELLER", "BUYER"]),
  fullName: step2Schema.shape.fullName,
  phone: step2Schema.shape.phone,
  email: step2Schema.shape.email,
  password: step2Schema.shape.password,
  agreed: step2Schema.shape.agreed,
  businessLicense: fileUploadItemSchema.nullable().refine(
    (val) => val && val.status === "ready",
    "Vui lòng upload Giấy đăng ký kinh doanh."
  ),
  authorizationDoc: fileUploadItemSchema.nullable().refine(
    (val) => val && val.status === "ready",
    "Vui lòng upload Xác nhận đại diện/Giấy uỷ quyền."
  ),
})

type RegisterFormData = z.infer<typeof registerSchema>

// ─── Step 1: Role selection ───────────────────────────────────────────────────

interface RoleCardProps {
  selected: boolean
  onClick: () => void
  icon: string
  title: string
  description: string
}

function RoleCard({ selected, onClick, icon, title, description }: RoleCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "flex flex-col rounded-[11px] p-[14px] text-left transition-all cursor-pointer",
        selected
          ? "border border-primary bg-[#F6FDF9]"
          : "border-[0.5px] border-border bg-surface hover:border-border-strong",
      ].join(" ")}
    >
      <div className="flex w-full items-center justify-between">
        <span className={["ms text-[22px]", selected ? "text-primary" : "text-text-muted"].join(" ")}>
          {icon}
        </span>
        <span className={["ms text-[20px]", selected ? "text-primary" : "text-text-muted"].join(" ")}>
          {selected ? "radio_button_checked" : "radio_button_unchecked"}
        </span>
      </div>
      <div className="mt-2 text-[14.5px] font-medium text-text-primary">{title}</div>
      <div className="mt-0.5 text-[12.5px] text-text-secondary">{description}</div>
    </button>
  )
}

function Step1Role({ onNext }: { onNext: () => void }) {
  const { control } = useFormContext<RegisterFormData>()

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    onNext()
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col">
      <div className="mb-1 text-[22px] font-medium text-text-primary">Tạo tài khoản</div>
      <div className="mb-6 text-sm text-text-secondary">Bạn tham gia sàn với vai trò nào?</div>

      <Controller
        name="role"
        control={control}
        render={({ field }) => (
          <div className="mb-8 grid grid-cols-2 gap-3">
            <RoleCard
              selected={field.value === "SELLER"}
              onClick={() => field.onChange("SELLER")}
              icon="agriculture"
              title="Bên bán"
              description="Nông dân, HTX, đại lý thu mua"
            />
            <RoleCard
              selected={field.value === "BUYER"}
              onClick={() => field.onChange("BUYER")}
              icon="storefront"
              title="Bên mua"
              description="Doanh nghiệp chế biến, xuất khẩu"
            />
          </div>
        )}
      />

      <Button type="submit" className="h-[46px] w-full rounded-[9px] text-[15px]">
        Tiếp theo
        <span className="ms ml-1 text-base">arrow_forward</span>
      </Button>
    </form>
  )
}

// ─── Step 2: Info ─────────────────────────────────────────────────────────────

function Step2Info({ onNext, onBack }: { onNext: () => void; onBack: () => void }) {
  const {
    register,
    control,
    watch,
    trigger,
    formState: { errors },
  } = useFormContext<RegisterFormData>()

  const passwordVal = watch("password") || ""
  const strength = getPasswordStrength(passwordVal)

  async function handleStepSubmit(e: React.FormEvent) {
    e.preventDefault()
    const isValid = await trigger(["fullName", "phone", "email", "password", "agreed"])
    if (isValid) {
      onNext()
    }
  }

  return (
    <form onSubmit={handleStepSubmit} className="flex flex-col">
      <div className="mb-1 text-[22px] font-medium text-text-primary">Thông tin tài khoản</div>
      <div className="mb-5 text-sm text-text-secondary">Điền đầy đủ để hoàn tất hồ sơ đăng ký.</div>

      <div className="mb-4 grid grid-cols-2 gap-4">
        <TextInput
          id="reg-fullname"
          label="Họ và tên"
          required
          placeholder="Nguyễn Văn A"
          error={errors.fullName?.message}
          {...register("fullName")}
        />
        <TextInput
          id="reg-phone"
          label="Số điện thoại"
          required
          placeholder="0912 345 678"
          error={errors.phone?.message}
          {...register("phone")}
        />
      </div>

      <div className="mb-4">
        <TextInput
          id="reg-email"
          label="Email"
          required
          type="email"
          placeholder="nguyen@email.com"
          error={errors.email?.message}
          {...register("email")}
        />
      </div>

      {/* Password */}
      <div className="mb-4 flex flex-col">
        <TextInput
          id="reg-password"
          label="Mật khẩu"
          required
          type="password"
          placeholder="••••••••"
          autoComplete="new-password"
          error={errors.password?.message}
          {...register("password")}
        />
        {/* Strength bar */}
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

      {/* Terms */}
      <div className="mb-1">
        <Controller
          name="agreed"
          control={control}
          render={({ field }) => (
            <Checkbox
              checked={field.value}
              onCheckedChange={field.onChange}
              label={
                <span className="text-[13px] leading-[1.5] text-text-secondary">
                  Tôi đồng ý với{" "}
                  <a href="#" className="text-primary hover:underline">
                    Điều khoản sử dụng
                  </a>{" "}
                  và{" "}
                  <a href="#" className="text-primary hover:underline">
                    Chính sách bảo mật
                  </a>
                  , bao gồm quy trình ký quỹ và giải quyết tranh chấp.
                </span>
              }
            />
          )}
        />
      </div>
      {errors.agreed && <div className="mb-3 text-[13px] text-danger">{errors.agreed.message}</div>}
      {!errors.agreed && <div className="mb-4" />}

      <div className="flex gap-3">
        <Button
          type="button"
          variant="outline"
          className="h-[46px] flex-1 rounded-[9px] text-[15px]"
          onClick={onBack}
        >
          Quay lại
        </Button>
        <Button type="submit" className="h-[46px] flex-1 rounded-[9px] text-[15px]">
          Tiếp theo
          <span className="ms ml-1 text-base">arrow_forward</span>
        </Button>
      </div>
    </form>
  )
}

// ─── Step 3: Upload documents ─────────────────────────────────────────────────

function FilePickerSlot({
  label,
  description,
  file,
  onFilePicked,
  onRetry,
  error,
}: {
  label: string
  description: string
  file: FileUploadItem | null
  onFilePicked: (file: File) => void
  onRetry: () => void
  error?: string
}) {
  const inputRef = useRef<HTMLInputElement>(null)

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const picked = e.target.files?.[0]
    if (picked) onFilePicked(picked)
    e.target.value = ""
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="text-[13px] font-medium text-text-primary">
        {label} <span className="text-danger">*</span>
      </div>
      <div className="text-[12px] text-text-muted">{description}</div>

      {file ? (
        <FileUpload files={[{ ...file, onRetry }]} />
      ) : (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className={[
            "flex items-center gap-2.5 rounded-sm border-[0.5px] border-dashed px-4 py-3 text-sm text-text-secondary transition-colors hover:border-primary hover:text-primary cursor-pointer",
            error ? "border-danger text-danger" : "border-border-strong",
          ].join(" ")}
        >
          <span className="ms text-[19px]">upload_file</span>
          Chọn file (PDF, JPG, PNG — tối đa 10 MB)
        </button>
      )}

      {error && <div className="text-[13px] text-danger">{error}</div>}

      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.jpg,.jpeg,.png"
        className="hidden"
        onChange={handleChange}
      />
    </div>
  )
}

function Step3Upload({
  onSubmit,
  onBack,
  isLoading,
}: {
  onSubmit: () => void
  onBack: () => void
  isLoading: boolean
}) {
  const {
    control,
    trigger,
    formState: { errors },
  } = useFormContext<RegisterFormData>()

  function handleFilePicked(
    field: "businessLicense" | "authorizationDoc",
    file: File,
    onChange: (value: FileUploadItem | null) => void
  ) {
    onChange({
      name: file.name,
      sizeLabel: `${(file.size / 1024 / 1024).toFixed(1)} MB`,
      status: "ready" as const,
    })
    trigger(field)
  }

  async function handleStepSubmit(e: React.FormEvent) {
    e.preventDefault()
    const isValid = await trigger(["businessLicense", "authorizationDoc"])
    if (isValid) {
      onSubmit()
    }
  }

  return (
    <form onSubmit={handleStepSubmit} className="flex flex-col">
      <div className="mb-1 text-[22px] font-medium text-text-primary">Giấy tờ xác minh</div>
      <div className="mb-6 text-sm text-text-secondary">
        Hồ sơ được quản trị viên xét duyệt thủ công — thường trong 1–2 ngày làm việc.
      </div>

      <div className="mb-6 flex flex-col gap-5">
        <Controller
          name="businessLicense"
          control={control}
          render={({ field }) => (
            <FilePickerSlot
              label="Giấy đăng ký kinh doanh"
              description="Bản scan hoặc ảnh rõ nét, còn hiệu lực."
              file={field.value}
              onFilePicked={(file) => handleFilePicked("businessLicense", file, field.onChange)}
              onRetry={() => field.onChange(null)}
              error={errors.businessLicense?.message}
            />
          )}
        />

        <Controller
          name="authorizationDoc"
          control={control}
          render={({ field }) => (
            <FilePickerSlot
              label="Xác nhận người đại diện / Giấy uỷ quyền"
              description="Xác nhận người ký hợp đồng có thẩm quyền đại diện pháp nhân."
              file={field.value}
              onFilePicked={(file) => handleFilePicked("authorizationDoc", file, field.onChange)}
              onRetry={() => field.onChange(null)}
              error={errors.authorizationDoc?.message}
            />
          )}
        />
      </div>

      {/* Info hint */}
      <div className="mb-6 flex items-start gap-2.5 rounded-[9px] border-[0.5px] border-[#BBF7D0] bg-[#F6FDF9] px-3.5 py-3">
        <span className="ms mt-px text-[19px] text-primary">schedule</span>
        <span className="text-[12.5px] leading-[1.5] text-text-secondary">
          Sau khi gửi, hồ sơ chờ quản trị viên duyệt. Tài khoản chưa duyệt chưa thể giao dịch —
          bạn sẽ nhận thông báo khi được kích hoạt.
        </span>
      </div>

      <div className="flex gap-3">
        <Button
          type="button"
          variant="outline"
          className="h-[46px] flex-1 rounded-[9px] text-[15px]"
          onClick={onBack}
          disabled={isLoading}
        >
          Quay lại
        </Button>
        <Button
          type="submit"
          disabled={isLoading}
          className="h-[54px] flex-1 rounded-[10px] text-[16px] font-semibold"
        >
          {isLoading ? (
            <span className="flex items-center gap-2">
              <span className="ms animate-spin text-base">progress_activity</span>
              Đang gửi...
            </span>
          ) : (
            "Gửi hồ sơ đăng ký"
          )}
        </Button>
      </div>
    </form>
  )
}

// ─── Main RegisterForm ────────────────────────────────────────────────────────

interface RegisterFormProps {
  onSuccess: () => void
  onLogin: () => void
}

function RegisterForm({ onSuccess, onLogin }: RegisterFormProps) {
  const [step, setStep] = useState(1)
  const [isLoading, setIsLoading] = useState(false)

  const methods = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      role: "SELLER",
      fullName: "",
      phone: "",
      email: "",
      password: "",
      agreed: false,
      businessLicense: null,
      authorizationDoc: null,
    },
    mode: "onTouched",
  })

  function handleSubmit(_data: RegisterFormData) {
    setIsLoading(true)
    // TODO: Replace with Keycloak API call
    setTimeout(() => {
      setIsLoading(false)
      onSuccess()
    }, MOCK_OTP_RESPONSE_DELAY_MS)
  }

  return (
    <div className="flex flex-1 flex-col px-14 py-10">
      <Stepper steps={["Vai trò", "Thông tin", "Giấy tờ"]} currentStep={step} className="mb-8" />

      <FormProvider {...methods}>
        {step === 1 && <Step1Role onNext={() => setStep(2)} />}
        {step === 2 && <Step2Info onNext={() => setStep(3)} onBack={() => setStep(1)} />}
        {step === 3 && (
          <Step3Upload
            onSubmit={methods.handleSubmit(handleSubmit)}
            onBack={() => setStep(2)}
            isLoading={isLoading}
          />
        )}
      </FormProvider>

      <div className="mt-6 text-center text-[13.5px] text-text-secondary">
        Đã có tài khoản?{" "}
        <button type="button" onClick={onLogin} className="text-primary hover:underline cursor-pointer">
          Đăng nhập
        </button>
      </div>
    </div>
  )
}

export { RegisterForm }
export type { RegisterFormData }
