# OPEN_ISSUES.md — Report-3_SRS_v1_6 (cập nhật 12/10/2026, tối)

## Còn mở — việc phải làm tay, không phải quyết định

- **Mockup màn hình (Part III) chưa đổi theo role mới.** Khoảng 30 ảnh vẫn ghi "Admin Portal / ADMIN | Platform Administrator". Nặng nhất là ảnh 7.2.2 có tiêu đề "Breach Case Detail (Admin)"; 2.2.2 "A different Admin must approve this proposal."; 7.4.1 "awaiting Admin decision"; 7.1.1 "notify counterparty/Admin (MSG80)". Một số màn Buyer/Seller (1.2.1 My Profile, 5.x, 6.x) cũng mang header ADMIN, lỗi này có từ bản gốc. Cần export lại: header đúng role (OPERATOR / ARBITRATOR / BUYER·SELLER), Admin chỉ còn ở 8.x và 9.x.
  - Thêm cho đợt này: màn Breach Case của Buyer/Seller cần nút **Contest** và hiển thị hạn contest (GB-50). Màn Breach Case Detail của Arbitrator cần hiện trạng thái "waiting for contest window".
- **7.2.1 Breach Case Queue dùng chung ảnh với 7.2.2.** Lỗi có sẵn từ bản gốc; làm cùng đợt mockup.
- **SDS / Architecture docx + contracts/*.yaml chưa sync role.** Gồm enum `decisionSource` còn ADMIN, `PublicUserResponse.role` chưa có ARBITRATOR, `reputation.elevated_risk_cleared` còn `approvedByAdminId`/`clearedByAdminId`, và chưa có contest window. YAML chờ cấp quyền repo.

## Đã chốt (12/10/2026)

- **Maker-checker cho resolve breach → KHÔNG dùng maker-checker nội bộ; thay bằng contest window.** Quyết định có lỗi kèm chế tài (penalty, tịch thu cọc, khoá uy tín) chỉ có hiệu lực sau 2 ngày làm việc. Bên bị phạt được contest 1 lần, và case chuyển sang Level 1.5 độc lập với quyết định cuối. Nếu quyết định đã dựa trên report giám định thì từ chối contest; đường còn lại là VIAC hoặc toà ngoài platform. Quyết định no-fault, refund và release chạy ngay.
  - Lý do để defend: breach có đối thủ thật, bên bị phạt tự kiểm tra quyết định, nên người kiểm thứ hai là bên ngoài platform. Cách này mạnh hơn 2 nhân viên nội bộ, vốn có thể thông đồng. Unlock reputation hay clear AML thì không có ai thiệt trực tiếp đứng ra kháng, nên phải bù bằng người duyệt thứ hai nội bộ (GB-06).
  - SRS: UC41 sửa bước 9 và thêm alt flow "Decision Contested". Thêm GB-50. BF-05 vẽ lại: bỏ bước "Arbitrator tự review objection", thay bằng Level 1.5. Trước đó BF-05 có objection window còn UC41 thì không — mâu thuẫn này đã hết.
  - Design: milestone-escrow §6.4 và data-governance §5.3/§11.
- **GB-49 bị lạc** (paste nhầm vào ô "Retention" của bảng business constraints, trong khi appendix chỉ có GB-01 đến GB-48). Đã tách thành dòng "Replacement freeze" riêng và thêm GB-49 vào appendix.
- **AML hold đồng bộ và inspection đột xuất → ra khỏi Phase 2.** Khớp GB-47 "No transaction is held because of the flag". Phase 2 chỉ ghi nhận tín hiệu, ELEVATED_RISK ở dạng projection-only, và bank lập báo cáo giao dịch đáng ngờ. Nghĩa vụ báo cáo PCRT thuộc bank (Luật PCRT 2022 Điều 4). Ghi chú ở reputation design.
- **`OPERATOR_MANUAL`**: code đi theo SRS. Đổi enum và chạy migration `UPDATE ... SET source='OPERATOR_MANUAL' WHERE source='ADMIN_MANUAL'` (chỉ đổi nhãn, giữ attribution). Inspection `ingestion_source` cũng đổi tương tự. Đã ghi vào pricing và inspection design.
- **Bằng chứng nghiệp vụ ngân hàng** → ghi là giả định, không claim. SRS đã ghi Custodian Bank "simulated by the mock bank-service". Tài liệu khảo sát (Doc 01/02) phải ghi rõ "chưa có xác nhận từ người làm ngân hàng — giả định cần validate trước khi productize". Không bịa nguồn.

## Đã xử lý trước đó

- Change Log v1.6; footer NUMPAGES; BF-03/04/06 đặt trang ngang; câu Admin/GB-48; thứ tự lane 7 business flow.
- Sync role 9 design doc (data-governance, milestone-escrow, reputation, api-gateway, user-service, inspection, verification-matrix, signature, notification) + product, pricing.
