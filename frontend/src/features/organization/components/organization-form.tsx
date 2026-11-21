import { useRef } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { useAuthStore } from "@/shared/auth/auth-store"
import { Button } from "@/shared/ui/button"
import { TextInput } from "@/shared/ui/text-input"
import { Textarea } from "@/shared/ui/textarea"
import { RadioGroup, RadioButton } from "@/shared/ui/radio-group"
import { Banner } from "@/shared/ui/banner"
import {
  organizationFormSchema,
  organizationErrorMessage,
  registrationRoleProblem,
  type OrganizationFormValues,
} from "../api/profile"
import { useRegisterOrganization } from "../hooks/use-organization-profile"
import { OrganizationDocuments } from "./organization-documents"

export function OrganizationForm() {
  const { roles } = useAuthStore()
  const mutation = useRegisterOrganization()
  const submitting = useRef(false)
  const {
    register,
    control,
    watch,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<OrganizationFormValues>({
    resolver: zodResolver(organizationFormSchema),
    defaultValues: {
      role:
        roles.length === 1 && (roles[0] === "BUYER" || roles[0] === "SELLER")
          ? roles[0]
          : undefined,
      organizationName: "",
      phone: "",
      address: "",
    },
  })
  const roleProblem = registrationRoleProblem(roles, watch("role"))
  const busy = isSubmitting || mutation.isPending
  const submit = handleSubmit(async (values) => {
    if (submitting.current) return
    submitting.current = true
    try {
      await mutation.mutateAsync(values)
    } catch {
      /* Render the mutation error below; do not claim success. */
    } finally {
      submitting.current = false
    }
  })
  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <section
        id="organization-profile"
        className="space-y-5 rounded-md border border-border bg-surface p-5 sm:p-6"
      >
        <h2 className="text-base">Thông tin doanh nghiệp</h2>
        <p className="text-sm text-text-secondary">
          Tên doanh nghiệp là thông tin bắt buộc. Hồ sơ được Operator xem xét
          sau khi gửi; đăng ký không đồng nghĩa đã được xác minh.
        </p>
        <fieldset disabled={busy} className="space-y-5">
          <div>
            <p id="account-type-label" className="mb-2 text-sm">
              Loại tài khoản <span className="text-danger">*</span>
            </p>
            <Controller
              name="role"
              control={control}
              render={({ field }) => (
                <RadioGroup
                  aria-labelledby="account-type-label"
                  value={field.value ?? ""}
                  onValueChange={field.onChange}
                  className="flex-row flex-wrap gap-6"
                >
                  <RadioButton value="BUYER" label="Bên mua" disabled={busy} />
                  <RadioButton value="SELLER" label="Bên bán" disabled={busy} />
                </RadioGroup>
              )}
            />
            {errors.role && (
              <p role="alert" className="mt-1 text-sm text-danger">
                {errors.role.message}
              </p>
            )}
          </div>
          {roleProblem && <Banner variant="warning">{roleProblem}</Banner>}
          <TextInput
            id="organization-name"
            label="Tên doanh nghiệp"
            required
            autoComplete="organization"
            {...register("organizationName")}
            error={errors.organizationName?.message}
            aria-invalid={Boolean(errors.organizationName)}
            aria-describedby={
              errors.organizationName ? "organization-name-error" : undefined
            }
          />
          {errors.organizationName && (
            <span id="organization-name-error" className="sr-only" role="alert">
              {errors.organizationName.message}
            </span>
          )}
          <TextInput
            id="organization-phone"
            label="Số điện thoại"
            type="tel"
            autoComplete="tel"
            {...register("phone")}
            error={errors.phone?.message}
          />
          <Textarea
            id="organization-address"
            label="Địa chỉ"
            autoComplete="street-address"
            {...register("address")}
            error={errors.address?.message}
          />
        </fieldset>
      </section>
      <OrganizationDocuments disabled={busy} />
      {mutation.isError && (
        <Banner role="alert" variant="danger">
          {organizationErrorMessage(mutation.error)}
        </Banner>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" size="lg" disabled={busy || Boolean(roleProblem)}>
          {busy ? "Đang gửi hồ sơ…" : "Gửi hồ sơ doanh nghiệp"}
        </Button>
        <p className="text-xs text-text-secondary">
          Chỉ thông tin hồ sơ được gửi. Tệp đã chọn chưa được lưu lên hệ thống.
        </p>
      </div>
    </form>
  )
}
