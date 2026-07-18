---
name: verification-matrix-phase2
description: "Verification matrix Phase 2 — danh sách invariant hệ thống ↔ test chứng minh, xếp ưu tiên; nguồn sự thật cho test plan trước khi code."
status: DESIGNED — chưa code.
metadata:
  type: test-plan
  phase: 2
  extends: "toàn bộ 14 design docs phase 2"
  related: "data-governance-phase2-design.md §2/§8b/§8c; bank-service-phase2-design.md §4/§5b; hash-chain-phase2-design.md §4-5"
---

## 1. Mục đích

Mỗi invariant quan trọng của hệ phải có **1 test chứng minh nó đứng vững** (hiện 39 rows sau 2 review pass 18/07) — không phải test coverage chung chung, mà test đúng thứ sẽ gây thảm hoạ nếu vỡ. Đây là danh sách ưu tiên code/test cao nhất; viết test cho bảng này **trước** khi viết test khác.

Ưu tiên: **P0** = tiền/immutability/bằng chứng — vỡ là mất tính chính danh của platform. **P1** = access control/luồng an toàn. **P2** = đúng nghiệp vụ.

## 2. Matrix

### P0 — Tiền, immutability, bằng chứng

| # | Invariant | Test | Nguồn design |
|---|---|---|---|
| 1 | Event lặp không release/lock tiền hai lần | Publish cùng bank request nhiều lần với `payload.sourceEventId == envelope.eventId` → đúng 1 bộ ledger entry; result dùng eventId mới và causationId=request eventId | bank §4 idempotency |
| 2 | Ledger append-only — không UPDATE/DELETE | Repository chỉ có insert/select; test lớp repo + quyền DB user | bank ledger, governance nguyên tắc 3 |
| 3 | Hợp đồng đã `SIGNED` không sửa được terms | Gọi update terms sau ký → `409`; hash không đổi | signature §3, milestone §3.1 |
| 4 | `audit_record` bị sửa phải bị phát hiện | Tamper trực tiếp DB (đổi amount/hash) → `VerifyChainJob` fail + đúng vị trí | hash-chain §4.2/§5 |
| 5 | Ledger lệch với record đã anchor phải bị phát hiện | Sửa 1 ledger leg lệch typed audit projection (`contractId`, `milestoneId`, `settledAmount`, `seizedAmount`, release/refund legs) → `LedgerAuditReconciliationJob` bắn `reconciliation_mismatch` | bank §5b.1; hash-chain §4.5 |
| 6 | Bank completion callback lặp không nhân đôi | Gửi duplicate callback → 1 bộ entry, trạng thái không đổi lần 2 | bank §4 |
| 7 | DLQ replay không side effect lặp | Replay `eventId` gốc sau khi đã xử lý → consumer skip, không entry mới | governance §8b |
| 8 | Contract settled không còn tiền lock treo | Sau `contract.settled`/`cancelled`: tổng lock theo contract = 0 (reconciliation test) | bank §3.2, escrow |
| 9 | Kill switch chặn toàn bộ dòng tiền | ES256/RFC8785 request: wrong key/signature/timestamp/action/replayed nonce bị exact 401/409 code; `system_lock ACTIVE` làm mọi `bank.*_requested` fail; Gateway không retry và cap 64 KB | bank §3.5.2-3.5.5; gateway §3.4 |
| 10 | Provisional Level 2 không release quá mức sàn và bảo toàn tiền | Buyer im lặng → `sellerReleaseAmount` dùng release floor, không buyer refund; mọi reconcile/terminal leg explicit, tổng release+refund+remaining lock bảo toàn batchAmount, zero command bị omit | milestone §3.2 Bước 0-3; bank §3.3 |
| 11 | Khoá cọc fail không kẹt im lặng | Bank fail liên tục → hết 3 retry → `escrow.deposit_lock_failed` + contract giữ `SIGNED` + notification 3 bên; `RetryDepositLock` sau khi hết lỗi → `ACTIVE` | milestone §3.1 |
| 11b | Partial lock không nuốt tiền: buyer LOCKED + seller fail → terminal phải refund | Mock seller-leg fail vĩnh viễn → `MarkActivationFailed` → `bank.refund_requested` cho leg buyer, qua `ACTIVATION_REFUND_PENDING`, chỉ `ACTIVATION_FAILED` khi refund confirmed; refund fail → đứng lại + alert | milestone §3.1 (18/07) |
| 11c | 2 chữ ký phải cùng bản terms | Buyer ký, đổi terms (phải bị 409); ép seller ký hash khác → REJECT, không SIGNED | signature §6 bước 4 (18/07) |
| 11d | Mọi field record đều được hash commit | Tamper riêng `source_type`/`subject_id`/`prev_hash_subject` (giữ nguyên content) → `VerifyChainJob` recompute fail | hash-chain §3 canonical (18/07 r2) |
| 11e | `audit_anchor` cũng bất biến | Repository/DB user chỉ INSERT+SELECT trên audit_anchor; UPDATE/DELETE → denied | hash-chain §3 (18/07 r2) |
| 11f | Source/result hash commitment verify riêng với record hash | `Contract.signedContentHash` 3-way khớp; inspection recompute `resultHash` từ RFC8785 normalized result và `reportHash` từ report identity/file/result/timestamp/actor; tamper bất kỳ field nào bị reject; `record_hash` vẫn verify riêng | hash-chain §4.1; inspection §2.3/§4 |

### P1 — Access control & luồng an toàn

| # | Invariant | Test | Nguồn design |
|---|---|---|---|
| 12 | User khác không thao tác contract người khác | Ownership check → `403` (contract, escrow, statement) | contract/escrow, gateway §4 |
| 13 | Client không tự tiêm identity | Gửi kèm `X-User-Id`/`X-User-Role`/`X-Gateway-Secret` giả → bị strip/overwrite, downstream nhận identity từ JWT | gateway §2/§7 |
| 14 | `/internal/**` không ra ngoài | Gọi từ external → `404/403` tại Gateway, không forward | gateway §3.5 |
| 15 | File nhiễm virus không thành evidence | Upload EICAR qua `StoreOnBehalfOf` và qua email intake → `FAILED`, không có `file.ready`, không gắn được vào milestone/report | file-service §3-4 |
| 16 | OPERATOR không chạm được đường ADMIN | OPERATOR gọi approve maker-checker / `RetryDepositLock` / `/admin/audit` → `403` | governance §5.4 |
| 17 | ADMIN không tự approve đề xuất của chính mình | Cùng 1 account propose rồi approve → reject | governance §5.3 |
| 18 | Unlock sớm không mở khoá nhầm khi nhiều lock chồng | 2 lock (T+60, T+30), unlock sớm lock 1 → `lockedUntil` user-service = T+30, `sign()` vẫn bị chặn | reputation §7, user §2.2 |
| 19 | `sign()` fail-closed khi user-service chết | Stop user-service → sign bị reject, không fallback anonymous | user §3/§7 |
| 20 | OTP fail không gửi muộn | Provider trả lỗi → contract-service nhận 5xx; xác nhận không có mail nào được gửi sau đó với `requestId` cũ trừ khi caller tự retry | notification §2.2/§5 |
| 21 | Public DTO không lộ PII | `GET /users/{id}` → không email/phone/address; public-summary yêu cầu JWT | user §4.1, gateway §3.2 |
| 21b | OTP challenge binding | Verify với JWT user khác / otpId khác contract / terms đã đổi sau phát OTP → 403/409/invalidate; không lấy được "OTP mới nhất" của người khác | signature §6-§7 (18/07) |
| 21c | Lock ordering sống qua restart | Apply unlock revision N, restart service, deliver locked revision N-1 (eventId mới) → bị bỏ qua nhờ `last_lock_revision` persist | user §2.2 (18/07) |
| 21d | `legalHold` chặn xoá tuyệt đối | Set legalHold + retention đã hết → lifecycle job gọi `DeleteFile` → REJECT, file còn nguyên | file-service §7, governance §8 (18/07 r2) |
| 21e | Xoá file 2 bước tự lành | Mock MinIO delete fail sau tombstone → job re-run → cả DB lẫn blob về trạng thái nhất quán, không rác | file-service §7 (18/07 r2) |
| 21f | Listing/offer fail-closed khi user-service chết | Stop user-service → CreateListing/CreateOffer trả 503, user đang khoá không lách được | user §3 (18/07 r2) |

### P2 — Đúng nghiệp vụ

| # | Invariant | Test | Nguồn design |
|---|---|---|---|
| 22 | Cà phê/cao su bắt buộc plot; gạo/điều không | Commodity-gate test 2 chiều: tạo listing thiếu plot → fail/pass đúng theo commodity | product (EUDR gate) |
| 23 | Analytics chết không ảnh hưởng giao dịch | Stop analytics-service → settle milestone vẫn hoàn tất; analytics **catch-up từ queue backlog** khi sống lại (outage ngắn — KHÔNG phải cold rebuild, sửa 18/07/2026) | governance §3 (read model) |
| 24 | Notification dedup theo recipient + type | 1 event 2 recipients + retry → mỗi (recipient, type) đúng 1 mail `SENT` | notification §3.1/§5 |
| 25 | Restore đụng tiền phải qua verify mới mở | Restore `bank_db` test env → 2 job verify chạy pass trước khi mở traffic (demo DR) | governance §8c |
| 26 | Event cũ không ghi đè quyết định mới | Deliver `reputation.locked/unlocked` sai thứ tự (`lockRevision` nhỏ hơn — sửa 18/07) → bị bỏ qua | user §2.2 |
| 26b | Plot overlap chéo seller bị bắt | Seller B đăng ký polygon trùng/lọt trong polygon Seller A → `plotReuseRisk = HIGH` + OPERATOR review, không auto-reject | product §2.3b (18/07) |
| 26c | Yield anomaly là signal, không phải gate | `declaredQuantity` vượt Σ(plotArea×maxYield) → `yieldRisk` + yêu cầu bổ sung/review; listing KHÔNG bị auto-reject | product §2.3b (18/07 r2) |
| 26d | Deployment policy enforce được | CI check: image pin theo digest, secret không nằm trong image, protected branch bắt buộc approval — fail build nếu vi phạm (policy test, không phải runtime test) | governance §2 threat model (18/07 r2) |

## 3. Ghi chú thực thi

- P0 #2, #4, #5: cần test đụng thẳng DB (tamper) — viết dạng integration test với Testcontainers MySQL, không mock repository.
- P0 #7 + P1 #17: dùng chung fixture maker-checker/replay của governance §8b.
- Matrix này là living doc: mỗi quyết định design mới phải tự hỏi "invariant nào mới sinh ra?" và thêm row trước khi code.

---

*Design session: 18/07/2026 · Chưa code · Test plan ưu tiên cao nhất trước khi implement.*
