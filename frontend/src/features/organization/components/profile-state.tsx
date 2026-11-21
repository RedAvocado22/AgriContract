import { Skeleton } from "@/shared/ui/skeleton"
import { EmptyState } from "@/shared/ui/empty-state"
import { organizationErrorMessage } from "../api/profile"

export function ProfileLoading() {
  return (
    <div
      role="status"
      aria-label="Đang tải hồ sơ doanh nghiệp"
      className="space-y-4 rounded-md border border-border bg-surface p-6"
    >
      <p className="text-sm text-text-secondary">
        Đang tải hồ sơ doanh nghiệp…
      </p>
      <Skeleton className="h-6 w-2/3" />
      <div className="grid gap-4 sm:grid-cols-2">
        {[0, 1, 2, 3].map((key) => (
          <Skeleton key={key} className="h-16" />
        ))}
      </div>
    </div>
  )
}

export function ProfileError({
  error,
  retry,
}: {
  error: unknown
  retry: () => void
}) {
  return (
    <EmptyState
      role="alert"
      variant="error"
      title="Chưa tải được hồ sơ"
      description={organizationErrorMessage(error)}
      actionLabel="Thử lại"
      onAction={retry}
    />
  )
}
