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

> **Role vận hành (18/07/2026):** các use case Admin trong doc này (review/moderate/nhập liệu hằng ngày) nhận thêm role `OPERATOR` theo `data-governance-phase2-design.md` §5 — permission matrix ở đó là source of truth, doc này không lặp lại; flow/state/schema không đổi.


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

**Chốt (03/07/2026):** không tạo `Dispute` aggregate mới chỉ để có unique key — `Dispute` hiện không tồn tại như 1 entity riêng, chỉ là state trong `Milestone` state machine (`milestone-escrow-phase2-design.md` §3.2). *(Ghi chú 19/07/2026: milestone-escrow §6.4 từ nay có `BreachCase` — aggregate cấp contract cho cáo buộc vi phạm/attribution. Nó KHÔNG đảo quyết định này: milestone `CONTESTED` vẫn là state, Signature vẫn key theo `UNIQUE(reportId)`, inspection không tham chiếu BreachCase — contract-service tự nối phán quyết từ report (`resultHash`) vào `AttributionDecision` §6.4b, inspection-service không cần biết.)* Thay vào đó, thêm field `reportId` (FK logic → `inspection_report.report_id`, §5 — **không phải FK database**, xem lý do ở §5) vào `Signature` cho nhánh `signerRole = INSPECTOR`. Đổi constraint: `UNIQUE(reportId)` — mỗi report chỉ ký đúng 1 lần, tự nhiên đúng cho cả trường hợp 1 milestone bị dispute nhiều lần (force majeure xong rồi lại dispute số lượng lần nữa), không cần khái niệm entity mới. `contractId` vẫn giữ trên `Signature` (denormalized, tiện query theo hợp đồng), nhưng không còn nằm trong unique constraint cho nhánh INSPECTOR.

**Sửa (17/07/2026) — bảng vật lý dời về inspection-service, đóng lỗ cross-service write:** thiết kế trên (mở rộng enum + `report_id` vào bảng `signature` của `contract_db`) để lại 1 lỗ chưa ai định nghĩa: inspector thao tác ở inspection-service, nhưng bảng nằm ở `contract_db` — inspection-service không được ghi DB của service khác (database-per-service), và không có internal API nào của contract-service cho việc này. Chốt: **nhánh INSPECTOR dùng bảng riêng `inspector_signature` trong DB của inspection-service** — cùng shape schema (`signerUserId`, `authTime`, `signedAt`, `reportHash` ở vị trí `signedContentHash`, `ipAddress`), `UNIQUE(report_id)`, và `report_id` giờ `REFERENCES inspection_report` **hợp lệ** (cùng DB — gọn hơn cả bản cũ vốn phải bỏ FK). Bảng `signature` bên `contract_db` giữ nguyên thuần `BUYER`/`SELLER` như `signature-phase2-design.md` §3 gốc, không mở rộng enum. Khái niệm "dùng chung mô hình identity với Buyer/Seller" không đổi — chung *cơ chế* (step-up, `authTime`, session freshness §2.4), không chung *bảng vật lý*.

### 2.2 KYC-gate — nội dung khác Buyer/Seller, cơ chế giống

Cùng gate lúc đăng ký account, fail-closed by default (`signature-phase2-design.md` §5), nhưng xác minh nội dung khác:

- Buyer/Seller: xác minh **thẩm quyền đại diện** (BLDS 2015 Điều 142) — người bấm nút có phải người có quyền đại diện pháp nhân không.
- INSPECTOR Level 1.5: xác minh **chứng chỉ/giấy phép hoạt động kiểm định thương mại** — câu hỏi khác hẳn, không phải "ai đại diện" mà "tổ chức này có đủ tư cách pháp lý phát hành report có giá trị làm bằng chứng không".

`authorizationExpiresAt` (đã có sẵn trên `User`) tái dùng được nguyên — giấy phép kiểm định cũng có ngày hết hạn, cùng cơ chế nhập tay từ giấy tờ thật, không hardcode. Timestamp/expiry theo UTC/ICT convention của `milestone-escrow-phase2-design.md` §1.1; giấy chỉ ghi date hết hạn cuối ngày ICT.

### 2.3 Inspection measurement result và hash commitment (chốt 18/07/2026; sửa ownership 23/07/2026)

Inspection-service sở hữu schema finding/result dùng chung cho Level 1.5 và Level 2:

```text
InspectionSettlementResultV1 {
  resultSchemaVersion: "1.0",
  measuredQuantityKg: decimal >= 0,
  acceptedQuantityKg: decimal >= 0 and <= measuredQuantityKg,
  measurementStatus: COMPLETE | INCONCLUSIVE,
  actualQualityMetrics: CoffeeActualMetrics | RiceActualMetrics |
                        RubberActualMetrics | CashewActualMetrics,
  inconclusiveReasons?: string[],
  determinedAt: timestamp
}
```

Actual union discriminated bằng `commodity`; variant phải khớp commodity của contract. Field đo được:

- Coffee: `type`, `moisturePercent`, `foreignMatterPercent`, `blackBrokenBeansPercent`.
- Rice: `brokenPercent`, `moisturePercent`, `chalkyKernelPercent`, `foreignMatterPercent`, `purityPercent`. **Không có `varietyName`** — đây là identity attribute của goods/committed spec, không phải kết quả đo và không thể làm reject.
- Rubber: `grade`, `dirtPercent`, `ashPercent`, `volatileMatterPercent`, `nitrogenPercent`, `plasticityRetentionIndex`.
- Cashew: `grade`, `moisturePercent`, `defectiveKernelPercent`, `foreignMatterPercent`, `kernelOutturnLbsPer80Kg`.

Inspection-service không còn sở hữu `qualityDisposition`. Nó chỉ cam kết quantity + actual measurements + việc report có kết luận được hay không. Contract-service đối chiếu committed spec/deviation policy để tạo disposition, quality discount và final entitlement; coffee `type` và rubber/cashew `grade` là ba categorical field duy nhất được exact-match-reject.

Certificate/phiếu kiểm theo batch mà Seller hoặc Buyer đính kèm lúc cân là delivery/dispute evidence riêng. Inspection-service không parse nó thành actual metric, không chép nó vào `InspectionSettlementResultV1`, không commit nó trong `resultHash`, không dùng nó để tự sinh disposition và không coi nó là bản thay thế cho report `CONFIRMED`.

Snapshot này là kết quả normalized tối thiểu, bất biến sau khi report được `CONFIRMED`. Không mang raw file, contact PII hay nội dung report tự do. `actualQualityMetrics` nằm trong `normalizedResult`, nên mọi actual metric đều nằm trong bytes được hash commit:

```text
normalizedResult = InspectionSettlementResultV1
resultHash = SHA256(RFC8785_canonical_json(normalizedResult))
reportHash = SHA256(RFC8785_canonical_json({
  reportId,
  contractId,
  milestoneId,
  tier,
  reportFileHash,
  resultHash,
  sourceTimestamp,
  actorOrSourceIdentity
}))
```

`reportFileHash` là hash bytes bất biến do file-service/inspection intake cung cấp. `actorOrSourceIdentity` là `inspectorId` cho Level 1.5 hoặc định danh nguồn external + `confirmedByAdminId` cho Level 2. Contract-service verify `resultHash` và `reportHash` trước khi dùng kết quả; inspection-service không tính tiền, không quyết định disposition và không quyết định escrow transition. Reviewer ở contract workflow chỉ xác nhận phép tính tất định giữa committed terms trong `signedContentHash` và actual metrics trong `resultHash`; không được thêm tiêu chí chủ quan ngoài deviation policy đã ký.

### 2.4 Session freshness — tách config riêng khỏi Signature

Window freshness là elapsed duration từ system timestamp UTC; không phụ thuộc timezone/client clock, theo convention chung milestone-escrow §1.1.

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

**Chốt (04/07/2026, thay thế bản 03/07/2026):** giữ nguyên nguyên tắc 3-mail song song (SGS/Bureau Veritas gửi report trực tiếp tới 3 nơi cùng lúc, không qua tay 1 người trung gian) — nhưng đổi vị trí thứ 3: thay vì Admin dùng inbox cá nhân, dùng 1 hòm mail thuộc platform (`intake@...`), nhận qua **IMAP polling do file-service vận hành** (**sửa 17/07/2026** — đồng bộ với quyết định đã chốt ở `file-service-phase2-design.md` §4.1, supersede phương án SendGrid Inbound Parse của bản 04/07: webhook cần domain thật + verify DNS + public HTTPS endpoint, quá nặng cho scope; file-service là owner duy nhất của mailbox `intake@...`, inspection-service không tự đọc mail — chỉ consume event). Buyer, seller vẫn nhận bản gốc trực tiếp từ tổ chức kiểm định như cũ, không đổi — chain-of-custody argument gốc (không để 1 actor làm gatekeeper duy nhất) không bị ảnh hưởng, vì hòm intake chỉ thay thế đúng vị trí Admin, không gộp cả 3 vị trí về 1 mối.

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
  6. Publish `notification.level2_commission_requested` tới notification-service bằng
     executable command envelope `{eventId, eventType, eventVersion, occurredAt,
     producer, aggregateId, correlationId, causationId, payload}`; `payload` chính xác là
     `{commissionId, contractId, recipients:[{userId,email,role}], org,
     intakeAddress, contractContext}`. Recipient của org dùng `userId = null`, role
     `INSPECTOR`; buyer/seller dùng canonical user IDs. Notification gửi mail
     transactional tới org và bản xác nhận cho buyer/seller; inspection-service không
     gọi SendGrid trực tiếp.
```

**Ranh giới rõ ràng, không tự huyễn:** bước 6 chỉ tạo ra *yêu cầu*, không tạo ra *sự đồng ý*. SGS/Bureau Veritas có thật sự nhận job, lên lịch, cử người đi kiểm định hay không vẫn là quan hệ thương mại thật ngoài hệ thống (báo giá, PO, lịch hẹn) — không automate được, và không cần automate, vì đó không phải nơi tạo ra bằng chứng. Cái audit record ở bước 5 chứng minh **platform đã yêu cầu gửi đi đâu, lúc nào** — không chứng minh org có làm đúng theo yêu cầu đó.

### 3.5 Case ID — join key giữa commission và report, không tự động gán thẳng

**Chốt (04/07/2026):** hầu hết tổ chức TIC (Testing, Inspection, Certification) track job nội bộ bằng mã case riêng của họ, độc lập với platform. Tận dụng mã này làm join key, thay vì xin org tuân theo format của mình — khác về bản chất so với việc yêu cầu nhét metadata tuỳ ý vào nội dung report.

Khi org reply ack (có thể kèm case ID trong subject hoặc nội dung), inspection-service consume `file.email_notice` từ file-service (**sửa 17/07/2026** — mail ack không attachment không tạo File, file-service §4.1) và parse best-effort, đưa ra **gợi ý**, không tự gán:

```
ParseCommissionAck(emailNotice):   // consumer của file.email_notice, system-triggered
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

**Chốt (04/07/2026):** thay Admin tự tải và upload làm luồng chính bằng ingestion tự động + review, giữ use case gốc lại làm fallback. (Cơ chế nhận mail **sửa 17/07/2026**: IMAP qua file-service — §3.3; luồng dưới viết lại theo handshake `file.ready`.)

```
IngestExternalInspectionReportEmail(fileReadyEvent):   // consumer file.ready
                                                       // (ingestChannel = EMAIL_INTAKE)
  0. (Ở file-service, TRƯỚC event này — owner: file-service §4) IMAP poll mailbox
     intake@... → extract MIME attachment → storageHash tính ngay lúc lưu MinIO →
     virus-scan → publish file.ready mang emailMeta {senderDomain, spfDkimResult,
     subject, receivedAt}. Bytes bất biến từ điểm này (storageHash).
  1. Đọc spfDkimResult từ emailMeta — lưu kết quả, KHÔNG dùng để reject cứng,
     chỉ làm tín hiệu cho bước review (raise bar domain, không chứng minh thẩm quyền
     người gửi — Admin vẫn là quyết định cuối)
  2. Lấy bytes qua GetFile(fileId) nội bộ →
     reportFileHash = SHA256(content)
     — tính NGAY khi consume event, trước khi bất kỳ Admin nào thấy report.
     storageHash + reportFileHash đóng băng raw bytes trước con người; reportHash cuối
     chỉ được tính khi report CONFIRMED và đã có normalizedResult/resultHash (§2.3).
  3. Giữ fileId làm reference (file đã nằm sẵn ở file-service từ bước 0,
     không upload lại)
  4. Tìm case ID trong subject/report, lookup intake_case_id trong
     level2_inspection_commission (status = CASE_ID_CONFIRMED, cùng org)
     → match được → commissionId cụ thể; không match → commissionId = NULL
  5. INSERT inspection_report (tier = LEVEL_2, commission_id, contract_id (denormalized
     từ commission nếu match được, NULL nếu không), status = PENDING_REVIEW,
     ingestion_source = AUTO_EMAIL, spf_dkim_result, report_hash, ...)
  6. KHÔNG publish audit-service ở bước này

ReviewPendingExternalReport(pendingReportId, commissionId, decision, externalVerificationStatus):
  Role: ADMIN. Endpoint KHÔNG nhận tham số file — về mặt kỹ thuật Admin không có
  khả năng thay file report qua đường này.

  0. Reload report, commission, milestone và report hiệu lực hiện có trong cùng
     transaction. Chỉ tiếp tục nếu report còn PENDING_REVIEW; milestone còn
     BUYER_RECEIVED hoặc CONTESTED; commission vẫn là match của report và còn
     khớp contract/org đã negotiate;
     và chưa có report effective/CONFIRMED khác cho cùng milestone.
     Stale ở bất kỳ guard nào → 409 REPORT_STATE_CONFLICT hoặc
     COMMISSION_STATE_CONFLICT; không đổi state.
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

`REJECTED` là terminal cho chính `inspection_report` đó. Nộp lại phải tạo commission/report mới; Phase 2 không thêm `rejectionReason`, resubmission state hoặc taxonomy lý do reject cho external report.

**Fallback:** giữ nguyên use case ingest thủ công gốc (`SubmitExternalInspectionReport`, Admin tự tải và upload report, `role ADMIN`, không phải role `INSPECTOR`), đổi `ingestion_source = ADMIN_MANUAL` — dùng khi org gửi về địa chỉ liên hệ quen thuộc thay vì `intake@...`, hoặc webhook lỗi. `org` vẫn lấy từ `Contract.contractTerms.level2InspectorOrg` (không cho Admin tự gõ lại); `reportFileHash = SHA256(content)` được tính ngay khi store, còn `reportHash` cuối tính theo §2.3 sau normalized confirmation. `uploadedByAdminId`/`confirmed_by_admin_id` lấy từ JWT của Admin đang thao tác.

---

## 4. Hash Chain — `source_type` mới

**Chốt (03/07/2026, bổ sung 04/07/2026):** `audit_record.source_type` (`hash-chain-phase2-design.md` §3) hiện có `MILESTONE_EVENT | CONTRACT_SIGNED | INSPECTION_REPORT`. Doc này thêm 3 giá trị:

| source_type | Actor đứng sau | Cơ chế xác thực |
|---|---|---|
| `INSPECTION_REPORT` (Level 1.5) | INSPECTOR đã KYC, đã login | `Signature` + RBAC |
| `EXTERNAL_INSPECTION_REPORT` (Level 2, report cuối) | Không có actor login — chỉ Admin thao tác | Auto-intake (SPF/DKIM signal) + Admin `CONFIRMED` qua `ReviewPendingExternalReport` (§3.6), hoặc `ADMIN_MANUAL` fallback |
| `LEVEL2_INSPECTION_COMMISSIONED` (Level 2, yêu cầu commission) | Admin | RBAC bình thường — ghi nhận platform đã yêu cầu gửi report đi đâu, không chứng minh org có tuân theo |

**Không gộp chung** dù cùng mục đích nghiệp vụ tổng quát — vì sức nặng bằng chứng khác nhau thật sự. `INSPECTION_REPORT` đứng sau 1 actor đã KYC/login; `EXTERNAL_INSPECTION_REPORT` là *kết quả đã xác nhận* của 1 report không actor login; `LEVEL2_INSPECTION_COMMISSIONED` chỉ ghi lại *yêu cầu*, không phải kết quả. Gộp chung 1 nhãn là che giấu khác biệt evidentiary — đúng thứ hội đồng bảo vệ sẽ hỏi nếu phát hiện các loại report khác nhau về sức nặng pháp lý lại đứng chung 1 nhãn trong audit trail.

**Bổ sung (17/07/2026) — transport vào chain, trước đây chưa đặt tên:** "publish audit-service"/"INSERT audit_record" ở §3.4/§3.6 chưa định nghĩa đi bằng đường nào — và inspection-service không được INSERT thẳng DB của audit-service. Chốt 2 domain event RabbitMQ, audit-service là consumer duy nhất ghi `audit_record` (cơ chế thống nhất toàn hệ thống — `hash-chain-phase2-design.md` §2.4):
- `inspection.level2_commissioned` — publish ở bước 5 của `InitiateLevel2Inspection` (§3.4; "INSERT audit_record" ở đó đọc là *yêu cầu ghi*, thực thi qua event này), payload = content của audit record → `source_type = LEVEL2_INSPECTION_COMMISSIONED`.
- `inspection.report_confirmed` — publish khi report thành `CONFIRMED` (Level 1.5 lúc submit hợp lệ; Level 2 lúc `ReviewPendingExternalReport` APPROVE hoặc nhánh `ADMIN_MANUAL`). Payload canonical: `{reportId, contractId, milestoneId, tier, normalizedResult: InspectionSettlementResultV1, resultHash, reportFileHash, reportHash, confirmedAt, inspectorId?, confirmedByAdminId?}`. `reportFileHash` is the minimum verification material implied by the approved requirement that contract-service recompute `reportHash`; it is a hash only, not raw file content. Audit-service map `tier` sang `INSPECTION_REPORT` / `EXTERNAL_INSPECTION_REPORT`; contract-service consume idempotently theo `eventId`/`reportId`, verify hashes và chỉ apply cho milestone `BUYER_RECEIVED` hoặc `CONTESTED` có cùng `contractId`/`milestoneId`.

Contract-service giữ ownership tính tiền và transition:

```text
effectiveUnitPrice = acceptedPriceAdjustment?.effectiveUnitPrice ?? agreedPrice
quantityAdjustedEntitlement =
  min(lockedAmount, normalizedResult.acceptedQuantityKg * effectiveUnitPrice)
qualityDiscountAmount =
  quantityAdjustedEntitlement * max(applicablePenaltyMetric.discountRate, default 0)
finalSellerEntitlement = quantityAdjustedEntitlement - qualityDiscountAmount
buyerRefundAmount = lockedAmount - finalSellerEntitlement
```

Contract-service tạo immutable `QualityAssessment`; `measurementStatus = INCONCLUSIVE` tiếp tục reinspection/Level 2 và không tạo attribution/remedy hoặc phát tiền. Nhiều metric penalty chỉ lấy discount rate lớn nhất một lần; reject zone tạo `NON_CONFORMING`. `min()` chỉ cap over-delivery; không áp quantity `toleranceRate`/Delta 2 lần thứ hai sau inspection.

Nếu đây là quality dispute, contract-service luôn mở/resolve Rổ B rồi hội tụ `AttributionDecision -> RemedyDecision -> remedy.finalized`; không phát đồng thời `milestone.settled`. Mapping disposition, `QUALITY_BELOW_COMMITTED`, inspection fee `LOSER_PAYS`, penalty base và per-fund conservation thuộc owner design `milestone-escrow-phase2-design.md` §3.3. Inspection-service không thêm ledger enum, quality-specific leg hoặc disposition vào `InspectionSettlementResultV1`.

---

## 5. Database — Additive

```sql
-- SUPERSEDED (17/07/2026): KHÔNG mở rộng bảng signature của contract_db nữa — xem §2.1.
-- Nhánh INSPECTOR dùng bảng riêng trong DB inspection-service: cross-service write là bất khả
-- dưới database-per-service (bản 04/07 bỏ được FK nhưng không trả lời được "ai ghi row này").
-- Bảng signature (contract_db) giữ nguyên BUYER/SELLER thuần — signature-phase2-design.md §3.
CREATE TABLE inspector_signature (
    signature_id     CHAR(36) PRIMARY KEY,
    report_id        CHAR(36) NOT NULL UNIQUE REFERENCES inspection_report(report_id),
                     -- cùng DB → REFERENCES hợp lệ, chặt hơn cả bản cũ (vốn phải bỏ FK)
    contract_id      CHAR(36) NOT NULL,           -- denormalized, plain UUID lưu bằng CHAR(36), tiện query
    signer_user_id   CHAR(36) NOT NULL,           -- JWT sub của INSPECTOR
    auth_time        TIMESTAMP NOT NULL,      -- step-up, inspectionAuthMaxAgeSeconds = 1800s (§2.4)
    signed_at        TIMESTAMP NOT NULL,
    report_hash      VARCHAR(64) NOT NULL,    -- vị trí tương ứng signedContentHash của nhánh buyer/seller
    ip_address       VARCHAR(45)
);

-- ContractTerms, thêm field mới (contract_db)
ALTER TABLE contract_terms ADD COLUMN level2_inspector_org VARCHAR(255) NULL;

-- inspection-service — bảng track việc commission Level 2 org, tách biệt khỏi report
CREATE TABLE level2_inspection_commission (
    commission_id         CHAR(36) PRIMARY KEY,
    contract_id           CHAR(36) NOT NULL,        -- FK thường, KHÔNG unique — 1 hợp đồng có thể
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
    report_id                        CHAR(36) PRIMARY KEY,
    contract_id                      CHAR(36) NULL,     -- NOT NULL cho LEVEL_1_5 và LEVEL_2 ADMIN_MANUAL;
                                                       -- NULL tạm thời hợp lệ cho LEVEL_2 AUTO_EMAIL khi
                                                       -- status = PENDING_REVIEW (chưa match được commission)
                                                       -- Denormalized, tiện query — KHÔNG dùng làm join key
                                                       -- thật cho LEVEL_2, xem commission_id bên dưới.
    tier                              VARCHAR(10) NOT NULL,    -- 'LEVEL_1_5' | 'LEVEL_2'
    content                           JSON NOT NULL,   -- MySQL 8 (sửa dialect 18/07/2026)
    milestone_id                      CHAR(36) NULL,   -- bắt buộc trước khi CONFIRMED
    report_file_hash                  VARCHAR(64) NOT NULL,
    normalized_result                 JSON NULL,       -- InspectionSettlementResultV1; bắt buộc khi CONFIRMED
    result_hash                       VARCHAR(64) NULL, -- SHA256 RFC8785 canonical normalized_result
    report_hash                       VARCHAR(64) NULL,        -- bắt buộc khi CONFIRMED; commit resultHash + identity/file/timestamp/actor
    -- nhánh LEVEL_1_5 — bắt buộc:
    inspector_id                     CHAR(36) NULL,               -- = signerUserId, NOT NULL nếu tier = LEVEL_1_5
    -- nhánh LEVEL_2 — bắt buộc:
    external_org                     VARCHAR(255) NULL,       -- NOT NULL nếu tier = LEVEL_2
    confirmed_by_admin_id            CHAR(36) NULL,               -- NOT NULL nếu tier = LEVEL_2 và status = CONFIRMED.
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
    commission_id                    CHAR(36) NULL REFERENCES level2_inspection_commission(commission_id),
                                                                  -- join key thật cho LEVEL_2 (cùng DB inspection-service,
                                                                  -- REFERENCES hợp lệ ở đây — khác trường hợp signature.report_id
                                                                  -- ở trên, vốn xuyên service)
    external_verification_status     VARCHAR(20) NULL,        -- 'NOT_CHECKED' | 'VERIFIED' | 'UNVERIFIED' | 'UNAVAILABLE' —
                                                                  -- optional, chỉ LEVEL_2, Admin tự điền nếu org có dịch vụ verify công khai
    external_verification_checked_at TIMESTAMP NULL,
    confirmed_at                     TIMESTAMP NULL,
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
- **Endpoint duyệt (`ReviewPendingExternalReport`) không nhận file, và `reportFileHash` đóng băng trước khi Admin chạm vào**; `reportHash` cuối commit chính file hash đó cùng normalized result và attribution lúc CONFIRMED — ngăn Admin thay file hoặc sửa result ngoài hash.
- **3-mail chỉ là passive backup**, không có active reconciliation tự động — không có bước nào chủ động so sánh bản buyer/seller nhận với bản platform xử lý, chỉ nằm đó dùng khi có tranh chấp thật cần lôi ra đối chiếu. Kế thừa đúng giới hạn "phải có người chủ động nhìn" từ `hash-chain-phase2-design.md` §6.
- **WebAuthn/passkey cho INSPECTOR** — cùng lý do đã ghi ở `signature-phase2-design.md` §8: không đổi tier pháp lý (vẫn thiếu chứng thư CA cấp phép, Điều 22 khoản 3(đ)), chỉ nâng chất lượng bằng chứng khi tranh chấp, không nâng presumption pháp lý mặc định. Để ngoài phạm vi, có chủ đích.

---

## 7. Status — Inspection Design

**Chốt (03/07/2026; hash contract superseded 18/07/2026):** Level 1.5 = actor thật, nhánh signature thuộc inspection-service, KYC nội dung khác Buyer/Seller, session freshness `inspectionAuthMaxAgeSeconds` = 1800s. Level 2 = không Signature, không RBAC; `level2InspectorOrg` NOT NULL có điều kiện trong `ContractTerms`, negotiate lúc ký, không hardcode danh sách tổ chức. Hash contract hiện hành là `InspectionSettlementResultV1` + `resultHash` + revised `reportHash` ở §2.3.

**Chốt bổ sung (04/07/2026) — merge addendum + fix kiến trúc:**
- **Level 2 ingestion chuyển từ thuần thủ công sang auto-intake + human-confirm:** hòm mail platform (`intake@...`, qua IMAP polling của file-service — sửa 17/07/2026, xem §3.3) thay thế vị trí Admin trong 3-mail song song gốc (buyer/seller không đổi, §3.3). Thêm use case `InitiateLevel2Inspection` ghi nhận platform đã yêu cầu commission org đi đâu, lúc nào (§3.4). Case ID của org dùng làm join key qua bảng `level2_inspection_commission`, match tự động chỉ ở mức gợi ý, Admin xác nhận thật (§3.5). Report cuối ingest tự động, hash đóng băng ngay khi nhận, publish audit chain dời sang lúc Admin `CONFIRMED` qua `ReviewPendingExternalReport` (§3.6). Giữ ingestion thủ công gốc làm fallback (`ingestion_source = ADMIN_MANUAL`).
- **`audit_record.source_type` có 3 giá trị mới:** `INSPECTION_REPORT`, `EXTERNAL_INSPECTION_REPORT`, `LEVEL2_INSPECTION_COMMISSIONED` — tách riêng vì sức nặng bằng chứng khác nhau (§4).
- **Fix cross-service FK bug (phát hiện lúc rà kiến trúc 04/07/2026):** `signature.report_id` **không** dùng `REFERENCES inspection_report(report_id)` nữa — vi phạm database-per-service, vì `signature` (contract_db) và `inspection_report` (inspection-service DB) là 2 database khác nhau. Giữ integrity ở application layer, cùng pattern các cross-service reference khác trong hệ thống (§5).
- **KI-2 đóng (§3.2):** allowlist 3 nhóm cho `level2InspectorOrg` — major quốc tế hardcode, trong nước verify qua BoA-VIAS, "lạ" thì Admin duyệt case-by-case và **không** lưu vào danh sách dùng chung (né bài toán dọn danh sách khi tổ chức phá sản/mất accreditation). Verify qua accreditation certificate number, tra đúng cơ quan công nhận quốc gia đã phát hành. **Nguồn tra cứu (thu hẹp 08/07/2026, L4):** BoA-VIAS (`boa.gov.vn`) + IAF CertSearch (`iafcertsearch.org`) + ILAC Signatory Search (`ilac.org/signatory-search`) — 3 nguồn online xác định, Admin tra tay; tự động hoá full qua API là enhancement, chưa xác nhận BoA có API JSON.

**Nguyên tắc xuyên suốt cho phần auto-intake:** tự động hoá phần lặp lại, nhàm chán, không cần phán đoán (nhận mail, tính hash, gợi ý match) — giữ nguyên quyết định cuối (cái biến 1 file thành bằng chứng) ở người, có RBAC, có audit trail, không thể lặng lẽ override. Mục tiêu không phải loại bỏ con người khỏi quyết định — mục tiêu là không để 1 người là điểm duy nhất quyết định cái gì là bằng chứng thật.

Inspection — **đóng session, sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.** File `inspection-phase2-design-addendum.md` đã merge vào đây — có thể xoá khỏi project knowledge.

---

*Cập nhật 19/07/2026 (ghi chú §3: BreachCase mới ở milestone-escrow không đảo quyết định UNIQUE(reportId); inspection không tham chiếu BreachCase) · Design session: 03/07/2026 · Addendum merge + auto-intake flow: 04/07/2026 · Fix cross-service FK: 04/07/2026 · 08/07/2026 (L4: thu hẹp tra cứu accreditation — thêm nguồn cụ thể IAF CertSearch + ILAC Signatory Search + BoA-VIAS, từ "deferred chưa biết tra đâu" → "3 nguồn online xác định", §3.2) · Cập nhật 13/07/2026 (làm rõ 2 giả định ngoài scope — BV document-verification + BoA API — thành Known Limitation §6) · Đồng bộ cross-service 17/07/2026 (IMAP/file.ready handshake §3.3-3.6; inspector_signature tách bảng §2.1/§5; 2 domain event vào chain §4) · Chưa code · **Sẵn sàng đưa vào Architecture/SDS/TechnicalSpec chính thức.***
