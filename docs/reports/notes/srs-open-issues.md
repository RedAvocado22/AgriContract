# OPEN_ISSUES.md — Report-3_SRS (cập nhật 04/11/2026)

## Còn mở — việc phải làm tay, không phải quyết định

- **Mockup màn hình (Part III) — checklist sửa từng ảnh (soát 04/11/2026, SRS 1.7).** Ảnh không có nguồn trong repo; sửa trong tool gốc rồi thay ảnh vào docx (giữ nguyên rel, chỉ thay file `word/media/imageN.png`). Quy tắc header/menu: Guest = `GUEST`; Buyer/Seller = `Party Portal`, góc phải `BUYER` hoặc `SELLER`, menu party (Dashboard, My Contracts, Milestones, Breach Cases, Evidence, Notifications); Operator = `Operator Console`; Arbitrator = `Arbitrator Console`; Inspector = `Inspector Portal`, menu không có Users & KYC; Admin chỉ còn ở 8.x, 9.x.
  - **Sai ảnh, phải có ảnh mới:**
    - 5.2.3 Edit Contract Terms (`image59`) đang nhúng nhầm use-case diagram của Arbitrator.
    - 2.1.3 Verify KYC Dialog không có ảnh.
    - 7.2.1 Breach Case Queue dùng chung `image57` với 7.2.2. Cần màn queue riêng: bảng case REPORTED/UNDER_REVIEW, filter status/reason/severity.
  - **Header `Admin Portal / ADMIN | Platform Administrator` → BUYER/SELLER (Party Portal):** 1.2.1 My Profile (`image51`), 5.1.1 Create Offer (`image16`), 5.2.1 My Contracts (`image7`), 5.2.2 Contract Detail (`image3`, menu đang là menu Admin), 5.4.1 Propose Replacement (`image63`), 5.5.1 Export Escrow Statement (`image66`), 6.1.1 Milestone Detail (`image65`), 6.2.1 Record Weighing (`image72`, SELLER), 6.2.2 Record Receipt (`image75`, BUYER), 6.3.1 Respond to Flag (`image70`, SELLER), 6.3.2 Claim Force Majeure (`image74`, SELLER), 6.4.1 Milestone Price Adjustment (`image55`). 6.3.1/6.3.2/6.4.1 còn chữ "OPERATIONS" đè lên "NAVIGATION".
  - **Header → BUYER/SELLER, ghi role cụ thể thay `PARTY | Signed in`:** 7.1.1 Report Breach (`image41`; bỏ "notify counterparty/Admin (MSG80)" → "counterparty and Arbitrator"), 7.1.2 Breach Case Detail Party View (`image43`; decision source bỏ `ADMIN` → `ARBITRATOR / INSPECTOR_L1_5 / INSPECTOR_L2 / MUTUAL`; sửa chữ đè ở ô Inspection Status; thêm nút **Contest** + Contest Status/Deadline (GB-50); menu đang lặp "Breach Cases" 2 lần). 7.3.1 Terminate Contract (`image46`): header `BUYER / SELLER / ADMIN` → `BUYER / SELLER`.
  - **Header → ARBITRATOR (Arbitrator Console):** 2.2.1 Reputation Action Requests (`image58`, "Signed in as Admin (admin.minh)" → Arbitrator; nút Approve/Reject chỉ hiện cho Arbitrator), 7.2.2 Breach Case Detail (`image57`; tiêu đề "(Admin)" → "(Arbitrator)", "Admin cannot edit them" → "Arbitrator", thêm trạng thái "waiting for contest window"), 7.2.3 Request Inspection (`image48`), 7.2.4 Resolve Breach Case (`image47`; dropdown Decision Source đang chọn `ADMIN` → `ARBITRATOR`), 7.4.1 FM Review Queue (`image53`, "awaiting Admin decision" → "Arbitrator"), 7.4.2 FM Claim Detail (`image54`), 7.7.1 Level 2 Commissions (`image20`).
  - **Header → OPERATOR (Operator Console):** 2.2.2 Propose Reputation Action (`image30`, "A different Admin must approve this proposal." → "An Arbitrator must approve this proposal." — MSG17, GB-06), 7.5.1 Activation Failures (`image40`), 7.5.2 Milestone Funding Failures (`image44`), 7.7.2 Pending External Report Review (`image23`), 7.7.3 Manual External Report Upload (`image27`, "under your Admin identity" → "Operator"), 7.7.4 Email Intake Failures (`image29`). 3.1.x My Plots (`image24/26/38/35`, SELLER) đang ghi breadcrumb "KYC Detail" và menu có "Users" → sửa breadcrumb "My Plots", bỏ "Users".
  - **Header → INSPECTOR:** 7.6.1 Assigned Cases (`image1`; bỏ dòng RICE/RUBBER/CASHEW, chỉ COFFEE; bỏ Users & KYC/Contracts/Reputation khỏi menu), 7.6.2 Submit Inspection Result (`image21`; header đang ADMIN; bỏ dòng "RICE (5 rates) · RUBBER · CASHEW").
  - **COFFEE-only:** 4.1.2 Manual Price Entry (`image19`, "RUBBER or CASHEW" → COFFEE), 8.1.2 Contract Evidence Export (`image25`, "coffee or rubber contracts" → "coffee contracts").
  - Giữ nguyên ADMIN: 8.1.1, 8.1.2, 8.2.1, 9.1.1–9.1.3. Đã đúng: 1.1.x, 1.2.2, 2.1.1, 2.1.2, 3.4.x, 3.5.x, 3.6.1, 4.1.1.

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

- Đã làm ở SRS 1.7 / SDS (PR #326, 02/11/2026): ngoại lệ contest cho breach cấp contract (UC41, GB-50, BF-05); SDS sync role, `decisionSource` không còn ADMIN, có contest window.
- Resolve/review breach chỉ ARBITRATOR (chốt 04/11/2026): OpenAPI `x-roles`, milestone-escrow §6.4, SDS. Kết quả Inspector vào case qua `inspection.report_confirmed`. Ma trận SRS đã đúng từ trước.
- Change Log v1.6; footer NUMPAGES; BF-03/04/06 đặt trang ngang; câu Admin/GB-48; thứ tự lane 7 business flow.
- Sync role 9 design doc (data-governance, milestone-escrow, reputation, api-gateway, user-service, inspection, verification-matrix, signature, notification) + product, pricing.
