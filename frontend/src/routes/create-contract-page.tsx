import { ContractsLayout } from "@/features/contracts/components/contracts-layout"
import { contractBlockers } from "@/features/contracts/api/capabilities"
import { Banner } from "@/shared/ui/banner"
import { Button } from "@/shared/ui/button"
import { TextInput } from "@/shared/ui/text-input"
import { Stepper } from "@/shared/ui/stepper"
import { useOrganizationPermissions } from "@/features/organization/hooks/use-organization-permissions"
import { useAuthStore } from "@/shared/auth/auth-store"

export function CreateContractPage() {
  const { canCreateOrSign } = useOrganizationPermissions()
  const roles = useAuthStore((state) => state.roles)
  return (
    <ContractsLayout title="Chuẩn bị hợp đồng cà phê">
      <Stepper
        steps={["Nguồn hàng", "Điều khoản", "Xác nhận"]}
        currentStep={1}
      />
      <Banner variant="warning">{contractBlockers.create}</Banner>
      {(!canCreateOrSign || !roles.includes("BUYER")) && (
        <Banner variant="warning">
          Chỉ bên mua có hồ sơ đã xác minh mới được tạo hợp đồng.
        </Banner>
      )}
      <section className="space-y-4 rounded-md border border-border bg-surface p-4">
        <h2 className="text-lg">Hợp đồng B2B nội địa Việt Nam</h2>
        <p className="text-sm text-text-secondary">
          Phạm vi: cà phê. Chờ nguồn hàng thật đã duyệt, phiên bản nguồn hàng,
          số lượng khả dụng và lượng đặt tối thiểu trước khi nhập điều khoản.
        </p>
        <TextInput
          label="Nguồn hàng đã duyệt"
          id="create-listing"
          disabled
          placeholder="Chưa có nguồn hàng thật được liên kết"
        />
        <TextInput
          label="Khối lượng cam kết (kg)"
          id="create-quantity"
          disabled
          placeholder="Chờ số lượng khả dụng từ máy chủ"
        />
        <Button disabled>Tạo hợp đồng</Button>
        <p className="text-xs text-text-secondary">
          Chưa lưu bản nháp, chưa giữ hàng và chưa gửi yêu cầu tạo hợp đồng.
        </p>
      </section>
    </ContractsLayout>
  )
}
