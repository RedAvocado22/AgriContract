---
title: "AGRICONTRACT"
subtitle: "Giải pháp và mô hình nghiệp vụ Phase 2"
author: "Tài liệu đồ án tốt nghiệp"
date: "Phiên bản final · Tháng 7/2026"
toc-title: "Mục lục"
---

# **Tóm tắt điều hành**

AgriContract là contract layer cho giao dịch nông sản B2B: hợp đồng điện tử, cọc và milestone escrow qua custody boundary, giao nhận/giám định, attribution/remedy, reputation và audit evidence. Thiết kế Phase 2 thay mô hình “huỷ một nút rồi phạt” bằng taxonomy chấm dứt và decision chain rõ ràng. Tiền và uy tín không phản ứng trực tiếp với allegation hoặc lifecycle event; chúng chỉ phản ứng với quyết định cuối cùng canonical.

Tài liệu này mô tả mô hình người dùng, golden flow, giá trị và giới hạn ở mức nghiệp vụ. API, event wire contract, schema, idempotency và migration chi tiết nằm trong Architecture/SDS. Mọi capability được mô tả là target design; tích hợp ngân hàng/giám định production và legal validation vẫn là giới hạn.

# **1. Định nghĩa sản phẩm**

AgriContract là nền tảng số hoá hợp đồng mua bán nông sản B2B với cơ chế tiền cọc và tiền phong toả do ngân hàng giữ. Hợp đồng không bị phủ nhận giá trị pháp lý chỉ vì được giao kết và lưu trữ bằng phương tiện điện tử; hiệu lực vẫn phụ thuộc các điều kiện chung của giao dịch, pháp luật về hợp đồng và quy định có liên quan.

**Phạm vi giới hạn có chủ đích ở tầng hợp đồng.** AgriContract không xử lý logistics, không phải sàn thương mại điện tử, không thay thế hệ thống kế toán doanh nghiệp. Phần mềm giải quyết một vấn đề cụ thể — thiếu cơ chế thực thi workflow theo điều kiện trong giao dịch forward contract nông sản B2B — và không có tham vọng giải quyết toàn bộ chuỗi cung ứng.

> **Căn cứ pháp lý — Luật GDĐT 2023, Điều 8, Điều 14 và Điều 34–36.** Thông tin không bị phủ nhận giá trị pháp lý chỉ vì ở dạng thông điệp dữ liệu; việc giao kết và thực hiện hợp đồng điện tử vẫn phải tuân thủ pháp luật về hợp đồng và quy định liên quan. Audit trail có thể được sử dụng làm chứng cứ, với giá trị phụ thuộc độ tin cậy của phương thức tạo lập, gửi, nhận, lưu trữ và bảo toàn tính toàn vẹn.

# **2. Năm tầng người dùng**

Các tầng stakeholder không đồng nhất với role runtime. Keycloak có năm role BUYER, SELLER, INSPECTOR, OPERATOR và ADMIN. OPERATOR xử lý công việc vận hành hằng ngày có thể đảo ngược như KYC, review, moderation và nhập giá thủ công; ADMIN giữ các quyết định rủi ro cao, security/audit và bước approve maker-checker. External Verifier và System là actor kỹ thuật, không phải role người dùng thông thường.

Nền tảng có năm nhóm người dùng với quan hệ pháp lý và quyền hạn hoàn toàn khác nhau. Nhầm lẫn giữa các tầng này dẫn đến hiểu sai về mô hình doanh thu, cơ chế trust và phân tích rủi ro pháp lý.

| **Tầng** | **Chủ thể** | **Vai trò** |
|---|---|---|
| Tầng 1 — Software Buyer | Hiệp hội ngành hàng (VICOFA, VRA, VINACAS) hoặc doanh nghiệp thu mua lớn (Intimex, Phúc Sinh Group, XNK 2/9 Đắk Lắk) | Trả phí license/subscription — nguồn doanh thu của nền tảng. Triển khai cho cộng đồng thành viên; chỉ định OPERATOR vận hành hằng ngày và ADMIN kiểm soát hành động rủi ro cao |
| Tầng 2 — Platform Buyer | Doanh nghiệp thu mua, tập đoàn xuất khẩu nông sản (nhiều trường hợp trùng Tầng 1) | Khởi tạo offer và đàm phán điều khoản; khoá tiền ký quỹ trước khi bên bán giao hàng; xác nhận nhận hàng để kích hoạt giải ngân |
| Tầng 3 — Platform Seller | Hợp tác xã nông sản, nông hộ liên kết, doanh nghiệp cung ứng nguyên liệu | Đăng listing sau khi được xác minh; đàm phán và ký hợp đồng điện tử; giao hàng và nhận thanh toán. Nhóm được cơ chế ký quỹ bảo vệ trực tiếp nhất |
| Tầng 4 — INSPECTOR | Tổ chức giám định được Nhà nước công nhận: Vinacontrol, Quatest, SGS, Bureau Veritas, Intertek | Nhân chứng chuyên môn độc lập, không phải người phán xử. Xác định số lượng/chất lượng tại điểm giao nhận; nộp inspection report có hash xác thực, không sửa được sau khi submit |
| Tầng 5 — Escrow Holder | Ngân hàng thương mại được NHNN cấp phép (Agribank/BIDV) | Không phải người dùng nền tảng. Giữ tiền thật; nền tảng chỉ gửi lệnh khoá/giải ngân/phạt. Cấu trúc này loại bỏ rủi ro vi phạm quy định trung gian thanh toán |

**Vì sao bán cho hiệp hội, không bán trực tiếp cho HTX.** HTX nhỏ không mua phần mềm. Hiệp hội hoặc doanh nghiệp thu mua lớn triển khai nền tảng, thành viên của họ sử dụng. Cách này cũng giải quyết bài toán niềm tin: HTX không cần tin một startup mới — họ tin VICOFA/VRA đã triển khai nền tảng. Khi ngân hàng giữ tiền, niềm tin hoàn toàn độc lập với uy tín thương hiệu của nền tảng.

### **Xử lý xung đột lợi ích khi Tầng 1 và Tầng 2 trùng nhau**

Khi doanh nghiệp thu mua vừa mua license vừa là bên mua hàng trên nền tảng, đội vận hành do họ chỉ định có xung đột lợi ích trong vai trò xử lý tranh chấp. Hai cơ chế kiểm soát:

1.  Tranh chấp giá trị lớn hoặc hàng hoá phức tạp bắt buộc kích hoạt INSPECTOR độc lập — OPERATOR/ADMIN được ủy quyền chỉ thực thi kết quả giám định, không được ra phán quyết độc lập.


2.  Mọi quyết định vận hành của OPERATOR/ADMIN được ghi vào audit trail không thể xoá/sửa sau khi submit; hành động override bị đánh dấu vĩnh viễn.

Khuyến nghị vận hành: ưu tiên triển khai qua hiệp hội ngành hàng — bên trung lập, không có lợi ích đối lập với Buyer hay Seller trong từng giao dịch — để loại bỏ xung đột lợi ích ngay từ mô hình phân phối.

# **3. Luồng giao dịch Phase 2**

## **3.1 Golden flow từ listing đến quyết toán**

1. Seller đã đủ điều kiện tạo listing; product-service đóng băng snapshot sản phẩm/plot theo commodity.
2. Buyer tạo hợp đồng `OFFERED`; hai bên chỉ sửa `ContractTerms` trước chữ ký thứ hai.
3. Mỗi chữ ký dùng OTP challenge binding theo user–contract–terms. Hai chữ ký phải có cùng `signedContentHash`; sau đó hợp đồng thành `SIGNED` và terms bất biến.
4. escrow-service yêu cầu bank-service lock buyer deposit và seller deposit. Chỉ khi cả hai leg thành công hợp đồng mới `ACTIVE`. Lock một phần rồi thất bại phải refund leg đã lock trước khi `ACTIVATION_FAILED`.
5. Mỗi milestone có funding riêng: `FUNDING_PENDING → LOCKED` hoặc `FUNDING_FAILED`. Seller obligation clock chỉ chạy sau `LOCKED`; lỗi kỹ thuật/ngân hàng đã xác nhận không bị suy thành seller breach.
6. Seller cân và khai giao; buyer xác nhận sạch hoặc flag. Nếu có inspection đang pending, timer không được auto-confirm.
7. Phần đã giao có thể quyết toán pro-rata. Delta 1 vượt ngưỡng chỉ mở quy trình attribution, không tự động gán vi phạm.
8. Mọi hậu quả tiền/uy tín cuối cùng hội tụ tại `AttributionDecision → RemedyDecision → remedy.finalized`.
9. Normal settlement cũng dùng decision no-fault để trả buyer deposit và seller deposit. `SETTLED` chỉ được commit khi bank ledger đủ expected `remedyLegId` và tổng lock contract bằng 0.

## **3.2 State và hành vi chấm dứt**

Contract state chuẩn: `OFFERED`, `NEGOTIATING`, `SIGNED`, `ACTIVE`, `REPLACEMENT_PENDING`, `SUPERSEDE_REFUND_PENDING`, `ACTIVATION_REFUND_PENDING`, `SETTLED`, `TERMINATED`, `WITHDRAWN`, `SUPERSEDED`, `ACTIVATION_FAILED`.

| **Hành vi** | **Ý nghĩa** | **Attribution/hậu quả** |
|---|---|---|
| `WITHDRAW_OFFER` | Rút đề nghị trước khi đủ hai chữ ký | Không remedy leg; `WITHDRAWN` zero-lock by construction |
| `MUTUAL_TERMINATION` | Hai bên đồng thuận kết thúc toàn bộ phạm vi còn lại | `finalBreachingRole = null`; hoàn trả/giải phóng đúng chủ; không penalty/reputation lock |
| `MUTUAL_REPLACEMENT` | Tạo hợp đồng mới thay thế hợp đồng cũ | Hợp đồng mới phải ký trước; hợp đồng cũ chỉ `SUPERSEDED` sau refund hết lock; không rollback lịch sử cũ nếu activation mới fail |
| `TERMINATION_FOR_BREACH` | Chấm dứt do vi phạm đã được attribution | Hậu quả theo `finalBreachingRole`, `LegalProfile` và phần nghĩa vụ còn lại; milestone đã `SETTLED` không bị truy thu |
| `TERMINATION_FOR_FORCE_MAJEURE` | Kết thúc no-fault sau review bất khả kháng | Không penalty/reputation; tiền về đúng chủ |
| `ACTIVATION_FAILURE` | Không thể hoàn tất lock cọc sau retry/cure | Refund leg đã lock; chỉ terminal khi zero-lock |

## **3.3 Attribution: không đồng nhất người yêu cầu với bên vi phạm**

- `requestedBy`: ai khởi tạo yêu cầu.
- `allegedBreachingRole`: bên đang bị cáo buộc, chưa phải kết luận.
- `finalBreachingRole`: kết luận cuối cùng; chỉ field này được dùng để quyết định sanction.

Rổ A dùng bằng chứng khách quan và tạo `AttributionDecision(decisionSource = SYSTEM, breachCaseId = null)`. Rổ B tạo `BreachCase` với state `REPORTED → UNDER_REVIEW → RESOLVED`. Allegation chưa final không được chạm tiền hoặc tạo negative reputation. Kết quả no-fault có `finalBreachingRole = null` và `penaltyEligible = reputationEligible = false`.

## **3.4 Remedy và bảo toàn tiền**

`RemedyDecision` chứa nhiều `remedyLegs`; mỗi leg có `remedyLegId` duy nhất. Các loại hậu quả tách bạch:

- `PAYMENT_SETTLEMENT` / `PAYMENT_REFUND` cho tiền milestone và hoàn tiền bình thường;
- `DEPOSIT_FORFEITURE` cho hậu quả cọc nếu điều khoản/pháp luật cho phép;
- `CONTRACTUAL_PENALTY` theo mức trần của `LegalProfile` và phần nghĩa vụ bị vi phạm;
- `DAMAGES_COMPENSATION` chỉ khi `damagesPolicy` và bằng chứng cho phép.

Không dùng một khoản “phạt” gộp. Không double recovery; forfeiture có thể phải offset theo policy. `requestedBy` không được dùng để chọn bên bị seize. Một `remedyDecisionId` nhóm nhiều leg nên không được unique ở bank ledger; dedup tiền ở cấp `remedyLegId`.

## **3.5 Milestone, Delta 1/Delta 2 và Level 2 provisional**

Milestone delivery state: `CREATED`, `IN_PROGRESS`, `SELLER_WEIGHED`, `BUYER_RECEIVED`, `AWAITING_SELLER_RESPONSE`, `CONTESTED`, `FORCE_MAJEURE_PENDING_REVIEW`, `SETTLED`. Seller-only evidence không tự settle. Buyer non-response chỉ auto-confirm khi milestone vẫn ở `BUYER_RECEIVED` và không có inspection pending.

Delta 1 (quantity) cho phép quyết toán phần đã giao và mở attribution cho phần thiếu nếu vượt threshold. Delta 2 (quality) đi theo flag/response/inspection. Level 2 provisional phải dùng release floor và các leg explicit sao cho `release + refund + remaining lock = batchAmount`; zero-amount command bị omit.

## **3.6 Vì sao seller không bị khoá vốn lưu động toàn hợp đồng**

Tiền hàng được lock và giải phóng theo từng milestone, không khoá toàn bộ giá trị từ ngày ký. Seller deposit là tham số contract-level riêng, còn buyer funding của batch chỉ được coi là sẵn sàng khi bank xác nhận `LOCKED`. Thiết kế vẫn cần pilot để kiểm chứng mức cọc/cửa sổ funding phù hợp theo từng ngành.

# **4. Giám định độc lập — ranh giới Level 1, 1.5 và 2**

- **Level 1:** đối chiếu và phản hồi giữa buyer/seller trong workflow milestone.
- **Level 1.5:** inspector tích hợp nền tảng, có KYC/session, nhưng `inspection result signature/hash` là commitment riêng, không dùng nhầm chữ ký hợp đồng. Kết quả normalized và report identity được hash trước khi phát event.
- **Level 2:** tổ chức bên ngoài được hai bên chọn và đóng băng trong `ContractTerms`; không giả định có RBAC/chữ ký trong hệ thống. Platform phát commission kèm case ID, nhận report qua mailbox/entrypoint riêng, virus scan và human-confirm; report hash được đóng băng trước thao tác review.

Tổ chức Level 2 phải phù hợp allowlist/accreditation rule theo hợp đồng. Các tích hợp SGS/Bureau Veritas hoặc tổ chức trong nước là interface/mô phỏng trong đồ án, không được trình bày như quan hệ production đã ký.

# **5. Uy tín và cơ chế khoá tài khoản**

Reputation không phạt theo allegation, `contract.terminated`, hành vi bấm nút hay event milestone quan sát. **Input tiêu cực duy nhất** là `remedy.finalized` với `reputationEligible = true` và attribution đã final. User bị ảnh hưởng được xác định trực tiếp từ `buyerId`/`sellerId` cùng `finalBreachingRole` trong payload.

`lock_entry` là insert-only, unique theo `remedy_decision_id`; một decision tạo tối đa một lock. Mở khoá sớm dùng override append-only và maker-checker, không UPDATE record lịch sử. user-service nhận projection với `lockRevision` tăng đơn điệu và lưu `last_lock_revision` để không bị event cũ ghi đè sau restart. Completion/dispute facts được lưu bất biến; score được tính khi query, không persist như chân lý cố định.

Tín hiệu `ELEVATED_RISK`/AML chỉ là projection hỗ trợ review ở Phase 2, không tự chặn settlement hoặc thay thế quyết định của tổ chức tín dụng.

# **6. Bằng chứng, audit và notification**

Audit-service tạo `audit_record` append-only với dual chain global/per-subject. Hash commitment bao gồm cả envelope identity (`sourceEventId`, correlation/causation) và toàn bộ field canonical; sửa metadata hoặc payload đều phải bị phát hiện. `audit_record` và `anchor_request_outbox` được ghi cùng transaction; replay cùng source event không tạo record, anchor hoặc evidence email thứ hai.

Bằng chứng có thể được đối chiếu ở nhiều vị trí: chain trong database, digest/anchor và OpenTimestamps theo thiết kế. Đây là cơ chế phát hiện sửa đổi, không chứng minh nội dung khai báo là đúng hay giao dịch hợp pháp.

Notification-service nhận **notification command riêng**, không được dùng business event như một trigger hậu quả. RabbitMQ là đường mặc định; internal synchronous API chỉ dùng cho OTP để bảo đảm provider fail thì không có mail gửi muộn ngoài kiểm soát. Dedup theo `(event_id, recipient_email, notification_type)`.

# **7. Khung pháp lý và quản trị rủi ro**

## **7.1 LegalProfile đóng băng cùng hợp đồng**

`LegalProfile` gồm `governingLaw`, `contractType`, `maxContractualPenaltyRate` và `damagesPolicy`. Với `VN_COMMERCIAL_LAW`, thiết kế áp trần contractual penalty 8% cho phần nghĩa vụ bị vi phạm. Ba policy bồi thường được tách: `COMMERCIAL_CUMULATIVE_IF_PROVEN`, `CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE`, `EXPRESS_PENALTY_ONLY`.

Phân loại deposit theo BLDS Điều 328, tiền phong toả/ký quỹ tại tổ chức tín dụng theo Điều 330 và quan hệ penalty–damages phải được legal validation cho mẫu hợp đồng pilot. Hệ thống không tự suy “cọc = phạt” và không coi mọi chấm dứt là breach.

## **7.2 Governance và trust boundary**

Role Phase 2: `BUYER`, `SELLER`, `ADMIN`, `INSPECTOR`, `OPERATOR`. Maker-checker chỉ áp ở hai điểm high-risk đã thiết kế; người đề xuất không tự approve. Gateway xác thực coarse-grained và strip identity header; owner service chịu ownership/fine-grained authorization. `/internal/**` không có route external và các use case ký/listing/offer fail-closed khi user-service unavailable.

External Verifier dùng ES256, canonical JSON, nonce persist và cửa sổ timestamp; lock/unlock đi trực tiếp bank-service, không có Admin bypass hay gateway retry. Đây là **zero-trust-oriented control tại một boundary**, không phải kiến trúc full Zero Trust. Nếu verifier key holder và các operator liên quan thông đồng, rủi ro vẫn tồn tại.

# **8. Ba lớp giá trị**

| **Lớp** | **Giá trị gần hạn** | **Điều cần validation** |
|---|---|---|
| Giao dịch | Điều khoản rõ, milestone funding, đối chiếu giao nhận, hoàn/giải phóng tiền đúng điều kiện | Mức cọc, cửa sổ thời gian, phí và trải nghiệm hai bên |
| Tin cậy | Attribution tách allegation/final decision; audit trail; reputation dựa trên fact cuối | Tỷ lệ case Rổ A/Rổ B, thời gian review, mức chấp nhận dữ liệu uy tín |
| Dữ liệu | Read model về termination, milestone, FM, cấu trúc giao dịch và EUDR evidence | Chất lượng dữ liệu, quyền sử dụng, utility cho ngân hàng/hiệp hội |

ROI và willingness-to-pay chưa được chứng minh bằng dữ liệu sơ cấp; phải đo trong pilot thay vì suy từ tổng kim ngạch ngành.

# **9. Điểm khác biệt của mô hình**

AgriContract không cạnh tranh bằng việc trở thành nhà phân phối hay blockchain công khai. Điểm khác biệt là một contract layer bản địa hoá: (1) legal profile/terms bất biến sau ký; (2) custody boundary tách khỏi nghiệp vụ; (3) milestone và attribution/remedy có invariant bảo toàn tiền; (4) giám định nhiều cấp; (5) audit/notification/data governance tích hợp. So sánh với giải pháp quốc tế chỉ là bằng chứng khái niệm; mức phù hợp tại Việt Nam vẫn cần discovery.

# **10. Chiến lược vào thị trường — anchor buyer pilot**

Pilot nên bắt đầu từ một anchor buyer có mạng lưới HTX/supplier sẵn có, một hoặc hai commodity và contract template được luật sư xác nhận. Phạm vi pilot cần giới hạn số nhánh termination/remedy, ngân hàng/custodian sandbox, tổ chức giám định và KPI release/reconciliation. Không launch hai phía rộng khi chưa đo funnel mời–KYC–listing–ký–funding–settlement.

# **11. Câu hỏi phản biện trọng yếu**

**Hệ thống có tự phán xử không?** Không. Rổ A chỉ tự attribution khi evidence khách quan đáp ứng guard đã ký; Rổ B cần review. Hệ thống tạo workflow và bằng chứng, không thay toà án/trọng tài.

**Ai giữ tiền?** Trong sản phẩm thật phải là custodian/tổ chức tín dụng hợp pháp. bank-service Phase 2 là ledger/adapter mô phỏng interface, không phải tuyên bố AgriContract được phép giữ tiền.

**Người yêu cầu chấm dứt có bị phạt không?** Không mặc định. `requestedBy` và `finalBreachingRole` là hai field khác nhau; sanction chỉ dựa trên quyết định cuối.

**Có blockchain/Zero Trust không?** Không claim blockchain hay full Zero Trust. Thiết kế dùng hash chain/anchor và một số control zero-trust-oriented phù hợp trusted-operator model.

**Tại sao event-driven nhưng không double money?** Transactional outbox + source event id + `remedyLegId` unique + consumer idempotency; terminal lifecycle còn có reconciliation guard và zero-lock invariant.

# **12. Giới hạn và phạm vi ngoài**

- 12 business services, API/event/schema và Verification Matrix là thiết kế implementation-ready, chưa phải code/production đã chứng minh.
- Không tích hợp ngân hàng thật, CA chữ ký số, tổ chức inspection production, đối tác tín dụng hoặc cold rebuild analytics trong Phase 2 hiện tại.
- Không có full amendment; thay đổi sau ký dùng mutual replacement saga. Mutual termination chỉ cho toàn bộ remaining scope.
- Không tự xử lý logistics, kế toán, bảo hiểm, due-diligence statement EUDR hay phán quyết pháp lý.
- Baseline rừng, plot overlap và yield anomaly là signal/review support; không auto kết luận gian lận.
- Pricing scrape/manual có rủi ro độ trễ/nguồn; không phải indexed settlement engine đã hoàn chỉnh.
- External verifier emergency lock là control hẹp; không xoá rủi ro thông đồng và không thay DR/segregation of duties.
- Data retention/cross-border/PII, mẫu contract và legal classification phải được validation trước deployment thật.

# **13. Danh mục nguồn tham khảo**

### **Văn bản pháp luật**

- Luật Giao dịch Điện tử 2023 (Luật 20/2023/QH15, hiệu lực 1/7/2024).

- Nghị định 52/2024/NĐ-CP — Thanh toán không dùng tiền mặt; Nghị định 52/2013/NĐ-CP — Thương mại điện tử.

- Nghị định 98/2018/NĐ-CP — Liên kết sản xuất và tiêu thụ nông sản.

- Luật Trọng tài Thương mại 2010 (Luật 54/2010/QH12).

- Bộ luật Dân sự 2015 — Điều 328, 330, 142, 403, 156, 351.

- Luật Thương mại 2005 — Điều 300, 302.

- Nghị định 88/2019/NĐ-CP — Xử phạt vi phạm hành chính lĩnh vực tiền tệ.

### **Án lệ & tổ chức pháp lý**

- Bản án 15/2021/KDTM-ST — Toà án quận Nam Từ Liêm, Hà Nội.

- VIAC — Thống kê tranh chấp 2024 (475 vụ, kỷ lục).

### **Thị trường & nghiên cứu**

- VTV.vn — Giá cà phê tăng, làn sóng phá vỡ hợp đồng (17/4/2024).

- Báo Pháp Luật TP.HCM — Nghịch lý xuất khẩu cà phê Việt (4/4/2026).

- Vietnam.vn — Banks partner with the digital agricultural supply chain (1/4/2026).

- Agribank — Chương trình tín dụng chuỗi giá trị nông nghiệp 2024.

- VCCI — Báo cáo Kinh tế tư nhân 2025 (75,5% doanh nghiệp cần tài sản thế chấp để tiếp cận tín dụng).

- EC Regulation (EU) 2025/2650 — EUDR timeline (23/12/2025).

- European Commission — EUDR Information System; Understand Due Diligence (truy cập 7/2026).

- Alemu, D., Guinan, A. & Hermanson, J. (2021), DOI 10.1080/09614524.2020.1860194.

- Macchiavello, R. (2022), DOI 10.1146/annurev-economics-051420-110722.

- Ewusi Koomson, J. et al. (2022), DOI 10.1080/14728028.2022.2079007.

- Tefera, D. A. & Bijman, J. (2021), DOI 10.1186/s40100-021-00198-0.

- Abreham, G. et al. (2025), DOI 10.1080/23311932.2025.2551263.


