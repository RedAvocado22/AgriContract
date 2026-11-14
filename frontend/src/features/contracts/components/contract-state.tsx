import { EmptyState } from "@/shared/ui/empty-state"
import { Skeleton } from "@/shared/ui/skeleton"
import { StatusBadge } from "@/shared/ui/status-badge"
import { contractError, statusLabels } from "../types/presentation"

export function ContractLoading() {
  return (
    <div role="status" aria-label="Đang tải hợp đồng" className="space-y-3">
      <Skeleton className="h-24" />
      <Skeleton className="h-48" />
    </div>
  )
}
export function ContractFailure({
  error,
  retry,
}: {
  error: unknown
  retry: () => void
}) {
  return (
    <EmptyState
      variant="error"
      title="Chưa thể tải hợp đồng"
      description={contractError(error)}
      actionLabel="Thử lại"
      onAction={retry}
    />
  )
}
export function ContractBadge({ status }: { status: string }) {
  return (
    <StatusBadge
      className="whitespace-normal leading-5"
      variant={
        status === "ACTIVE"
          ? "success"
          : status === "SIGNED"
            ? "warning"
            : status === "ACTIVATION_FAILED"
              ? "danger"
              : "neutral"
      }
    >
      {statusLabels[status] ?? "Trạng thái chưa nhận biết"}
    </StatusBadge>
  )
}
