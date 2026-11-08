import { useRef, useState } from "react"
import { FileUpload } from "@/shared/ui/file-upload"
import { Button } from "@/shared/ui/button"
import {
  selectOrganizationDocuments,
  type LocalOrganizationDocument,
} from "../api/documents"

export function OrganizationDocuments({
  disabled = false,
}: {
  disabled?: boolean
}) {
  const [documents, setDocuments] = useState<LocalOrganizationDocument[]>([])
  const input = useRef<HTMLInputElement>(null)
  return (
    <section
      className="space-y-4 rounded-md border border-border bg-surface p-5 sm:p-6"
      aria-labelledby="organization-documents-title"
    >
      <h2 id="organization-documents-title" className="text-base">
        Tài liệu doanh nghiệp
      </h2>
      <p id="document-selection-help" className="text-sm text-text-secondary">
        Chức năng lưu giấy tờ chưa sẵn sàng. Tệp chọn ở đây chỉ nằm trên thiết
        bị, không được gửi cùng hồ sơ và sẽ mất khi rời trang. Định dạng và giới
        hạn tệp đang chờ quy định từ hệ thống.
      </p>
      <div className="rounded-sm border border-dashed border-border-strong p-4">
        <input
          ref={input}
          type="file"
          multiple
          hidden
          disabled={disabled}
          aria-label="Chọn tài liệu trên thiết bị"
          onChange={(event) =>
            setDocuments(
              selectOrganizationDocuments(Array.from(event.target.files ?? []))
            )
          }
        />
        <Button
          type="button"
          variant="outline"
          disabled={disabled}
          aria-describedby="document-selection-help"
          onClick={() => input.current?.click()}
        >
          Chọn tài liệu trên thiết bị
        </Button>
      </div>
      <FileUpload
        files={documents.map(({ file }, index) => ({
          key: `${index}-${file.name}`,
          name: file.name,
          sizeLabel: `${file.size.toLocaleString("vi-VN")} byte`,
          status: "local",
        }))}
      />
      {documents.length > 0 && (
        <Button
          type="button"
          variant="outline"
          disabled={disabled}
          onClick={() => {
            setDocuments([])
            if (input.current) input.current.value = ""
          }}
        >
          Bỏ lựa chọn tệp
        </Button>
      )}
    </section>
  )
}
