---
name: inspection-phase2-design
description: "Tiered Dispute Resolution — INSPECTOR Level 1.5 vs Level 2, identity model & evidentiary chain khác nhau cho từng tier. Nguồn: design session 03/07/2026, merge addendum 04/07/2026, fix cross-service FK 04/07/2026."
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

**Chốt (03/07/2026):** không tạo `Dispute` aggregate mới chỉ để có unique key — `Dispute` hiện không tồn tại như 1 entity riêng, chỉ là state trong `Milestone` state machine (`milestone-escrow-phase2-design.md` §3.2). Thay vào đó, thêm field `reportId` (FK logic → `inspection_report.report_id`, §5 — **không phải FK database**, xem lý do ở §5) vào `Signature` cho nhánh `signerRole = INSPECTOR`. Đổi constraint: `UNIQUE(reportId)` — mỗi report chỉ ký đúng 1 lần, tự nhiên đúng cho cả trường hợp 1 milestone bị dispute nhiều lần (force majeure xong rồi lại dispute số lượng lần nữa), không cần khái niệm entity mới. `contractId` vẫn giữ trên `Signature` (denormalized, tiện query theo hợp đồng), nhưng không còn nằm trong unique constraint cho nhánh INSPECTOR.

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

## 3. Level 2 — External Inspector (Admin-mediated → Auto-intake + Human-confirm)

### 3.1 Không Signature, không RBAC

SGS/Bureau Veritas không có account, không login, không role trên platform. Không áp KYC-gate §2.2, không áp `Signature` schema. `services.md` dòng 10 ("Role-based access granular INSPECTOR") chỉ đúng cho Level 1.5 — ghi chú lại ở đây thay vì sửa `services.md`, đúng nguyên tắc §1 (khung gốc không phải nơi giữ quyết định chi tiết).

### 3.2 Chọn tổ chức Level 2 — negotiate trong `ContractTerms`, không phải danh sách cố định

**Chốt (03/07/2026):** không hardcode danh sách tổ chức — SGS, Bureau Veritas chỉ là ví dụ minh hoạ, không phải enum đóng. Buyer/seller có thể muốn dùng tổ chức kiểm định quốc tế khác tuỳ mặt hàng/thị trường xuất khẩu.

| Field | Loại | Ghi chú |
|---|---|---|
| `level2InspectorOrg` | String, nullable | Free text — tên tổ chức Level 2 buyer/seller đồng thuận trước. Snapshot immutable lúc `sign()`, cùng nguyên tắc `milestoneSchedule`, `agreedPrice` (milestone-escrow §2.1). |

**Vì sao negotiate lúc ký, không phải lúc dispute xảy ra:** nếu để tới lúc escalate lên Level 2 mới bắt 2 bên thoả thuận tổ chức nào, họ đang trong tranh chấp — đúng lúc khó đồng thuận nhất — phải tự thương lượng thêm 1 lớp nữa trước khi tranh chấp gốc còn chưa giải quyết, tạo deadlock ngay tại bước cần giải quyết deadlock. Chốt sẵn lúc ký (khi 2 bên còn thiện chí) loại bỏ rủi ro này.

**Chốt (03/07/2026) — NOT NULL có điều kiện, không phải tuyệt đối:** bắt buộc điền `level2InspectorOrg` lúc `sign()` **chỉ khi** giá trị/loại hàng của hợp đồng nằm trong ngưỡng có thể escalate tới Level 2 (theo config ngưỡng đã có ở `milestone-escrow-phase2-design.md` §8). Hợp đồng nào bị cap cứng ở Level 1.5 theo đúng ngưỡng đó thì field này luôn `NULL`, không cần hỏi — buyer/seller không phải đàm phán thêm 1 lớp cho tình huống không bao giờ xảy ra với họ. Validate ở use case `Sign`: ngưỡng cho phép Level 2 mà `level2InspectorOrg` vẫn NULL → reject, yêu cầu negotiate trước khi ký.

**KI-2 — ĐÃ ĐÓNG (04/07/2026).** Vấn đề gốc: negotiate `level2InspectorOrg` hoàn toàn tự do, không allowlist — 2 bên thông đồng có thể tự chọn tổ chức dễ dãi/tự dựng.

**Chốt — allowlist 3 nhóm, không phải danh sách phẳng:**

| Nhóm | Cơ chế xác nhận | Lưu trữ |
|---|---|---|
| **Major quốc tế đã biết** (SGS, Bureau Veritas...) | Hardcode sẵn, không cần verify lại mỗi lần | 1 bảng DB Admin quản lý (`level2_inspector_allowlist`), thêm/bớt được không cần deploy code |
| **Trong nước** | Verify qua BoA-VIAS (`boa.gov.vn` — Văn phòng Công nhận Chất lượng Việt Nam) | Cùng bảng trên, sau khi verify lần đầu |
| **Lạ, ngoài 2 nhóm trên** | Admin duyệt case-by-case | **Private — không ghi vào danh sách dùng lại chung** |

**Cơ chế Admin verify nhóm "lạ":** tổ chức phải cung cấp **accreditation certificate number**, Admin tra đúng cơ quan công nhận quốc gia đã phát hành số đó — trong nước tra thẳng BoA-VIAS, quốc tế tra theo chuỗi ILAC → national accreditation body của nước đó → registry của chính AB đó. Không có số hoặc tra không khớp → reject, không tạo `PlotRegistryEntry`/không cho chọn tổ chức đó.

**Vì sao approval nhóm "lạ" cố tình để private, không thêm vào danh sách dùng chung:** né hẳn bài toán "công ty phá sản/mất accreditation thì phải dọn danh sách" — vì không có state sống lâu nào cần dọn. Mỗi lần "lạ" là 1 lần verify mới, không tích luỹ nợ kỹ thuật kiểu danh sách cần bảo trì định kỳ. Chi phí verify (tra 1 mã số trên web BoA/ILAC, vài phút) không phụ thuộc scale — rẻ như nhau dù dùng 5 hay 500 lần, vì đây là việc người-làm-tốc-độ-người trên sự kiện tần suất thấp, không phải hot path cần tối ưu.

**Không lưu để tái sử dụng, nhưng vẫn phải lưu để chịu trách nhiệm giải trình — 2 mục đích khác nhau, không được lẫn:** dù không cache lại quyết định để dùng cho commission khác, quyết định **cho đúng commission này** phải để lại dấu vết đầy đủ — ai duyệt, dựa trên căn cứ gì, mã số nào, lúc nào — vì đây là bằng chứng có thể bị lôi ra khi tranh chấp report sau này.

`level2_inspection_commission` (§5) mở rộng thêm:

| Field | Loại | Ghi chú |
|---|---|---|
| `orgVerificationType` | Enum (`HARDCODED_MAJOR`, `BOA_VERIFIED`, `ADMIN_AD_HOC`) | Nhóm nào trong 3 nhóm allowlist |
| `verificationReference` | String, nullable | Accreditation certificate number đã tra — `NULL` cho `HARDCODED_MAJOR` (đã verify 1 lần khi thêm vào bảng allowlist, không verify lại mỗi lần dùng) |
| `verifiedByAdminId` | UUID, nullable | `NULL` cho `HARDCODED_MAJOR` cùng lý do trên |
| `verifiedAt` | Timestamp, nullable | |

`InitiateLevel2Inspection` (§3.4) bước 2 mở rộng: nếu `org` thuộc nhóm `BOA_VERIFIED`/`ADMIN_AD_HOC`, bắt buộc Admin nhập `verificationReference` trước khi commission được tạo — không cho tạo commission mà thiếu căn cứ. Bước 5 (INSERT `audit_record`, `source_type = LEVEL2_INSPECTION_COMMISSIONED`) đã tồn tại sẵn — chỉ mở rộng `content` thêm 3 field trên, **không cần event/source_type mới**. Hash chain hiện có (`hash-chain-phase2-design.md`) tự động cho quyết định này tính bất biến — đúng cơ chế đã dùng cho mọi quyết định quan trọng khác trong hệ thống, không phải cơ chế riêng mới.

**Đã thu hẹp (08/07/2026, L4) — từ "deferred, chưa biết tra ở đâu" sang "có nguồn tra online rõ ràng":** verify accreditation không còn là hộp đen. Ba nguồn tra online cụ thể:
- **BoA-VIAS** (`boa.gov.vn`) — VIAS = scheme Inspection Bodies theo ISO/IEC 17020, đúng loại tổ chức cho Level 2 inspector. BoA (nay thuộc STAMEQ, Bộ KH&CN từ 01/01/2025) là signatory ILAC-MRA/IAF-MLA/APAC-MRA — tra thẳng cho tổ chức trong nước.
- **IAF CertSearch** (`iafcertsearch.org`) — global database validate chứng nhận, BoA Vietnam có mặt.
- **ILAC Signatory Search** (`ilac.org/signatory-search`) — directory accreditation body, có cả code nhúng vào website để host search facility.

Admin tra tay trên 3 nguồn này (vài phút/lần, sự kiện tần suất thấp — không phải hot path). **Tự động hoá full qua REST API là enhancement, không phải bây giờ:** chưa xác nhận BoA có API export danh sách VIAS dạng JSON (có thể vẫn phải tra trên portal). Nhưng từ "không biết tra đâu" → "3 nguồn tra online xác định" là nâng cấp thật cho khả năng defend — không còn là điểm mù.

### 3.3 Ingestion flow — hòm mail platform thay thế vị trí Admin trong 3-mail song song

**Chốt (04/07/2026, thay thế bản 03/07/2026):** giữ nguyên nguyên tắc 3-mail song song (SGS/Bureau Veritas gửi report trực tiếp tới 3 nơi cùng lúc, không qua tay 1 người trung gian) — nhưng đổi vị trí thứ 3: thay vì Admin dùng inbox cá nhân, dùng 1 hòm mail thuộc platform (`intake@...`), nhận qua SendGrid Inbound Parse webhook. Buyer, seller vẫn nhận bản gốc trực tiếp từ tổ chức kiểm định như cũ, không đổi — chain-of-custody argument gốc (không để 1 actor làm gatekeeper duy nhất) không bị ảnh hưởng, vì hòm intake chỉ thay thế đúng vị trí Admin, không gộp cả 3 vị trí về 1 mối.

**Vì sao đổi:** hòm mail platform lưu được kèm metadata máy đọc được (kết quả SPF/DKIM, timestamp hệ thống, raw headers) mà 1 inbox cá nhân không có — và không phụ thuộc Admin có đọc mail đúng lúc, có xoá mail, hay đổi máy hay không. Buộc hành động của Admin đi qua hệ thống có audit, thay vì qua inbox không ai theo dõi được.

**Giới hạn không giải quyết được (ghi rõ, không giả vờ):** platform chỉ xác nhận được leg gửi tới `intake@...` — vì đó là hệ thống của mình, webhook fire tức là có bằng chứng nhận. Leg tới buyer/seller thì không xác nhận được, vì đó là inbox của người khác. Đây không phải thiếu sót kỹ thuật — nếu platform xác nhận được, nghĩa là platform phải có quyền truy cập inbox buyer/seller, và lúc đó "bản gốc độc lập ngoài platform" đã mất chính tính độc lập khiến nó có giá trị. Xử lý: buyer/seller có thể tự bấm 1 nút "đã nhận report từ [org]" trong app khi milestone đang chờ Level 2 report — không chứng minh được org có gửi hay không, chỉ ghi lại actor mình quản lý (buyer/seller) đã tuyên bố gì, cùng pattern với self-report vốn đã dùng ở các bước khác trong hệ thống.

### 3.4 Commission — use case chủ động yêu cầu tổ chức kiểm định vào cuộc

**Chốt (04/07/2026):** thiếu bước platform chủ động yêu cầu tổ chức kiểm định vào cuộc trước khi report tự nhiên xuất hiện. Use case mới, đứng trước ingestion trong luồng thời gian:

```
InitiateLevel2Inspection(contractId):
  Role: ADMIN. Trigger: DisputeRoutingService route = LEVEL_2 (milestone-escrow §3.2)

  1. Validate contract đang ở state cho phép escalate Level 2
  2. org = Contract.contractTerms.level2InspectorOrg
     NULL → REJECT (theo §3.2, NULL chỉ hợp lệ khi hợp đồng cap cứng Level 1.5)
  3. buyerEmail, sellerEmail lấy từ user-service (không nhập tay)
  4. INSERT level2_inspection_commission (commission_id, contract_id, org,
     status = REQUESTED, requested_at = now())
  5. INSERT audit_record (contract_id, source_type = LEVEL2_INSPECTION_COMMISSIONED,
     content = {commissionId, intakeAddress: "intake@agricontract.vn",
                buyerEmail, sellerEmail, org}, timestamp = now())
  6. Publish `notification.level2_commission_requested` tới notification-service,
     payload `{eventId, commissionId, orgEmail, buyerEmail, sellerEmail,
     intakeAddress, contractContext}`. Notification gửi mail transactional tới org
     và bản xác nhận cho buyer/seller; inspection-service không gọi SendGrid trực tiếp.
```

**Ranh giới rõ ràng, không tự huyễn:** bước 6 chỉ tạo ra *yêu cầu*, không tạo ra *sự đồng ý*. SGS/Bureau Veritas có thật sự nhận job, lên lịch, cử người đi kiểm định hay không vẫn là quan hệ thương mại thật ngoài hệ thống (báo giá, PO, lịch hẹn) — không automate được, và không cần automate, vì đó không phải nơi tạo ra bằng chứng. Cái audit record ở bước 5 chứng minh **platform đã yêu cầu gửi đi đâu, lúc nào** — không chứng minh org có làm đúng theo yêu cầu đó.

### 3.5 Case ID — join key giữa commission và report, không tự động gán thẳng

**Chốt (04/07/2026):** hầu hết tổ chức TIC (Testing, Inspection, Certification) track job nội bộ bằng mã case riêng của họ, độc lập với platform. Tận dụng mã này làm join key, thay vì xin org tuân theo format của mình — khác về bản chất so với việc yêu cầu nhét metadata tuỳ ý vào nội dung report.

Khi org reply ack (có thể kèm case ID trong subject hoặc nội dung), webhook parse best-effort, đưa ra **gợi ý**, không tự gán:

```
ParseCommissionAck(rawEmail):   // webhook handler, system-triggered
  1. Extract case ID string nếu tìm được pattern hợp lý trong subject/body
  2. Match sender domain ↔ commission đang ở status = REQUESTED cho org tương ứng
  3. Nếu tìm được đúng 1 commission khớp domain + đang REQUESTED:
     → gợi ý cho Admin: "case-id X có thể thuộc commission Y"
  4. KHÔNG tự update level2_inspection_commission — chỉ hiển thị gợi ý

ConfirmCommissionCaseId(commissionId, caseId):
  Role: ADMIN
  1. UPDATE level2_inspection_commission
     SET intake_case_id = caseId, status = CASE_ID_CONFIRMED,
         case_id_confirmed_at = now()
     WHERE commission_id = commissionId
```

**Vì sao không tự gán thẳng:** subject/body email thật thường chứa nhiều số cùng lúc (báo giá, PO, ngày tháng, mã job) — quy ước không đồng nhất giữa các nhân viên/khu vực của org. Auto-match sai sẽ tạo ra **confident wrong** (hệ thống tự tin gắn nhầm hợp đồng) — nguy hiểm hơn nhiều so với **honest fail** (không match được, để `PENDING_REVIEW` chờ người xử lý), vì sai lầm loại đầu nằm im tới đúng lúc dispute thật cần dùng report mới lộ ra, lúc đó bằng chứng gắn sai hợp đồng là thứ bị khai thác đầu tiên. Chi phí xác nhận case ID sớm (1 click, lúc nhận ack) thấp hơn nhiều so với chi phí phát hiện sai ở report cuối — nên đẩy điểm quyết định lên sớm, nhưng không bỏ nó.

### 3.6 Ingestion report cuối — hash đóng băng trước khi có người chạm vào

**Chốt (04/07/2026):** thay Admin tự tải và upload làm luồng chính bằng ingestion tự động + review, giữ use case gốc lại làm fallback.

```
IngestExternalInspectionReportEmail(rawEmail):   // webhook handler, system-triggered
  1. Verify SPF/DKIM từ payload SendGrid — lưu kết quả, KHÔNG dùng để reject cứng,
     chỉ làm tín hiệu cho bước review (raise bar domain, không chứng minh thẩm quyền
     người gửi — Admin vẫn là quyết định cuối)
  2. reportHash = SHA256(content + timestamp + senderDomain)
     — tính NGAY, trước khi bất kỳ ai (kể cả Admin) chạm vào nội dung
  3. Upload file qua file-service → fileHash
  4. Tìm case ID trong report, lookup intake_case_id trong level2_inspection_commission
     (status = CASE_ID_CONFIRMED, cùng org) → match được → commissionId cụ thể
     Không match → commissionId = NULL
  5. INSERT inspection_report (tier = LEVEL_2, commission_id, contract_id (denormalized
     từ commission nếu match được, NULL nếu không), status = PENDING_REVIEW,
     ingestion_source = AUTO_EMAIL, spf_dkim_result, report_hash, ...)
  6. KHÔNG publish audit-service ở bước này

ReviewPendingExternalReport(pendingReportId, commissionId, decision, externalVerificationStatus):
  Role: ADMIN. Endpoint KHÔNG nhận tham số file — về mặt kỹ thuật Admin không có
  khả năng thay file report qua đường này.

  1. Nếu report đã auto-match commissionId khác với tham số truyền vào → REJECT request
     (không cho Admin âm thầm đổi match tự động qua đường vòng)
  2. commission = level2_inspection_commission WHERE commission_id = commissionId
     org từ commission phải khớp Contract.contractTerms.level2InspectorOrg — không khớp → REJECT
  3. decision = REJECT → status = REJECTED, dừng
  4. decision = APPROVE:
     status = CONFIRMED
     confirmed_by_admin_id = jwt.sub
     external_verification_status = externalVerificationStatus (optional — Admin tự đi
       verify qua dịch vụ công khai của org, nếu có, rồi điền kết quả cùng lúc duyệt)
     Publish audit-service, source_type = EXTERNAL_INSPECTION_REPORT
```

**Publish dời sang lúc CONFIRMED, không phải lúc ingest** — tránh mail rác/gắn nhầm hợp đồng lọt vào audit trail bất biến trước khi có người xác nhận.

**Fallback:** giữ nguyên use case ingest thủ công gốc (`SubmitExternalInspectionReport`, Admin tự tải và upload report, `role ADMIN`, không phải role `INSPECTOR`), đổi `ingestion_source = ADMIN_MANUAL` — dùng khi org gửi về địa chỉ liên hệ quen thuộc thay vì `intake@...`, hoặc webhook lỗi. `org` vẫn lấy từ `Contract.contractTerms.level2InspectorOrg` (không cho Admin tự gõ lại), `reportHash = SHA256(content + timestamp + org)`, `uploadedByAdminId`/`confirmed_by_admin_id` lấy từ JWT của Admin đang thao tác. Không có điểm nào trong luồng mới làm hệ thống kẹt cứng nếu tự động hoá thất bại.

---

## 4. Hash Chain — `source_type` mới

**Chốt (03/07/2026, bổ sung 04/07/2026):** `audit_record.source_type` (`hash-chain-phase2-design.md` §3) hiện có `MILESTONE_EVENT | CONTRACT_SIGNED | INSPECTION_REPORT`. Doc này thêm 3 giá trị:

| source_type | Actor đứng sau | Cơ chế xác thực |
|---|---|---|
| `INSPECTION_REPORT` (Level 1.5) | INSPECTOR đã KYC, đã login | `Signature` + RBAC |
| `EXTERNAL_INSPECTION_REPORT` (Level 2, report cuối) | Không có actor login — chỉ Admin thao tác | Auto-intake (SPF/DKIM signal) + Admin `CONFIRMED` qua `ReviewPendingExternalReport` (§3.6), hoặc `ADMIN_MANUAL` fallback |
| `LEVEL2_INSPECTION_COMMISSIONED` (Level 2, yêu cầu commission) | Admin | RBAC bình thường — ghi nhận platform đã yêu cầu gửi report đi đâu, không chứng minh org có tuân theo |

**Không gộp chung** dù cùng mục đích nghiệp vụ tổng quát — vì sức nặng bằng chứng khác nhau thật sự. `INSPECTION_REPORT` đứng sau 1 actor đã KYC/login; `EXTERNAL_INSPECTION_REPORT` là *kết quả đã xác nhận* của 1 report không actor login; `LEVEL2_INSPECTION_COMMISSIONED` chỉ ghi lại *yêu cầu*, không phải kết quả. Gộp chung 1 nhãn là che giấu khác biệt evidentiary — đúng thứ hội đồng bảo vệ sẽ hỏi nếu phát hiện các loại report khác nhau về sức nặng pháp lý lại đứng chung 1 nhãn trong audit trail.

---

## 5. Database — Additive

```sql
-- signature (mở rộng enum + thêm field cho nhánh INSPECTOR)
ALTER TABLE signature ADD COLUMN report_id UUID NULL;
-- KHÔNG dùng "REFERENCES inspection_report(report_id)" — sửa (04/07/2026), phát hiện lúc rà kiến trúc:
--   `signature` sống ở contract_db (contract-service), `inspection_report` sống ở DB riêng của
--   inspection-service. FK constraint xuyên service vi phạm database-per-service (đã chốt từ đầu
--   cho toàn hệ thống). Integrity giữ ở application layer — cùng pattern sellerEvidenceFileId/
--   buyerEvidenceFileId → file-service (không REFERENCES), và cùng cách contract_id trên chính
--   bảng inspection_report bên dưới cũng không REFERENCES ngược lại contract_db.
-- signer_role: 'BUYER' | 'SELLER' | 'INSPECTOR'
-- Nhánh INSPECTOR: report_id NOT NULL, constraint UNIQUE(report_id) — không dùng UNIQUE(contract_id, signer_role)
-- Nhánh BUYER/SELLER: report_id NULL, giữ nguyên UNIQUE(contract_id, signer_role) như signature-phase2-design.md §3

-- ContractTerms, thêm field mới (contract_db)
ALTER TABLE contract_terms ADD COLUMN level2_inspector_org VARCHAR(255) NULL;

-- inspection-service — bảng track việc commission Level 2 org, tách biệt khỏi report
CREATE TABLE level2_inspection_commission (
    commission_id         UUID PRIMARY KEY,
    contract_id           UUID NOT NULL,        -- FK thường, KHÔNG unique — 1 hợp đồng có thể
                                                  -- escalate Level 2 nhiều lần theo thời gian,
                                                  -- cùng lý do đã áp cho Signature-INSPECTOR ở §2.1
    org                    VARCHAR(255) NOT NULL,
    intake_case_id         VARCHAR(255) NULL,    -- NULL cho tới khi Admin confirm ack
    status                 VARCHAR(20) NOT NULL, -- REQUESTED | CASE_ID_CONFIRMED
    requested_at           TIMESTAMP NOT NULL,
    case_id_confirmed_at   TIMESTAMP NULL
);
-- UNIQUE(org, intake_case_id) — case ID do org tự phát hành, scope theo org tránh trùng

-- inspection-service — 1 bảng, phân theo tier, schema cuối cùng sau khi merge auto-intake flow
CREATE TABLE inspection_report (
    report_id                        UUID PRIMARY KEY,
    contract_id                      UUID NULL,     -- NOT NULL cho LEVEL_1_5 và LEVEL_2 ADMIN_MANUAL;
                                                       -- NULL tạm thời hợp lệ cho LEVEL_2 AUTO_EMAIL khi
                                                       -- status = PENDING_REVIEW (chưa match được commission)
                                                       -- Denormalized, tiện query — KHÔNG dùng làm join key
                                                       -- thật cho LEVEL_2, xem commission_id bên dưới.
    tier                              VARCHAR(10) NOT NULL,    -- 'LEVEL_1_5' | 'LEVEL_2'
    content                           JSONB NOT NULL,
    report_hash                       VARCHAR(64) NOT NULL,    -- LEVEL_2 AUTO_EMAIL: tính NGAY lúc ingest,
                                                                  -- trước khi ai (kể cả Admin) chạm vào — đóng băng
    -- nhánh LEVEL_1_5 — bắt buộc:
    inspector_id                     UUID NULL,               -- = signerUserId, NOT NULL nếu tier = LEVEL_1_5
    -- nhánh LEVEL_2 — bắt buộc:
    external_org                     VARCHAR(255) NULL,       -- NOT NULL nếu tier = LEVEL_2
    confirmed_by_admin_id            UUID NULL,               -- NOT NULL nếu tier = LEVEL_2 và status = CONFIRMED.
                                                                  -- Đổi tên từ uploaded_by_admin_id (04/07/2026) —
                                                                  -- semantics đổi từ "ai upload" sang "ai xác nhận",
                                                                  -- vì AUTO_EMAIL không còn ai "upload" theo nghĩa cũ.
    received_via                     VARCHAR(50) NULL,        -- 'EMAIL' | khác, chỉ LEVEL_2
    status                           VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
                                                                  -- 'PENDING_REVIEW' | 'CONFIRMED' | 'REJECTED' —
                                                                  -- chỉ LEVEL_2 AUTO_EMAIL dùng hết 3 giá trị;
                                                                  -- LEVEL_1_5 và LEVEL_2 ADMIN_MANUAL giữ default CONFIRMED
    ingestion_source                 VARCHAR(20) NULL,        -- 'AUTO_EMAIL' | 'ADMIN_MANUAL' — chỉ LEVEL_2
    spf_dkim_result                  VARCHAR(20) NULL,        -- chỉ AUTO_EMAIL
    commission_id                    UUID NULL REFERENCES level2_inspection_commission(commission_id),
                                                                  -- join key thật cho LEVEL_2 (cùng DB inspection-service,
                                                                  -- REFERENCES hợp lệ ở đây — khác trường hợp signature.report_id
                                                                  -- ở trên, vốn xuyên service)
    external_verification_status     VARCHAR(20) NULL,        -- 'NOT_CHECKED' | 'VERIFIED' | 'UNVERIFIED' | 'UNAVAILABLE' —
                                                                  -- optional, chỉ LEVEL_2, Admin tự điền nếu org có dịch vụ verify công khai
    external_verification_checked_at TIMESTAMP NULL,
    created_at                       TIMESTAMP NOT NULL DEFAULT now()
);
-- Invariant (inspector_id XOR (external_org AND (confirmed_by_admin_id nếu status=CONFIRMED)))
-- check ở use-case layer, không phải DB constraint — cùng pattern active-flag đã dùng ở product-service.
```

---

## 6. Known Limitations (có chủ đích, không phải điểm mù)

- **Commission là hành động thương mại thật ngoài hệ thống.** Platform ghi lại đã yêu cầu gửi report đi đâu, lúc nào (`LEVEL2_INSPECTION_COMMISSIONED`) — không chứng minh org có nhận lời, có tuân theo đúng 3 địa chỉ hay không. Không giải được bằng thiết kế, chỉ giảm được rủi ro qua fallback thủ công.
- **Xác nhận buyer/seller đã nhận mail là self-report, không phải bằng chứng độc lập.** App chỉ ghi lại actor tuyên bố gì, không xác minh được nội dung mail họ nhận có khớp bản Admin xử lý hay không.
- **Case ID matching là best-effort, phụ thuộc quy ước riêng của từng org.** Không match được luôn rơi về `PENDING_REVIEW`, không đoán bừa — honest fail được ưu tiên hơn confident wrong.
- **External verification (SGS document verification hoặc tương đương) là thao tác tay qua web form công khai, không phải API.** Không thể là gate tự động trong `ReviewPendingExternalReport`, chỉ là field Admin điền song song lúc duyệt. **Known Limitation:** không xác nhận được trong phạm vi đồ án liệu Bureau Veritas có dịch vụ document-verification tương đương SGS hay không — nếu deployment chọn BV, dùng giá trị `UNAVAILABLE` cho field này, không block luồng duyệt.
- **Tự động hoá tra cứu accreditation qua API là enhancement, không phải Phase 2 (Known Limitation).** 3 nguồn tra cứu (BoA-VIAS, IAF CertSearch, ILAC Signatory Search) hiện Admin tra tay; không xác nhận được BoA có API export JSON trong phạm vi đồ án — nối API là việc sau, không đổi logic duyệt (§3.2).
- **SPF/DKIM chỉ raise bar domain gửi, không chứng minh thẩm quyền người gửi.** Admin vẫn là quyết định cuối cho tính hợp lệ của report.
- **Endpoint duyệt (`ReviewPendingExternalReport`) không nhận file, và `reportHash` đóng băng trước khi Admin chạm vào** — đây là cơ chế kỹ thuật thật, không phải quy tắc miệng, ngăn Admin thay file report đã ingest.
- **3-mail chỉ là passive backup**, không có active reconciliation tự động — không có bước nào chủ động so sánh bản buyer/seller nhận với bản platform xử lý, chỉ nằm đó dùng khi có tranh chấp thật cần lôi ra đối chiếu. Kế thừa đúng giới hạn "phải có người chủ động nhìn" từ `hash-chain-phase2-design.md` §6.
- **WebAuthn/passkey cho INSPECTOR** — cùng lý do đã ghi ở `signature-phase2-design.md` §8: không đổi tier pháp lý (vẫn thiếu chứng thư CA cấp phép, Điều 22 khoản 3(đ)), chỉ nâng chất lượng bằng chứng khi tranh chấp, không nâng presumption pháp lý mặc định. Để ngoài phạm vi, có chủ đích.

---

## 7. Status — Inspection Design

**Chốt (03/07/2026):** Level 1.5 = actor thật, mở rộng `Signature` schema (`signerRole` thêm `INSPECTOR`, thêm `reportId`, constraint `UNIQUE(reportId)` thay vì theo milestone/dispute), KYC nội dung khác Buyer/Seller (chứng chỉ kiểm định thay vì thẩm quyền đại diện), `reportHash` giữ nguyên công thức gốc, session freshness tách riêng `inspectionAuthMaxAgeSeconds` = 1800s. Level 2 = không Signature, không RBAC; `level2InspectorOrg` NOT NULL có điều kiện trong `ContractTerms`, negotiate lúc ký, không hardcode danh sách tổ chức.

**Chốt bổ sung (04/07/2026) — merge addendum + fix kiến trúc:**
- **Level 2 ingestion chuyển từ thuần thủ công sang auto-intake + human-confirm:** hòm mail platform (`intake@...`, qua SendGrid Inbound Parse) thay thế vị trí Admin trong 3-mail song song gốc (buyer/seller không đổi, §3.3). Thêm use case `InitiateLevel2Inspection` ghi nhận platform đã yêu cầu commission org đi đâu, lúc nào (§3.4). Case ID của org dùng làm join key qua bảng `level2_inspection_commission`, match tự động chỉ ở mức gợi ý, Admin xác nhận thật (§3.5). Report cuối ingest tự động, hash đóng băng ngay khi nhận, publish audit chain dời sang lúc Admin `CONFIRMED` qua `ReviewPendingExternalReport` (§3.6). Giữ ingestion thủ công gốc làm fallback (`ingestion_source = ADMIN_MANUAL`).
- **`audit_record.source_type` có 3 giá trị mới:** `INSPECTION_REPORT`, `EXTERNAL_INSPECTION_REPORT`, `LEVEL2_INSPECTION_COMMISSIONED` — tách riêng vì sức nặng bằng chứng khác nhau (§4).
- **Fix cross-service FK bug (phát hiện lúc rà kiến trúc 04/07/2026):** `signature.report_id` **không** dùng `REFERENCES inspection_report(report_id)` nữa — vi phạm database-per-service, vì `signature` (contract_db) và `inspection_report` (inspection-service DB) là 2 database khác nhau. Giữ integrity ở application layer, cùng pattern các cross-service reference khác trong hệ thống (§5).
- **KI-2 đóng (§3.2):** allowlist 3 nhóm cho `level2InspectorOrg` — major quốc tế hardcode, trong nước verify qua BoA-VIAS, "lạ" thì Admin duyệt case-by-case và **không** lưu vào danh sách dùng chung (né bài toán dọn danh sách khi tổ chức phá sản/mất accreditation). Verify qua accreditation certificate number, tra đúng cơ quan công nhận quốc gia đã phát hành. **Nguồn tra cứu (thu hẹp 08/07/2026, L4):** BoA-VIAS (`boa.gov.vn`) + IAF CertSearch (`iafcertsearch.org`) + ILAC Signatory Search (`ilac.org/signatory-search`) — 3 nguồn online xác định, Admin tra tay; tự động hoá full qua API là enhancement, chưa xác nhận BoA có API JSON.

**Nguyên tắc xuyên suốt cho phần auto-intake:** tự động hoá phần lặp lại, nhàm chán, không cần phán đoán (nhận mail, tính hash, gợi ý match) — giữ nguyên quyết định cuối (cái biến 1 file thành bằng chứng) ở người, có RBAC, có audit trail, không thể lặng lẽ override. Mục tiêu không phải loại bỏ con người khỏi quyết định — mục tiêu là không để 1 người là điểm duy nhất quyết định cái gì là bằng chứng thật.

Inspection — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.** File `inspection-phase2-design-addendum.md` đã merge vào đây — có thể xoá khỏi project knowledge.

---

*Design session: 03/07/2026 · Addendum merge + auto-intake flow: 04/07/2026 · Fix cross-service FK: 04/07/2026 · 08/07/2026 (L4: thu hẹp tra cứu accreditation — thêm nguồn cụ thể IAF CertSearch + ILAC Signatory Search + BoA-VIAS, từ "deferred chưa biết tra đâu" → "3 nguồn online xác định", §3.2) · Cập nhật 13/07/2026 (làm rõ 2 giả định ngoài scope — BV document-verification + BoA API — thành Known Limitation §6) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.***
