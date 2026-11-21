export interface LocalOrganizationDocument {
  file: File
  storage: "local-only"
}

// TODO(BE): organization document upload
// No endpoint, MIME policy or size policy is frozen. No network request or fake success.
export function selectOrganizationDocuments(
  files: File[]
): LocalOrganizationDocument[] {
  return files.map((file) => ({ file, storage: "local-only" }))
}

export async function uploadOrganizationDocument(
  _document: LocalOrganizationDocument
): Promise<never> {
  throw new Error(
    "Chức năng lưu giấy tờ chưa sẵn sàng. Tệp hiện chỉ được chọn trên thiết bị."
  )
}
