import type { Consent } from "../types/contract"

// TODO(BE): expose active action-specific consent text/version bound to the contract hash.
// No guessed endpoint and no copy of server-owned statement configuration.
export async function getSigningConsent(): Promise<Consent | null> {
  return null
}

export const contractBlockers = {
  negotiation:
    "Chưa thể lưu: máy chủ chưa cung cấp trạng thái chữ ký, quyền sửa và điều kiện kiểm tra phiên bản cũ. Điều khoản có thể đã bị khóa sau chữ ký đầu tiên.",
  signing:
    "Chưa thể ký: chưa có nguồn cung cấp nội dung và phiên bản cam kết đang có hiệu lực từ máy chủ.",
  documents:
    "Tài liệu chưa sẵn sàng: dịch vụ chưa cung cấp bản xem trước và danh sách PDF đã ký. Chưa thể xác định trạng thái tạo PDF.",
  create:
    "Chưa thể tạo hợp đồng cà phê: API tạo mới chưa hỗ trợ phiên bản nguồn hàng và giữ số lượng theo quy trình hiện hành. Nguồn hàng trên giao diện hiện là dữ liệu mẫu.",
}

// TODO(BE): #232 Phase 2 create/reservation. Never call the legacy POST as a substitute.
export async function createCoffeeContract(): Promise<never> {
  throw new Error(contractBlockers.create)
}
// TODO(BE): server-rendered document preview/list/authorized READY download.
export async function getContractDocuments(): Promise<never> {
  throw new Error(contractBlockers.documents)
}
// TODO(BE): #234 activation cancellation; no simulated refund or cancellation.
