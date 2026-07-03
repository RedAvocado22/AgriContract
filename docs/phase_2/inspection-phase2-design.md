---
name: inspection-phase2-design
description: "Tiered Dispute Resolution — INSPECTOR Level 1.5 vs Level 2, identity model & evidentiary chain khác nhau cho từng tier. Nguồn: design session 03/07/2026."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "services.md § mục 6 (Inspection report hash)"
  related: "signature-phase2-design.md §8 (INSPECTOR để ngoài phạm vi, hẹn session riêng); milestone-escrow-phase2-design.md §3.2, §5, §8 (DisputeRoutingService, 3-tier routing); hash-chain-phase2-design.md §2.2, §3 (source_type mở rộng)"
---

## 1. Bối cảnh & Scope

3-tier INSPECTOR (Level 1 Admin nội bộ / Level 1.5 Vinacontrol-Quatest / Level 2 SGS-Bureau Veritas) đã chốt khái niệm ở `milestone-escrow-phase2-design.md` (§3.2 routing trong state machine, §5 force majeure cap ở Level 1.5, §8 config ngưỡng escalation). `signature-phase2-design.md` §8 chủ động để ngoài phạm vi lúc đó: *"INSPECTOR là third-party actor, không qua cùng luồng KYC buyer/seller ở §5 — 'same treatment' không áp trực tiếp được, cần session riêng xác định lại từ đầu."* Doc này là session đó.

**Insight gốc của session (03/07/2026):** Level 1.5 và Level 2 không phải 2 mức độ nghiêm trọng của cùng 1 vấn đề — chúng là 2 mô hình identity hoàn toàn khác nhau, không dùng chung được:

- **Level 1.5** (Vinacontrol/Quatest): tổ chức kiểm định quy mô tỉnh, quan hệ hợp đồng dịch vụ trực tiếp với platform khả thi → trở thành 1 actor thật trên platform, dùng chung mô hình identity với Buyer/Seller (`Signature`).
- **Level 2** (SGS/Bureau Veritas): tập đoàn quốc tế, quy trình bảo mật/IT riêng, không tích hợp login vào 1 platform agritech Việt Nam — và không cần, vì uy tín report của họ tự thân đã đủ (accreditation quốc tế, độc lập với platform). Platform chỉ cần bảo vệ integrity của file sau khi nhận, không cần chứng minh identity qua login flow.

**Không detail lại:** routing logic `DisputeRoutingService` (đã chốt ở milestone-escrow doc), Level 1 (Admin nội bộ dùng account Admin sẵn có, không cần thiết kế riêng).

---

## 2. Level 1.5 — Platform-integrated Inspector

### 2.1 Identity model — dùng chung `Signature`

Actor thật: account, login, JWT — coi như loại thứ 3 bên cạnh Buyer/Seller, dùng chung schema `Signature` (`signature-phase2-design.md` §3).

**Chốt (03/07/2026):** mở rộng `signerRole` enum: `BUYER`, `SELLER`, `INSPECTOR`.

`UNIQUE(contractId, signerRole)` gốc không áp trực tiếp được cho `INSPECTOR` — 1 hợp đồng có thể phát sinh nhiều report từ nhiều lần dispute khác nhau, không phải 1 lần duy nhất như buyer/seller ký.

**Chốt (03/07/2026):** không tạo `Dispute` aggregate mới chỉ để có unique key — `Dispute` hiện không tồn tại như 1 entity riêng, chỉ là state trong `Milestone` state machine (`milestone-escrow-phase2-design.md` §3.2). Thay vào đó, thêm field `reportId` (FK → `inspection_report.report_id`, §5) vào `Signature` cho nhánh `signerRole = INSPECTOR`. Đổi constraint: `UNIQUE(reportId)` — mỗi report chỉ ký đúng 1 lần, tự nhiên đúng cho cả trường hợp 1 milestone bị dispute nhiều lần (force majeure xong rồi lại dispute số lượng lần nữa), không cần khái niệm entity mới. `contractId` vẫn giữ trên `Signature` (denormalized, tiện query theo hợp đồng), nhưng không còn nằm trong unique constraint cho nhánh INSPECTOR.

### 2.2 KYC-gate — nội dung khác Buyer/Seller, cơ chế giống

Cùng gate lúc đăng ký account, fail-closed by default (`signature-phase2-design.md` §5), nhưng xác minh nội dung khác:

- Buyer/Seller: xác minh **thẩm quyền đại diện** (BLDS 2015 Điều 142) — người bấm nút có phải người có quyền đại diện pháp nhân không.
- INSPECTOR Level 1.5: xác minh **chứng chỉ/giấy phép hoạt động kiểm định thương mại** — câu hỏi khác hẳn, không phải "ai đại diện" mà "tổ chức này có đủ tư cách pháp lý phát hành report có giá trị làm bằng chứng không".

`authorizationExpiresAt` (đã có sẵn trên `User`) tái dùng được nguyên — giấy phép kiểm định cũng có ngày hết hạn, cùng cơ chế nhập tay từ giấy tờ thật, không hardcode.

### 2.3 `reportHash` — giữ nguyên công thức gốc

`services.md` mục 6: `reportHash = SHA256(content + timestamp + inspectorId)`. Với Level 1.5, `inspectorId` = `signerUserId` lấy từ JWT actor đã authenticated — công thức gốc vẫn đúng nguyên vì actor đó thật, verify được qua RBAC, không phải input tự gõ.

### 2.4 Session freshness — tách config riêng khỏi Signature

**Chốt (03/07/2026):** không tái dùng nguyên `signatureAuthMaxAgeSeconds` (300s). Lý do: 300s tính cho hành vi "đọc lại hợp đồng xong bấm ký ngay" — xác nhận tức thời. INSPECTOR thì khác: cân hàng/kiểm tra chất lượng ngoài hiện trường xong mới quay lại nộp report — khoảng cách giữa step-up và submit tự nhiên dài hơn, không phải vì session bị treo lâu mà vì bản chất công việc cần thời gian.

Thêm config riêng `inspectionAuthMaxAgeSeconds` (`application.yml`, cùng nhóm invariant kỹ thuật, không phải `ContractTerms`) — **chốt 1800s (30 phút).** Đủ cho thao tác nộp report sau khi hoàn tất kiểm tra thực địa, không phải hành vi tức thời như buyer/seller ký. Giá trị chỉnh được sau khi có dữ liệu vận hành thật, không phải hằng số cứng vĩnh viễn.

---

## 3. Level 2 — External Inspector (Admin-mediated)

### 3.1 Không Signature, không RBAC

SGS/Bureau Veritas không có account, không login, không role trên platform. Không áp KYC-gate §2.2, không áp `Signature` schema. `services.md` dòng 10 ("Role-based access granular INSPECTOR") chỉ đúng cho Level 1.5 — ghi chú lại ở đây thay vì sửa `services.md`, đúng nguyên tắc §1 (khung gốc không phải nơi giữ quyết định chi tiết).

### 3.2 Chọn tổ chức Level 2 — negotiate trong `ContractTerms`, không phải danh sách cố định

**Chốt (03/07/2026):** không hardcode danh sách tổ chức — SGS, Bureau Veritas chỉ là ví dụ minh hoạ, không phải enum đóng. Buyer/seller có thể muốn dùng tổ chức kiểm định quốc tế khác tuỳ mặt hàng/thị trường xuất khẩu.

| Field | Loại | Ghi chú |
|---|---|---|
| `level2InspectorOrg` | String, nullable | Free text — tên tổ chức Level 2 buyer/seller đồng thuận trước. Snapshot immutable lúc `sign()`, cùng nguyên tắc `milestoneSchedule`, `agreedPrice` (milestone-escrow §2.1). |

**Vì sao negotiate lúc ký, không phải lúc dispute xảy ra:** nếu để tới lúc escalate lên Level 2 mới bắt 2 bên thoả thuận tổ chức nào, họ đang trong tranh chấp — đúng lúc khó đồng thuận nhất — phải tự thương lượng thêm 1 lớp nữa trước khi tranh chấp gốc còn chưa giải quyết, tạo deadlock ngay tại bước cần giải quyết deadlock. Chốt sẵn lúc ký (khi 2 bên còn thiện chí) loại bỏ rủi ro này.

**Chốt (03/07/2026) — NOT NULL có điều kiện, không phải tuyệt đối:** bắt buộc điền `level2InspectorOrg` lúc `sign()` **chỉ khi** giá trị/loại hàng của hợp đồng nằm trong ngưỡng có thể escalate tới Level 2 (theo config ngưỡng đã có ở `milestone-escrow-phase2-design.md` §8). Hợp đồng nào bị cap cứng ở Level 1.5 theo đúng ngưỡng đó thì field này luôn `NULL`, không cần hỏi — buyer/seller không phải đàm phán thêm 1 lớp cho tình huống không bao giờ xảy ra với họ. Validate ở use case `Sign`: ngưỡng cho phép Level 2 mà `level2InspectorOrg` vẫn NULL → reject, yêu cầu negotiate trước khi ký.

### 3.3 Ingestion flow — 3-mail song song

**Chốt (03/07/2026):** SGS/Bureau Veritas gửi report qua email, đồng thời tới **3 người nhận cùng lúc**: Admin phụ trách hợp đồng đó, buyer, seller — gửi trực tiếp cả 3 từ phía tổ chức kiểm định, không phải Admin nhận rồi forward.

**Vì sao đây là chain-of-custody fix thật:** nếu chỉ Admin nhận, Admin là single point of trust — có thể (dù không cố ý) upload lên platform 1 bản khác bản gốc, không ai đối chiếu được vì không ai khác giữ bản gốc. Gửi song song cho buyer/seller nghĩa là 2 bên trực tiếp trong hợp đồng có bản gốc độc lập, ngoài platform, không qua tay Admin — nếu Admin upload sai, buyer/seller cầm bản email gốc đối chiếu được ngay. Cùng nguyên tắc "không để 1 người làm gatekeeper" đã áp ở `hash-chain-phase2-design.md` §5.2.

**Tính chất: passive backup, không phải active verify.** Không có bước nào chủ động so sánh bản buyer/seller nhận với bản Admin upload — nó chỉ nằm đó, dùng khi có tranh chấp thật cần lôi ra đối chiếu. Kế thừa đúng giới hạn đã ghi ở `hash-chain-phase2-design.md` §6: "phải có người chủ động nhìn" — 3-mail không tạo động lực phát hiện, chỉ đảm bảo bằng chứng tồn tại độc lập khi có người chịu nhìn.

### 3.4 Ingestion use case — `SubmitExternalInspectionReport`

Role `ADMIN`, không phải role `INSPECTOR`:

```
SubmitExternalInspectionReport(contractId, reportFile, receivedVia):
  1. org = Contract.contractTerms.level2InspectorOrg
     Nếu NULL → REJECT. Theo §3.2 (NOT NULL có điều kiện, validate lúc `sign()`), NULL ở đây chỉ có thể nghĩa là hợp đồng bị cap cứng Level 1.5 — report Level 2 không đáng lẽ tồn tại cho hợp đồng này, không phải case cần thêm nhánh xác nhận tổ chức

  2. Upload reportFile qua file-service, lấy fileHash (tầng riêng, đã có — services.md mục 6)

  3. reportHash = SHA256(content + timestamp + org)
     — org lấy từ ContractTerms, KHÔNG cho Admin tự gõ lại tên tổ chức,
       tránh lệch giữa cái đã negotiate và cái thực tế ghi vào report

  4. INSERT InspectionReport (contractId, tier=LEVEL_2, externalOrg=org,
     uploadedByAdminId=jwt.sub của Admin đang gọi, receivedVia, reportHash, ...)

  5. Publish vào audit-service, source_type = EXTERNAL_INSPECTION_REPORT (§4)
```

`uploadedByAdminId` lấy từ JWT của chính Admin đang thao tác — không phải field tự gõ. Để sau này cần truy trách nhiệm (ai upload report này lên platform) có audit fact rõ ràng — tách biệt hoàn toàn với `Signature`: đây không phải "Admin ký thay SGS", chỉ là ghi nhận hành động thao tác.

---

## 4. Hash Chain — `source_type` mới

**Chốt (03/07/2026):** `audit_record.source_type` (`hash-chain-phase2-design.md` §3) hiện có `MILESTONE_EVENT | CONTRACT_SIGNED | INSPECTION_REPORT`. Thêm giá trị thứ 4: `EXTERNAL_INSPECTION_REPORT`.

**Không gộp chung với `INSPECTION_REPORT`** dù cùng mục đích nghiệp vụ — vì sức nặng bằng chứng khác nhau thật sự:

| source_type | Actor đứng sau | Cơ chế xác thực |
|---|---|---|
| `INSPECTION_REPORT` (Level 1.5) | INSPECTOR đã KYC, đã login | `Signature` + RBAC |
| `EXTERNAL_INSPECTION_REPORT` (Level 2) | Không có — chỉ Admin thao tác | 3-mail song song (passive) + `uploadedByAdminId` |

Gộp chung 1 nhãn là che giấu khác biệt evidentiary — đúng thứ hội đồng bảo vệ sẽ hỏi nếu phát hiện 2 loại report khác nhau về sức nặng pháp lý lại đứng chung 1 nhãn trong audit trail.

---

## 5. Database — Additive

```sql
-- signature (mở rộng enum + thêm field cho nhánh INSPECTOR)
ALTER TABLE signature ADD COLUMN report_id UUID NULL REFERENCES inspection_report(report_id);
-- signer_role: 'BUYER' | 'SELLER' | 'INSPECTOR'
-- Nhánh INSPECTOR: report_id NOT NULL, constraint UNIQUE(report_id) — không dùng UNIQUE(contract_id, signer_role)
-- Nhánh BUYER/SELLER: report_id NULL, giữ nguyên UNIQUE(contract_id, signer_role) như signature-phase2-design.md §3

-- ContractTerms, thêm field mới (contract_db)
ALTER TABLE contract_terms ADD COLUMN level2_inspector_org VARCHAR(255) NULL;

-- inspection-service — 1 bảng, phân 2 nhánh theo tier
CREATE TABLE inspection_report (
    report_id             UUID PRIMARY KEY,
    contract_id           UUID NOT NULL,
    tier                  VARCHAR(10) NOT NULL,    -- 'LEVEL_1_5' | 'LEVEL_2'
    content               JSONB NOT NULL,
    report_hash           VARCHAR(64) NOT NULL,
    -- nhánh LEVEL_1_5 — bắt buộc:
    inspector_id          UUID NULL,               -- = signerUserId, NOT NULL nếu tier = LEVEL_1_5
    -- nhánh LEVEL_2 — bắt buộc:
    external_org          VARCHAR(255) NULL,       -- NOT NULL nếu tier = LEVEL_2
    uploaded_by_admin_id  UUID NULL,               -- NOT NULL nếu tier = LEVEL_2
    received_via          VARCHAR(50) NULL,        -- 'EMAIL' | khác, chỉ LEVEL_2
    created_at            TIMESTAMP NOT NULL DEFAULT now()
);
-- Invariant (inspector_id XOR (external_org AND uploaded_by_admin_id)) check ở use-case layer,
-- không phải DB constraint — cùng pattern active-flag đã dùng ở product-service.
```

---

## 6. Known Limitations (có chủ đích, không phải điểm mù)

- **3-mail chỉ là passive backup**, không có active reconciliation tự động — kế thừa nguyên giới hạn "phải có người chủ động nhìn" từ `hash-chain-phase2-design.md` §6.
- **WebAuthn/passkey cho INSPECTOR** — cùng lý do đã ghi ở `signature-phase2-design.md` §8: không đổi tier pháp lý (vẫn thiếu chứng thư CA cấp phép, Điều 22 khoản 3(đ)), chỉ nâng chất lượng bằng chứng khi tranh chấp, không nâng presumption pháp lý mặc định. Để ngoài phạm vi, có chủ đích.

3 điểm còn treo ở bản đầu (constraint Signature-INSPECTOR, `level2InspectorOrg` NULL, session freshness) đã chốt — xem §2.1, §2.4, §3.2.

---

## 7. Status — Inspection Design

**Chốt (03/07/2026):** Level 1.5 = actor thật, mở rộng `Signature` schema (`signerRole` thêm `INSPECTOR`, thêm `reportId` FK → `inspection_report`, constraint `UNIQUE(reportId)` thay vì theo milestone/dispute), KYC nội dung khác Buyer/Seller (chứng chỉ kiểm định thay vì thẩm quyền đại diện), `reportHash` giữ nguyên công thức gốc (`inspectorId` từ JWT authenticated), session freshness tách riêng `inspectionAuthMaxAgeSeconds` = 1800s. Level 2 = không Signature, không RBAC; `level2InspectorOrg` NOT NULL có điều kiện trong `ContractTerms` (bắt buộc nếu hợp đồng nằm trong ngưỡng có thể lên Level 2, NULL nếu cap cứng Level 1.5), negotiate lúc ký, không hardcode danh sách tổ chức; ingestion qua use case riêng `SubmitExternalInspectionReport` (role ADMIN); chain-of-custody bảo vệ bằng 3-mail song song (Admin + buyer + seller), passive backup. `audit_record.source_type` thêm `EXTERNAL_INSPECTION_REPORT`, tách khỏi `INSPECTION_REPORT` vì khác sức nặng bằng chứng.

Inspection — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.**

---

*Design session: 03/07/2026 · Chưa code · Chưa đưa vào Architecture/SDS/TechnicalSpec chính thức.*
