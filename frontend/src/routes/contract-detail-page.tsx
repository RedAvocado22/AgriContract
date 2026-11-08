import { useEffect, useRef, useState } from "react"
import { useParams } from "react-router-dom"
import { useContract } from "@/features/contracts/hooks/use-contracts"
import { ContractsLayout } from "@/features/contracts/components/contracts-layout"
import {
  ContractBadge,
  ContractFailure,
  ContractLoading,
} from "@/features/contracts/components/contract-state"
import { ContractTerms } from "@/features/contracts/components/contract-terms"
import { SigningPanel } from "@/features/contracts/components/signing-panel"
import { NegotiationForm } from "@/features/contracts/components/negotiation-form"
import { activationMessage } from "@/features/contracts/types/presentation"
import { contractBlockers } from "@/features/contracts/api/capabilities"
import { Banner } from "@/shared/ui/banner"
import { Button } from "@/shared/ui/button"
import { ContentHashDisplay } from "@/shared/ui/content-hash-display"
import { useToast } from "@/shared/ui/toast"

export function ContractDetailPage() {
  const { id = "" } = useParams()
  // Route key resets polling budget and memory-only signing state when the id changes.
  return <Detail key={id} id={id} />
}
function Detail({ id }: { id: string }) {
  const query = useContract(id)
  const contract = query.data
  const previous = useRef<{ hash: string | null; status: string } | null>(null)
  const [changed, setChanged] = useState(false)
  const toast = useToast()
  useEffect(() => {
    if (!contract) return
    if (previous.current?.hash && previous.current.hash !== contract.hash)
      setChanged(true)
    if (previous.current?.status === "SIGNED" && contract.status === "ACTIVE")
      toast.add({ title: "Hợp đồng đã có hiệu lực", type: "success" })
    previous.current = { hash: contract.hash, status: contract.status }
  }, [contract, toast])
  return (
    <ContractsLayout title="Chi tiết hợp đồng">
      {query.isPending ? (
        <ContractLoading />
      ) : query.isError ? (
        <ContractFailure
          error={query.error}
          retry={() => void query.refetch()}
        />
      ) : (
        contract && (
          <>
            <div className="flex flex-wrap items-center gap-3">
              <span className="break-all font-mono text-sm">{contract.id}</span>
              <ContractBadge status={contract.status} />
              <Button
                variant="outline"
                disabled={query.isFetching}
                onClick={() => void query.refetch()}
              >
                Tải lại trạng thái
              </Button>
            </div>
            {changed && (
              <Banner variant="warning">
                Điều khoản vừa thay đổi. Hãy đọc lại nội dung và mã vân tay mới;
                bản đề xuất và xác nhận cũ đã được bỏ.
              </Banner>
            )}
            {["OFFERED", "NEGOTIATING"].includes(contract.status) && (
              <Banner variant="warning">
                CHƯA CÓ HIỆU LỰC — thông tin điều khoản dưới đây không phải PDF
                hợp đồng do máy chủ phát hành.
              </Banner>
            )}
            <Banner variant={contract.status === "ACTIVE" ? "success" : "info"}>
              {activationMessage(contract.status)}
            </Banner>
            {contract.status === "SIGNED" && (
              <p className="text-xs text-text-secondary">
                Tự kiểm tra mỗi 5 giây khi trang mở, tối đa 2 phút; dừng khi có
                lỗi hoặc trạng thái thay đổi. Sau đó dùng “Tải lại trạng thái”.
                Không xác nhận khóa cọc từ phía trình duyệt.
              </p>
            )}
            {contract.generation === "legacy" && (
              <Banner variant="info">
                Hợp đồng theo định dạng cũ — chỉ xem thông tin, không đàm phán
                hoặc ký theo quy trình mới.
              </Banner>
            )}
            {contract.hash ? (
              <div className="rounded-md border border-border bg-surface p-4">
                <p className="mb-2 text-sm">Mã vân tay nội dung từ máy chủ</p>
                <ContentHashDisplay hash={contract.hash} />
                <p className="mt-2 text-xs text-text-secondary">
                  Phiên bản mẫu:{" "}
                  {String(
                    contract.terms.contractTemplateVersion ??
                      "Chưa được cung cấp"
                  )}
                  . Phiên bản sửa điều khoản chưa được API cung cấp.
                </p>
              </div>
            ) : (
              <p className="text-sm text-text-secondary">
                Chưa có mã vân tay nội dung.
              </p>
            )}
            <ContractTerms contract={contract} />
            {contract.generation === "coffee" &&
              ["OFFERED", "NEGOTIATING"].includes(contract.status) && (
                <>
                  <NegotiationForm
                    key={`terms-${contract.hash}`}
                    contract={contract}
                  />
                  <SigningPanel
                    key={`sign-${contract.hash}`}
                    contract={contract}
                  />
                </>
              )}
            <section className="space-y-3 rounded-md border border-border bg-surface p-4">
              <h2 className="text-lg">Tài liệu & lịch sử</h2>
              <Banner variant="info">{contractBlockers.documents}</Banner>
              <div className="flex flex-wrap gap-3">
                <Button disabled variant="outline">
                  Xem PDF nháp
                </Button>
                <Button disabled variant="outline">
                  Tải PDF đã ký
                </Button>
              </div>
              <p className="text-sm text-text-secondary">
                Chưa có API lịch sử và chi tiết chữ ký. Không suy ra tiến độ PDF
                từ trạng thái SIGNED hoặc ACTIVE.
              </p>
            </section>
          </>
        )
      )}
    </ContractsLayout>
  )
}
