---
name: data-governance-phase2-design
description: "Data Governance Phase 2 — ownership/system-of-record matrix, classification, role model thêm OPERATOR + maker-checker, immutability vs quyền xoá, AML tipping-off, retention và map ND13/2023."
status: DESIGNED — chưa code.
metadata:
  type: design
  phase: 2
  extends: "AgriContract Architecture v2; SDS security section"
  related: "user-service-phase2-design.md §2/§3; api-gateway-phase2-design.md §3.3; reputation-service-phase2-design.md §7/§8; hash-chain-phase2-design.md §2-3; bank-service-phase2-design.md §3.4b; file-service-phase2-design.md §7"
---

## 1. Bối cảnh & Scope

AgriContract xử lý nhiều nhóm dữ liệu nhạy cảm: hợp đồng + chữ ký điện tử, hồ sơ KYC/giấy uỷ quyền, số dư và giao dịch, report giám định, dữ liệu vùng trồng/EUDR, điểm uy tín và dữ liệu export cho bên thứ ba. Các quyết định governance thực ra đã tồn tại rải rác trong 13 design docs (database-per-service, không FK xuyên DB, append-only ledger/chain, tách public/internal DTO, không log OTP/PII, retention EUDR 5 năm, orphan file 1 tuần) — doc này **gom chúng thành 1 framework thống nhất**, bổ sung phần chưa có owner (role model vận hành, quyền xoá vs immutability, AML tipping-off, retention matrix, ND13), và là source of truth cho mọi câu hỏi "ai được làm gì với dữ liệu nào, giữ bao lâu".

**Không nằm trong scope (có chủ đích, tránh over-engineer):** không có `data-governance-service`, không data catalog/MDM platform, không hội đồng governance giả lập, không workflow phê duyệt nhiều tầng cho mọi field. Framing: *AgriContract không xây nền tảng Data Governance độc lập; hệ thống áp dụng một governance framework nhẹ, xác định rõ data ownership, system of record, quyền truy cập, tính bất biến và vòng đời dữ liệu cho từng data domain.*

## 2. Nguyên tắc

1. Mỗi data domain có đúng **1 system of record** — service khác chỉ giữ bản sao/projection, bản sao không bao giờ là nguồn chính thức và không được sửa để "chữa" nguồn.
2. **Least privilege theo use case**, không theo chức danh: quyền cấp cho hành động cụ thể, service sở hữu use case enforce (Gateway chỉ gate coarse-grained — nhất quán api-gateway §4).
3. **Dữ liệu bất biến không ai sửa được** — kể cả ADMIN: ledger, `audit_record`, hợp đồng đã `SIGNED`, `Signature`, snapshot đã anchor. Sai thì ghi bản ghi điều chỉnh mới có attribution, không UPDATE bản cũ (pattern reversal entry của bank-service).
4. Mọi hành động nhạy cảm (ghi *và đọc* dữ liệu Restricted) đều được **attribute + audit**.
5. Hành động rủi ro cao nhất dùng **maker-checker** (2 người, 2 role) — kéo dài triết lý "không gatekeeper đơn lẻ" đã có ở emergency unlock/verify-failure xuống tầng vận hành.
6. Retention theo mục đích nghiệp vụ/pháp lý có căn cứ, hết retention thì xoá hoặc ẩn danh hoá — "giữ mãi mãi" phải là quyết định tường minh, không phải mặc định do quên.
7. **Quyền hạ tầng ≠ business authority (Technical Custodian):** developer/DevOps/DBA vận hành database, storage, backup, secrets, monitoring — nhưng cầm root DB **không** cho quyền sửa hợp đồng, ledger hay lịch sử duyệt; sửa tay ngoài application layer là vi phạm governance, phát hiện bằng chain verify (đây chính là threat model hash chain được thiết kế để bắt — hash-chain §1).
8. **Trách nhiệm dữ liệu thuộc người tạo ra nó:** Buyer/Seller chịu trách nhiệm tính đúng của thông tin họ khai (tọa độ plot, hồ sơ pháp nhân, khối lượng khai báo); tổ chức giám định chịu trách nhiệm nội dung report họ phát hành; ngân hàng chịu trách nhiệm số dư thật. Platform chịu trách nhiệm **quy trình** — validate, moderate, geo-eval, audit, bảo toàn bằng chứng — không đảm bảo tính đúng tuyệt đối của dữ liệu ngoài thực địa. ("Ai chịu trách nhiệm khi tọa độ sai?" → Seller khai sai chịu trách nhiệm khai báo; platform chịu trách nhiệm đã chạy đúng validation/moderation và giữ được bằng chứng ai khai, khi nào.)

**Threat model đính kèm nguyên tắc 7 — malicious/compromised infrastructure administrator (mới, 18/07/2026):** người kiểm soát hạ tầng không cần sửa DB tay — có thể thay image đang deploy, sửa env/secret mount, patch logic verify thành `return true`, hoặc chạy bản app bỏ qua check. KMS/HSM giữ khoá không bị trích xuất nhưng không ngăn app độc hại được cấp quyền gọi ký sai; multi-signature chỉ có nghĩa khi các signer thuộc trust domain độc lập. Mitigation Phase 2 (đều đã có chỗ đứng trong design): two-person rule `proposedBy != approvedBy`; secret không bake trong image (env/secret store); production image pin theo digest; protected branch + CI approval; emergency action có nonce/expiry/action-type/target (bank §3.5); mọi verify fail/success đều vào audit; application DB account không có UPDATE/DELETE trên ledger/`audit_record`/`lock_entry`; External Verifier + OTS anchor là trust domain nằm NGOÀI hạ tầng platform — lớp phát hiện cuối cùng mà infra admin không với tới. **Limitation ghi thẳng:** prototype không bảo vệ hoàn toàn trước kẻ kiểm soát đồng thời CI/CD + runtime + database + key policy; production cần KMS/HSM độc lập, separation of duties hạ tầng và deployment attestation. Không claim "zero-trust tuyệt đối".

## 3. Data Ownership Matrix

Nguyên tắc map actor: platform vận hành thật chỉ có **ADMIN, OPERATOR (mới — §5), INSPECTOR, External Verifier, System**. Các chức danh tổ chức (Compliance Officer, Risk Owner, Finance, Platform Owner...) là **logical responsibilities trong mô hình vận hành mục tiêu**, không phải account role Keycloak: một người hoặc một tài khoản ADMIN có thể kiêm nhiều trách nhiệm trong phạm vi đồ án; khi triển khai thực tế, các trách nhiệm này cần được phân tách theo nguyên tắc separation of duties. **Contingency cuối kỳ (18/07/2026):** nếu thiếu thời gian implement OPERATOR, được phép giữ 1 role ADMIN + ghi rõ "prototype simplification, production cần tách permission" — đổi được vì enforcement thiết kế theo use case, không theo role label.

| Data domain | System of record | Update authority (Phase 2) | Bản sao/consumer được phép | Bản sao dùng để |
|---|---|---|---|---|
| User profile / KYC / thẩm quyền | user-service | OPERATOR duyệt/reject; user tự sửa profile của mình | Feign `InternalUserInfo` cho contract/product/escrow | Gate `sign()`/listing; **không** trả PII ra public DTO (KI-3) |
| Hợp đồng, milestone, Signature | contract-service | Chỉ state machine; đã `SIGNED` → bất biến, không role nào sửa terms | analytics (`dim_contract`), audit (hash), escrow (số cọc qua event) | Báo cáo / verify / khoá tiền — không sửa ngược trạng thái |
| Tiền / ledger / escrow | bank-service (ledger là nguồn thật duy nhất) | **Không sửa thủ công** — chỉ command events; sai → reversal entry | escrow-service (projection trạng thái, không giữ con số làm nguồn) | Điều phối luồng; đối soát luôn theo ledger |
| Inspection report + inspector_signature | inspection-service | INSPECTOR submit; OPERATOR review PENDING_REVIEW; `reportHash` bất biến từ ingest | audit (hash qua `inspection.report_confirmed`) | Nối chain |
| Plot / geo / EUDR nguồn | product-service | SELLER khai; OPERATOR moderate; geo-eval System | audit-service (EUDR report generate + lưu 5 năm — **report thuộc audit**) | Bằng chứng compliance |
| Reputation / lock / AML risk | reputation-service | System-generated theo công thức; **2 ngoại lệ thủ công đi maker-checker §5.3**: unlock sớm, clear `ELEVATED_RISK` | user-service (`lockedUntil` projection), analytics | Enforce nhanh; nguồn quyết định vẫn ở reputation |
| Audit chain / OTS proof | audit-service (writer duy nhất — hash-chain §2.4) | **Không ai** — append-only, verify jobs là read | notification (đính proof), External Verifier (đối soát hash) | Bằng chứng độc lập |
| Notification log | notification-service | System; template version trong code, Admin không sửa runtime | — | Bằng chứng "đã gửi gì, bản nào" |
| Giá tham chiếu | pricing-service | System scrape; OPERATOR nhập manual có attribution | contract (chốt giá), analytics | Tham chiếu — hợp đồng lưu snapshot giá riêng |
| Analytics warehouse | analytics-service | Không ai sửa tay — **catch-up từ queue backlog sau outage ngắn; cold rebuild toàn lịch sử CHƯA hỗ trợ** (sửa 18/07/2026 — RabbitMQ không phải event archive vô hạn, analytics tự ghi rõ không có backfill) | — | Read model + derived-signal producer (AML): không sửa ngược domain, không trên critical path |

## 4. Data Classification

| Mức | Định nghĩa | Ví dụ trong hệ | Quy tắc |
|---|---|---|---|
| **Public** | Ai cũng xem được, không cần auth | Giá tham chiếu, listing public, catalog | Không chứa PII/contact |
| **Internal** | User đã đăng nhập / nội bộ platform | Public-summary reputation (authenticated từ 17/07), analytics tổng hợp, `PublicUserResponse` | Không email/phone/address |
| **Confidential** | Đúng participant + role vận hành theo case | Hợp đồng + terms, offer, milestone evidence, profile đầy đủ | Ownership check ở service; đọc bởi OPERATOR/ADMIN phải theo case đang xử lý |
| **Restricted** | Truy cập tối thiểu, mọi lần **đọc** đều log | Hồ sơ KYC + giấy tờ, số dư/ledger, OTP artifacts, `SuspiciousTransactionReport`, `ELEVATED_RISK`, private key material | §7 tipping-off; §8 read-audit; không bao giờ ra khỏi service qua public DTO |

## 5. Role Model — thêm `OPERATOR` (Phase 2, làm thật)

### 5.1 Vấn đề

Hiện ADMIN ôm toàn bộ vận hành: KYC, review report, moderate, giá manual, unlock, AML, security. Hệ quả: (1) blast radius 1 account ADMIN bị compromise = toàn hệ thống; (2) mọi ADMIN thấy mọi thứ; (3) cost mua chuộc = mua đúng 1 người. Attribution thì hệ đã có sẵn (`verifiedByActorId` — tên mới 18/07, các trường proposedBy/approvedBy... đều vào chain) — cái thiếu là **least privilege + separation of duties**.

### 5.2 Đường cắt: theo độ đảo ngược + độ dính tiền/security, không cắt theo service

- **OPERATOR** — nghiệp vụ hằng ngày, đảo ngược được, có hàng đợi: duyệt/reject KYC; review external report `PENDING_REVIEW`; moderate category/listing/product; nhập giá manual; xử lý Level 2 commission (case ID, gán report). Sai sửa được, mọi quyết định vẫn attributed như cũ.
- **ADMIN** — không đảo ngược, dính tiền hoặc security: approve maker-checker (§5.3), `RetryDepositLock`, `MarkActivationFailed`, export bằng chứng `/admin/audit/**`, đọc `/admin/security/**`, quản lý role/account (Keycloak), cấu hình hệ thống.
- **Không role nào** sửa ledger, hợp đồng đã ký, `audit_record`, `Signature`, snapshot đã anchor.
- INSPECTOR và External Verifier **giữ nguyên** như design hiện tại — không đụng.

ADMIN được làm mọi việc của OPERATOR (fallback vận hành team nhỏ), nhưng **không được tự approve đề xuất do chính mình tạo** ở luồng maker-checker.

### 5.3 Maker-checker — đúng 2 điểm, không rải

Chỉ áp cho 2 hành động chí mạng của reputation (nơi duy nhất "system-generated" bị người can thiệp):

```text
OPERATOR propose (reason bắt buộc) → trạng thái PROPOSED
        → ADMIN approve → thực thi + publish event như hiện tại
        → ADMIN reject  → đóng đề xuất, ghi lý do
Cả propose lẫn approve/reject đều vào audit chain (payload event mang cả 2 ID).
```

1. **Unlock sớm** (`UnlockEarlyUseCase` — reputation §7): propose/approve thay vì Admin đơn phương; payload `reputation.unlocked` thêm `proposedByOperatorId` + `approvedByAdminId`.
2. **Clear `ELEVATED_RISK`** (reputation §8 mục 6): tương tự; `reputation.elevated_risk_cleared` mang cả 2 ID — khớp luôn yêu cầu "defensible closure" chuẩn AML.

Muốn gian phải mua **hai người khác nhau đúng cặp**, cả 2 để vết trên chain — đây mới là thứ tăng cost mua chuộc; role label một mình không tăng. **Chốt phương án (18/07/2026 — bỏ mơ hồ "2 người, 2 role"):** đây là **permission-based two-person rule**, KHÔNG phải strict role separation — ai có quyền PROPOSE (OPERATOR hoặc ADMIN) đều propose được, approve luôn cần ADMIN, ràng buộc cứng duy nhất là `proposedBy != approvedBy` (enforce ở service + persist trong `governance_action_request`, reputation §7). Team nhỏ vận hành được (2 ADMIN vẫn chạy maker-checker với nhau), separation vẫn giữ vì không ai tự duyệt việc mình đề xuất. KYC thường **không** maker-checker (1 OPERATOR đủ — bắt 2 chữ ký mọi hồ sơ là chết vận hành team 4-5 người); nâng lên maker-checker cho KYC tổ chức giá trị lớn là enhancement, ghi Known Limitation.

### 5.4 Permission matrix (theo use case thật)

| Use case | Buyer/Seller | OPERATOR | ADMIN |
|---|---|---|---|
| Xem dữ liệu của chính mình (`/me`, contracts, escrow...) | ✅ | — | — |
| Export statement bút toán của chính mình (bank §5b.2) | ✅ | ❌ | ❌ (không cần — đọc ledger trực tiếp theo case qua read-audit) |
| Duyệt/reject KYC (`/admin/users`) | ❌ | ✅ | ✅ (fallback) |
| Review report `PENDING_REVIEW`, Level 2 commission | ❌ | ✅ | ✅ (fallback) |
| Moderate category/product/listing; giá manual | ❌ | ✅ | ✅ (fallback) |
| Unlock sớm / clear `ELEVATED_RISK` | ❌ | Propose | Approve/Reject |
| `RetryDepositLock` / `MarkActivationFailed` | ❌ | ❌ | ✅ |
| Export bằng chứng (`/admin/audit/**`), đọc `/admin/security/**` | ❌ | ❌ | ✅ |
| Quản lý role/account (Keycloak) | ❌ | ❌ | ✅ |
| Sửa ledger / hợp đồng đã ký / `audit_record` / `Signature` | ❌ | ❌ | ❌ |
| Emergency lock/unlock hệ thống | ❌ | ❌ | ❌ (chỉ External Verifier — bank §3.5) |

### 5.5 Delta kỹ thuật (cost thật nằm ở đây)

1. Keycloak: thêm realm role `OPERATOR`.
2. user-service: enum role + migration (`user-service-phase2-design.md` §2/§6 — cập nhật 18/07/2026).
3. Gateway §3.3: các path vận hành nhận `ADMIN|OPERATOR`; đường approve/security/audit giữ `ADMIN` (cập nhật 18/07/2026).
4. Service enforce fine-grained: user-service (KYC), inspection (review/commission), product (moderate), pricing (manual) nhận thêm OPERATOR; reputation thêm trạng thái `PROPOSED` + 2 use case propose/approve.
5. Không đụng luồng Buyer/Seller/INSPECTOR/External Verifier nào.

## 6. Immutability vs Quyền xoá — cách 2 thứ sống chung

**Nguyên tắc: chain giữ hash, không giữ PII; dữ liệu gốc ở service DB và có vòng đời.**

- **Chốt (18/07/2026): `audit_record.content` minimal hoá** — chỉ ID (contractId, userId dạng UUID pseudonymous), số liệu cần verify (amount, quantity, hash con) và metadata sự kiện; **không** contact info, không tên tổ chức, không địa chỉ. UUID không tự tra ngược ra người nếu không truy cập user_db → xoá/ẩn danh hoá bản gốc ở user-service làm đứt liên kết cho truy cập thông thường, chain vẫn verify nguyên vẹn (hash không đổi). **Nói thẳng (18/07/2026): đây là pseudonymisation, CHƯA phải anonymisation hoàn toàn** — cùng userId còn tồn tại trong contract/bank/reputation DB và event payload lưu trữ; ai chiếm được nhiều DB về kỹ thuật vẫn nối lại được. Mapping ND13 (§9) và mọi câu trả lời hội đồng phải dùng đúng chữ pseudonymisation, không claim quá. Sync vào hash-chain §3.
- Xoá file/bản gốc **không bao giờ** kéo theo xoá `audit_record` — record chứng minh "sự kiện đã xảy ra với nội dung có hash X", không phụ thuộc bản gốc còn tồn tại; mất bản gốc chỉ mất khả năng re-derive.
- Email anchor đã rời platform — không thu hồi được, defend bằng lawful basis: nghĩa vụ lưu chứng từ giao dịch (Luật Kế toán ~10 năm) + phục vụ thời hiệu tranh chấp thương mại, không phải consent.
- User đóng tài khoản: contact info ẩn danh hoá sau retention (§8), nhưng dữ liệu gắn hợp đồng/ledger/AML giữ theo hàng riêng của nó — quyền xoá của chủ thể dữ liệu **không phủ** dữ liệu platform buộc giữ theo nghĩa vụ pháp lý (ND13 cũng thừa nhận ngoại lệ này).

### 6b. Phạm vi chứng minh của từng lớp bằng chứng (mới, 18/07/2026)

Tách "bằng chứng pháp lý mạnh" khỏi "tự động thắng tranh chấp" — bảng này là câu trả lời chuẩn khi hội đồng/đối tác hỏi "hash chain chứng minh được gì":

| Thành phần | Chứng minh được | KHÔNG chứng minh được |
|---|---|---|
| Hash file / audit chain | File/record không bị đổi sau thời điểm ghi nhận | Nội dung là đúng sự thật |
| OTP email + step-up | Người có quyền truy cập email + password tại thời điểm ký | Người đó chắc chắn còn thẩm quyền pháp lý (gate KYC/authorization thu hẹp, không loại trừ tuyệt đối) |
| OTS timestamp | Dữ liệu tồn tại trước thời điểm anchor | Giao dịch không có gian lận |
| Inspection report | Ý kiến chuyên môn của đơn vị giám định, toàn vẹn từ lúc ingest | Phán quyết pháp lý cuối cùng |

Hệ thống bảo toàn bằng chứng và nâng chất lượng bằng chứng — trọng tài/toà mới là nơi phán quyết. Đây cũng là lý do EUDR output gọi là **"EUDR evidence package / DDS-supporting export"**, không claim là Due Diligence Statement hoàn chỉnh (nghĩa vụ risk assessment/mitigation/submission thuộc operator/trader tại EU — product doc, cập nhật cùng ngày).

## 7. AML Tipping-off Exemption

`SuspiciousTransactionReport`, `ELEVATED_RISK` và structuring signals là dữ liệu **về** user mà user **không được biết** — báo cho đối tượng biết mình đang bị nghi ngờ (tipping-off) là vi phạm nguyên tắc AML và vô hiệu hoá detection. Quy tắc:

- Mọi luồng "user xem/export dữ liệu về mình" (hiện tại: `/me`, credit export có consent; tương lai: data-subject access request theo ND13) **loại trừ tường minh** nhóm này.
- `ELEVATED_RISK` không xuất hiện trong public-summary, credit export, hay bất kỳ DTO nào user chạm được; hành vi hệ thống với cặp bị cờ không khác biệt quan sát được từ phía user (đã đúng theo reputation §8 — ghi thành quy tắc governance).
- Chỉ ADMIN + luồng maker-checker §5.3 chạm nhóm này; mọi lần đọc log theo §8.

## 8. Retention Matrix + Read-audit

| Dữ liệu | Giữ | Căn cứ | Hết hạn thì |
|---|---|---|---|
| Hợp đồng + Signature + snapshot + evidence quyết toán | 10 năm sau khi hợp đồng đóng | Chứng từ kế toán + thời hiệu tranh chấp | Archive → ẩn danh hoá tham chiếu cá nhân |
| Ledger bank-service | 10 năm | Chứng từ tài chính | Archive, không xoá |
| Audit chain + OTS proof | Vĩnh viễn | Hash-only sau §6, không còn PII | — (quyết định tường minh, không phải mặc định) |
| Hồ sơ KYC + giấy uỷ quyền | 5 năm sau khi quan hệ chấm dứt | Chuẩn lưu trữ định danh AML | Xoá file, giữ metadata quyết định (ai duyệt, khi nào) |
| `SuspiciousTransactionReport` / AML records | 5 năm | Chuẩn AML | Xoá |
| EUDR report | 5 năm (đã chốt) | EUDR | Xoá |
| Notification log evidence (anchor/OTS/security) | Theo hợp đồng liên quan (10 năm) | Bằng chứng "đã gửi bản nào" | Cùng vòng đời hợp đồng |
| Notification log thường | 2 năm | Vận hành/đối soát | Xoá |
| OTP artifacts (`signature_otp` hash, attempts) | 90 ngày sau expiry | Chỉ phục vụ điều tra gian lận gần | Xoá |
| Contact info account đã đóng | Ẩn danh hoá sau 5 năm | Không còn mục đích | Trừ phần dính hàng có retention dài hơn |
| File orphan chưa gắn nghiệp vụ | 1 tuần (đã chốt file-service) | Không có mục đích | Xoá |
| Message broker (chứa email routing) | TTL ≤ 14 ngày | PII trong broker là chấp nhận có chủ đích | Broker tự expire |
| Analytics warehouse | Backup daily như MySQL khác (**sửa 18/07/2026** — catch-up queue chỉ bù outage ngắn, cold rebuild chưa hỗ trợ nên mất DB = mất lịch sử dashboard) | Read model | Ẩn danh hoá dimension theo nguồn |

**Read-audit cho Restricted (bổ sung — control rẻ hơn compartment hoá):** mỗi lần OPERATOR/ADMIN **mở** hồ sơ KYC, ledger detail, hay AML record → ghi log `{actorId, role, resourceType, resourceId, timestamp, caseContext?}` tại service sở hữu. Không cần chain, log thường + retention 2 năm là đủ deterrence; "mọi admin biết hết" được trị bằng vết đọc, không phải bằng phân quyền đọc phức tạp.

## 8b. DLQ & Event Replay Runbook (mới, 18/07/2026)

Hệ đã có retry + DLX + Outbox + idempotency ở từng service; mục này chốt **quy trình vận hành** khi message rơi vào DLQ — trước đây chưa doc nào ghi ai làm gì.

**Nguyên tắc replay:** replay luôn dùng **`eventId` gốc** — replay là redelivery, không phải event mới. Idempotency toàn hệ key theo `eventId` (bank §4, `user_event_idempotency`, dedup notification `(event_id, recipient_email, notification_type)`), nên consumer đã xử lý sẽ skip an toàn; sinh `eventId` mới là tạo nghiệp vụ mới → chính là con đường giải ngân hai lần. Cấm sửa payload khi replay; payload sai thuộc nhóm 2 dưới đây.

| Nhóm message trong DLQ | Nhận diện | Ai xử lý | Cách xử lý |
|---|---|---|---|
| **Transient hết retry** (consumer down, timeout, deadlock) | Lỗi hạ tầng, payload hợp lệ | OPERATOR | Replay `eventId` gốc sau khi nguyên nhân hết (alert/metric xác nhận); không cần approve |
| **Permanent/validation** (schema lệch, dữ liệu tham chiếu không tồn tại) | Fail ngay không retry | Developer chẩn đoán → OPERATOR replay | KHÔNG replay nguyên trạng — fix code/data trước (deploy consumer sửa, hoặc hành động nghiệp vụ tạo dữ liệu thiếu), rồi replay `eventId` gốc |
| **Tiền / security / evidence** (`bank.*`, `escrow.*`, audit ingest, security lock, OTP/anchor mail) | Theo routing key | OPERATOR đề xuất → **ADMIN approve** (maker-checker §5.3, cùng pattern) | Trước replay: check trạng thái aggregate đích (đã lock/release chưa, notification đã `SENT` chưa) — idempotency là lưới, không phải lý do bỏ check |

**Sau replay, kiểm tra gì:** trạng thái aggregate đích khớp kỳ vọng (milestone/escrow/ledger entry tồn tại đúng 1 lần; notification `SENT`); với nhóm tiền, `LedgerAuditReconciliationJob` (bank §5b.1) là lưới sau cùng bắt lệch số. Replay và kết quả đều ghi log vận hành có attribution (ai, khi nào, eventId).

**Không xử lý được → `PARKED`:** khi developer xác nhận event không còn xử lý được đúng nghĩa (dữ liệu nguồn đã đổi, event lỗi thời so với trạng thái hiện tại) — đánh dấu PARKED + lý do + attribution, **giữ message** theo retention log vận hành (2 năm), không xoá. Hệ quả nghiệp vụ (nếu có) xử lý bù bằng hành động nghiệp vụ có attribution — **tuyệt đối không sửa DB tay** (nguyên tắc 7). DLQ nhóm security/evidence tạo operational alert ngay khi rơi vào (đã chốt ở notification §5) — OPERATOR theo dõi queue hằng ngày, không đợi ai hỏi.


## 8c. Backup & Disaster Recovery tối thiểu (mới, 18/07/2026)

NFR đã có fault tolerance/observability nhưng chưa đặc tả backup/restore. Policy tối thiểu — không multi-region, chỉ policy + demo restore trên môi trường test:

| Thành phần | Backup | RPO | Ghi chú |
|---|---|---|---|
| MySQL từng service | Full daily (mysqldump/xtrabackup) | 24h | Mỗi service backup DB của mình — đúng database-per-service |
| `bank_db` + `audit_db` | Daily + **binlog liên tục** | ≤ 15 phút | Tiền + bằng chứng: ưu tiên phục hồi cao nhất, mất ít nhất |
| MinIO | Bucket mirror/versioning daily | 24h | `storageHash` trong DB là công cụ verify file sau restore |
| Keycloak | Realm export JSON theo config repo | Theo lần đổi config | User demo seed lại được; production export định kỳ |
| RabbitMQ | **Không backup message** | — | Message transient có chủ đích — **Outbox là nguồn phát lại**: sau restore, outbox poller resend event chưa confirm, consumer idempotency (eventId) nuốt trùng lặp an toàn |

**RTO đề xuất:** 4h cho toàn hệ (đồ án: demo được restore 1 môi trường test trong buổi).

**Quy tắc restore — phần quan trọng hơn backup:**
1. **Thứ tự ưu tiên:** `bank_db`/`audit_db` trước → user/contract/escrow → còn lại. Analytics restore từ backup như DB khác (**sửa 18/07/2026** — catch-up queue chỉ bù outage ngắn, không thay được backup; cold rebuild toàn lịch sử chưa hỗ trợ).
2. **Không restore riêng lẻ `bank_db` hoặc `escrow_db`:** backup các DB không cùng point-in-time; restore 1 mình DB tiền tạo lệch cross-service im lặng. Sau BẤT KỲ restore nào đụng tiền/bằng chứng: hệ ở trạng thái maintenance **ở tầng deployment/gateway** (service chưa nhận traffic — KHÔNG dùng `system_lock`: theo bank §3.5.5 lock/unlock chỉ External Verifier ký được, không có đường Admin tự bật; nếu deployment có verifier phối hợp thì lock verifier là lớp cộng thêm, không phải điều kiện), chạy `VerifyChainJob` (chain integrity) + `LedgerAuditReconciliationJob` (bank §5b.1 — ledger ↔ chain) **pass rồi mới mở traffic**.
3. **Restore test định kỳ:** mỗi tháng (tối thiểu 1 lần trước bảo vệ) restore backup mới nhất vào môi trường test + chạy 2 job verify — backup chưa từng restore thử là backup chưa tồn tại.
4. Restore là thao tác Technical Custodian nhưng quyết định "mở lại traffic sau restore đụng tiền" là ADMIN (business authority — nguyên tắc 7), ghi log attribution.


## 9. ND13/2023/NĐ-CP & Cross-border

- **Lawful basis theo nhóm xử lý:** KYC/thẩm quyền = nghĩa vụ pháp lý + thực hiện hợp đồng; ledger/chứng từ = nghĩa vụ pháp lý; credit export cho tổ chức tín dụng = **consent tường minh** (đã đúng theo reputation design); AML = nghĩa vụ pháp lý (kèm exemption §7). Không có xử lý mục đích marketing → không cần consent marketing.
- **Quyền chủ thể dữ liệu:** xem (`/me` + tương lai DSAR, trừ §7), sửa (profile PENDING/resubmit), xoá (theo §6 — có ngoại lệ nghĩa vụ pháp lý), rút consent (credit export ngừng từ thời điểm rút, không hồi tố bản đã export).
- **Cross-border:** SendGrid outbound → email chứa PII + snapshot hợp đồng chảy qua hạ tầng US — ghi nhận là chuyển dữ liệu ra nước ngoài theo ND13; scope đồ án ghi Known Limitation + hướng production (đánh giá tác động chuyển giao / chọn region provider). OTS lên Bitcoin sạch — chỉ hash.
- Hồ sơ đánh giá tác động xử lý dữ liệu chính thức: ngoài scope đồ án; doc này là bản map kỹ thuật để làm nhanh khi cần.

## 10. Delta cần sync vào docs khác (18/07/2026)

| Doc | Delta |
|---|---|
| user-service §2/§4.2/§6 | Enum role thêm `OPERATOR`; §4.2 KYC API nhận `ADMIN\|OPERATOR`; migration MODIFY |
| api-gateway §3.3 | Path vận hành nhận `ADMIN\|OPERATOR`; approve/security/audit giữ `ADMIN` |
| reputation §7/§8 | Maker-checker: trạng thái `PROPOSED`, use case propose/approve, event payload thêm `proposedByOperatorId`/`approvedByAdminId` |
| hash-chain §3 | `audit_record.content` minimal hoá — không PII, chỉ ID/số liệu/hash |
| inspection/product/pricing | Use case vận hành nhận thêm OPERATOR — sửa lúc implement, không đổi flow |
| bank §5b / hash-chain §4.5 / notification §4 / milestone-escrow §8b (18/07) | LedgerAuditReconciliationJob (đối chiếu tiền ledger ↔ chain) + statement export + enhancement logistics |
| Mọi service có consumer (18/07) | Áp DLQ/replay runbook §8b lúc implement — replay theo eventId gốc, nhóm tiền/security cần ADMIN approve; không đổi event contract nào |

## 11. Known Limitations / Out of Scope

- Maker-checker cho KYC tổ chức giá trị lớn — enhancement.
- Compartment hoá quyền *đọc* theo case (OPERATOR chỉ thấy hồ sơ được gán) — enhancement; Phase 2 dùng read-audit §8.
- DSAR endpoint tự phục vụ, hồ sơ ND13 chính thức, đánh giá cross-border chính thức — production.
- Chức danh tổ chức (Compliance/Risk/Finance) — organizational evolution, không phải role hệ thống Phase 2.

## 12. Status — Data Governance Design

**Chốt:** framework nhẹ trong docs, không service riêng. Ownership matrix map về actor thật; `OPERATOR` là **kết quả của governance analysis** và làm thật Phase 2 (cost thấp so với mức làm cứng hệ thống); maker-checker đúng 2 điểm reputation; `audit_record.content` minimal hoá để quyền xoá và immutability sống chung; AML tipping-off exemption tường minh; retention matrix có căn cứ + read-audit cho Restricted; map ND13 ở mức đồ án. Bổ sung cùng ngày: nguyên tắc Technical Custodian (quyền hạ tầng ≠ business authority) + trách nhiệm người tạo dữ liệu; contingency giữ 1 ADMIN nếu cuối kỳ thiếu thời gian.

---

*Design session: 18/07/2026 · Chưa code · Sẵn sàng đồng bộ vào SDS/Architecture ở lần regenerate kế tiếp.*
