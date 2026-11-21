import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Button } from "@/shared/ui/button"
import { TextInput } from "@/shared/ui/text-input"
import { Banner } from "@/shared/ui/banner"
import { MoneyDisplay } from "@/shared/ui/money-display"
import { negotiationSchema } from "../schemas/negotiation"
import { contractBlockers } from "../api/capabilities"
import { canNegotiate, decimalMoney } from "../types/presentation"
import { useOrganizationPermissions } from "@/features/organization/hooks/use-organization-permissions"
import type { Contract } from "../types/contract"

export function NegotiationForm({ contract }: { contract: Contract }) {
  const { canCreateOrSign } = useOrganizationPermissions()
  const original = decimalMoney(contract.terms.agreedPrice)
  const [review, setReview] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(negotiationSchema),
    defaultValues: { agreedPrice: original ?? "" },
  })
  return (
    <section className="space-y-4 rounded-md border border-border bg-surface p-4">
      <h2 className="text-lg">Đàm phán điều khoản</h2>
      <Banner variant="warning">{contractBlockers.negotiation}</Banner>
      <p className="text-sm text-text-secondary">
        Bản đề xuất cục bộ dưới đây chỉ để xem lại, chưa được gửi hoặc lưu.
        Phiên bản điều khoản mới sẽ do máy chủ xác nhận.
      </p>
      <form
        onSubmit={handleSubmit((data) => setReview(data.agreedPrice))}
        className="space-y-3"
      >
        <TextInput
          id="proposed-price"
          label="Đơn giá đề xuất (VND/kg)"
          inputMode="decimal"
          {...register("agreedPrice")}
          error={errors.agreedPrice?.message}
        />
        <Button type="submit" variant="outline">
          Xem lại thay đổi
        </Button>
      </form>
      {review && (
        <div className="space-y-2 text-sm" role="status">
          <p>
            Đơn giá hiện hành:{" "}
            {original ? (
              <MoneyDisplay amount={original} />
            ) : (
              "Chưa có thông tin"
            )}
          </p>
          <p>
            Đề xuất chưa gửi: <MoneyDisplay amount={review} />
          </p>
          <p>
            Các điều khoản khác giữ nguyên trong bản đề xuất; tổng giá trị do
            máy chủ tính khi hỗ trợ lưu.
          </p>
          <Button disabled={!canNegotiate(contract, canCreateOrSign)}>
            Lưu điều khoản
          </Button>
        </div>
      )}
    </section>
  )
}
