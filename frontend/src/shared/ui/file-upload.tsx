import { cn } from "@/shared/lib/utils"

interface FileUploadItem {
  key?: string
  name: string
  /** vd "2,1 MB". Bỏ trống khi status = "failed" — hiện failureReason thay vào. */
  sizeLabel?: string
  status: "processing" | "ready" | "failed"
  failureReason?: string
  onRetry?: () => void
}

interface FileUploadProps {
  files: FileUploadItem[]
  className?: string
}

function FileUpload({ files, className }: FileUploadProps) {
  return (
    <div data-slot="file-upload" className={cn("flex flex-col gap-2", className)}>
      {files.map((file) => (
        <div
          key={file.key ?? file.name}
          className="flex items-center gap-3 rounded-sm border-[0.5px] border-border px-3.5 py-2.5"
        >
          <div
            className={cn(
              "size-9 shrink-0 rounded-md",
              file.status === "failed" ? "bg-danger-tint" : "bg-surface-muted"
            )}
          />
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm text-text-primary">{file.name}</div>
            {file.status === "failed" ? (
              <div className="truncate text-[11px] text-danger">
                Không xử lý được: {file.failureReason}
              </div>
            ) : file.sizeLabel ? (
              <div className="text-[11px] text-text-muted">{file.sizeLabel}</div>
            ) : null}
          </div>
          {file.status === "processing" ? (
            <span className="flex shrink-0 items-center gap-1.5 text-[13px] text-warning">
              <span className="size-3.5 animate-spin rounded-full border-2 border-warning-tint border-t-warning" />
              Đang xử lý
            </span>
          ) : file.status === "ready" ? (
            <span className="flex shrink-0 items-center gap-1 text-[13px] text-success">
              <span className="ms text-[15px]">check_circle</span>
              Sẵn sàng
            </span>
          ) : (
            <button
              type="button"
              onClick={file.onRetry}
              className="shrink-0 text-[13px] text-danger underline"
            >
              Thử lại
            </button>
          )}
        </div>
      ))}
    </div>
  )
}

export { FileUpload }
export type { FileUploadItem }
