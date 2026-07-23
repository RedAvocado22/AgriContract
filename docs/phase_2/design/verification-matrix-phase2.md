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

Mỗi invariant quan trọng của hệ phải có **1 test chứng minh nó đứng vững** (hiện 55 rows: 39 sau 2 review pass 18/07 + 16 rows attribution/remedy 19/07) — không phải test coverage chung chung, mà test đúng thứ sẽ gây thảm hoạ nếu vỡ. Đây là danh sách ưu tiên code/test cao nhất; viết test cho bảng này **trước** khi viết test khác.

Mọi test có deadline/window dùng timestamp UTC và clock business `Asia/Ho_Chi_Minh`; date-only kết thúc `23:59:59.999 ICT`, business-day fixture dùng lịch Việt Nam theo milestone-escrow §1.1.

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
| 8 | Contract terminal không còn tiền lock treo | Normal completion/termination phát `remedy.finalized`, mock một bank leg còn missing/failed → contract vẫn non-terminal và chưa publish lifecycle event; khi đủ expected `remedyLegId` + remaining lock = 0 mới cho `contract.settled`/`contract.terminated`/`SUPERSEDED`/`ACTIVATION_FAILED` (pre-sign `WITHDRAWN` zero by construction) | milestone-escrow §3.1/§6.7; bank §3.2; escrow |
| 9 | Kill switch chặn toàn bộ dòng tiền | ES256/RFC8785 request: wrong key/signature/timestamp/action/replayed nonce bị exact 401/409 code; `system_lock ACTIVE` làm mọi `bank.*_requested` fail; Gateway không retry và cap 64 KB | bank §3.5.2-3.5.5; gateway §3.4 |
| 10 | Provisional Level 2 không release quá mức sàn và bảo toàn tiền | Buyer im lặng → `sellerReleaseAmount` dùng release floor, không buyer refund; mọi reconcile/terminal leg explicit, tổng release+refund+remaining lock bảo toàn batchAmount, zero command bị omit | milestone §3.2 Bước 0-3; bank §3.3 |
| 11 | Khoá cọc fail không kẹt im lặng | Bank fail liên tục → hết 3 retry → `escrow.deposit_lock_failed` + contract giữ `SIGNED` + notification 3 bên; `RetryDepositLock` sau khi hết lỗi → `ACTIVE` | milestone §3.1 |
| 11b | Partial lock không nuốt tiền: buyer LOCKED + seller fail → terminal phải refund | Mock seller-leg fail vĩnh viễn → `MarkActivationFailed` → `bank.refund_requested` cho leg buyer, qua `ACTIVATION_REFUND_PENDING`, chỉ `ACTIVATION_FAILED` khi refund confirmed; refund fail → đứng lại + alert | milestone §3.1 (18/07) |
| 11c | 2 chữ ký phải cùng bản terms | Buyer ký, đổi terms (phải bị 409); ép seller ký hash khác → REJECT, không SIGNED | signature §6 bước 4 (18/07) |
| 11d | Mọi field record đều được hash commit | Tamper riêng `source_type`/`subject_id`/`prev_hash_subject` (giữ nguyên content) → `VerifyChainJob` recompute fail | hash-chain §3 canonical (18/07 r2) |
| 11e | `audit_anchor` cũng bất biến | Repository/DB user chỉ INSERT+SELECT trên audit_anchor; UPDATE/DELETE → denied | hash-chain §3 (18/07 r2) |
| 11f | Source/result hash commitment verify riêng với record hash | `Contract.signedContentHash` 3-way khớp; inspection recompute `resultHash` từ RFC8785 normalized result và `reportHash` từ report identity/file/result/timestamp/actor; tamper bất kỳ field nào bị reject; `record_hash` vẫn verify riêng | hash-chain §4.1; inspection §2.3/§4 |
| 11g | Allegation chưa final không đụng tiền/reputation | Mở `BreachCase` (`breach.reported`) rồi thử phát seize/forfeiture/penalty + kiểm tra reputation consumer → mọi lệnh tiền bị chặn khi `status != RESOLVED`; reputation không có `lock_entry` mới | milestone-escrow §6.4 invariant (19/07) |
| 11h | Người request termination không tự động bị coi là vi phạm | Seller yêu cầu terminate vì buyer `FUNDING_FAILURE` → `finalBreachingRole = BUYER`, seller không bị seize/lock; mirror chiều ngược | milestone-escrow §6.0/§6b (19/07) |
| 11i | `finalBreachingRole = NULL` → không ai bị phạt | `MUTUAL_TERMINATION`/`MUTUAL_REPLACEMENT`/FM/`ACTIVATION_FAILURE` → cọc về đúng chủ (`REFUND_TO_BUYER`/`RELEASE_TO_SELLER`), zero `DEPOSIT_FORFEITURE`/`CONTRACTUAL_PENALTY`, zero `lock_entry` | milestone-escrow §6.5/§6.6/§6.7; bank §3.2; reputation §3 (19/07) |
| 11j | Contractual penalty không vượt cap LegalProfile | `sign()` với `buyerPenaltyRate`/`sellerPenaltyRate` > `maxContractualPenaltyRate` (8% VN_COMMERCIAL_LAW) → reject 400; penalty tính trên batchAmount phần bị vi phạm, không trên totalAmount | milestone-escrow §2.1b (19/07) |
| 11k | Không double recovery | Remedy calculator: với `damagesPolicy = CIVIL_PENALTY_ONLY_UNLESS_EXPRESSLY_CUMULATIVE`/`EXPRESS_PENALTY_ONLY` → không có `DAMAGES_COMPENSATION` leg; với `COMMERCIAL_CUMULATIVE_IF_PROVEN` → DAMAGES chỉ khi có bằng chứng, và `DEPOSIT_FORFEITURE` offset vào penalty; ledger không có 2 khoản seize bù cùng 1 tổn thất | milestone-escrow §2.1b/§6.7; bank §2 (19/07, sửa lần 2) |
| 11l | Mutual replacement nối đúng 2 contract, không double-count | Supersede → contract cũ `SUPERSEDED` + `supersededByContractId`; contract mới `replacesContractId`; milestone `SETTLED` cũ giữ nguyên; tiền lock cũ refund hết (tổng lock contract cũ = 0), contract mới lock từ 0 | milestone-escrow §6.6 (19/07) |
| 11m | Milestone funding failure không kẹt tiền + không phạt oan seller | Mock bank fail lock batch milestone 2 → hết retry → `FUNDING_FAILED`, đồng hồ giao hàng seller tạm dừng (không trigger seller-quá-hạn), leg đã lock refund; hết `fundingCureWindowDays` → buyer breach Rổ A, seller không bị seize | milestone-escrow §6b (19/07) |
| 11n | Termination pro-rata không đụng milestone đã `SETTLED` | `TERMINATION_FOR_BREACH` giữa hợp đồng (milestone 1-2 `SETTLED`, 3-5 chưa) → penalty/forfeiture chỉ tính trên batch 3-5; ledger milestone 1-2 không có entry mới, tiền đã release không bị truy thu | milestone-escrow §6.1 pro-rata (19/07) |
| 11o | Một `remedyDecisionId` → đúng 1 bộ bank legs, ≤ 1 reputation lock (chặn double-consume) | Replay `remedy.finalized` / gửi leg trùng → `ledger_entry.remedy_leg_id` UNIQUE chặn từng leg (đơn vị dedup đúng — 1 decision nhiều legs), reconcile `SUM` group theo `remedy_decision_id` khớp đúng 1 bộ; reputation `lock_entry.remedy_decision_id` UNIQUE chặn lock thứ 2 kể cả từ event khác | milestone-escrow §7.2; reputation §2.1; bank §2 DDL (19/07 lần 2, schema-hoá lần 4) |
| 11p | Supersede crash windows không nuốt tiền/không kẹt hợp đồng | (a) draft replacement không đủ 2 chữ ký → cũ vẫn `ACTIVE`, zero sự kiện tiền; (b) mới `SIGNED`, refund cũ fail giữa chừng → cũ đứng `SUPERSEDE_REFUND_PENDING` + alert, không nhảy `SUPERSEDED`; (c) cũ `SUPERSEDED` xong, activation mới fail → mới đi đường `ACTIVATION_FAILED` chuẩn, cũ không rollback | milestone-escrow §6.6 saga (19/07 lần 3) |
| 11q | Replay evidence event không nhân đôi audit record/anchor | Redeliver cùng `remedy.finalized`/`contract.terminated` (eventId cũ) → `audit_record.source_event_id` UNIQUE reject/no-op, không record mới, không anchor mới, không email evidence thứ 2 | hash-chain §3 `source_event_id` (19/07 lần 3) |

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
| 26e | Delta 1 vượt threshold không auto-gán bẻ kèo | Shortfall > threshold, không FM claim → milestone mở `BreachCase` (không bắn `milestone.cancelled_with_penalty` tự động); pro-rata phần đã giao vẫn release | milestone-escrow §4 nhánh 2 (19/07) |
| 26f | Reputation chỉ phạt strategic breach | `remedy.finalized` với `breachReasonCode = PRODUCTION_SHOCK_NON_FM` → không `lock_entry`; với `SIDE_SELLING` → có; `zeroProgressMultiplier` 1.5x chỉ khi strategic + 0 settled | reputation §3/§4.3 (19/07) |
| 26g | Analytics đo `finalBreachingRole`, không đo người bấm nút | `contract.terminated` các nhánh → `fact_contract_termination` tách `terminationType`/`requestedBy`/`finalBreachingRole`; mutual không đếm vào default rate | analytics (19/07) |
| 26h | Allegation bị bác/được miễn trách không phát hậu quả tiêu cực (đổi tên 19/07 lần 2 — Phase 2 cố tình chưa có cure state, không gọi "cure thành công") | `BreachCase` RESOLVED với `finalBreachingRole = NULL` → không termination, không lock_entry cho bên bị cáo buộc; `remedy.finalized` nếu phát chỉ chứa refund/release legs | milestone-escrow §6.4; reputation §4.3 (19/07) |
| 26i | Inspection đang pending chặn auto-confirm | Milestone `CONTESTED`/có commission Level 1.5-2 đang chạy → hết `buyerConfirmWindowDays` KHÔNG auto `CONFIRM_CLEAN`, không release; timer chỉ áp khi milestone còn đúng state `BUYER_RECEIVED` | milestone-escrow §3.2 bổ sung (19/07) |
| 27 | Quality disposition tính tất định | Recompute từ committed spec/deviation policy trong `signedContentHash` + actual metrics trong `resultHash`; reviewer không thể thêm criterion chủ quan làm đổi kết quả | milestone-escrow §3.3; inspection §2.3/§4 |
| 27b | Exact reject đúng commodity | Coffee `type`, rubber/cashew `grade` mismatch → reject; rice `varietyName` không có trong actual/policy và mọi rice decision chỉ dùng numeric metrics | product §8b; milestone §2.1c/§3.3 |
| 27c | Mapping disposition → attribution/remedy | Sau flag: `CONFORMING` zero punitive/reputation; `PARTIALLY_CONFORMING` max discount một lần và null role; `NON_CONFORMING` Seller + `QUALITY_BELOW_COMMITTED`, refund milestone + policy penalty/forfeiture; `INCONCLUSIVE` zero money | milestone-escrow §3.3 |
| 27d | Inspected path không áp Delta 2 lần hai | Clean path giữ Delta 1/Delta 2; inspected/contested dùng `min(lockedAmount, acceptedQuantityKg × effectiveUnitPrice)`; over-delivery chỉ bị cap | milestone-escrow §3.3/§4 |
| 27e | Quality conservation tách theo fund source | Sum `MILESTONE_PAYMENT` legs = `batchAmount`; penalty và `SELLER_DEPOSIT` forfeiture đối soát riêng; penalty base = `effectiveUnitPrice × committedQuantity`, không phải `totalContractValue` | milestone-escrow §3.3/§6.7; bank §2 |
| 27f | Một quality resolution chỉ có một money trigger | Publish/replay `remedy.finalized` → đúng một bộ legs theo `remedyLegId`; không publish/consume `milestone.settled` cho cùng resolution | milestone-escrow §7.1/§7.2 |
| 27g | Delivery certificate chỉ là evidence | Đính kèm certificate ở seller/buyer weigh vẫn không xuất hiện trong actual metrics/resultHash, không sinh disposition và không thay report confirmed | milestone-escrow §2.2/§3.3; inspection §2.3 |
| 27h | Inspection cost policy chỉ `LOSER_PAYS` | Schema reject mọi giá trị khác; conforming → Buyer, partial/non-conforming → Seller, inconclusive chưa allocate | milestone-escrow §2.1c/§3.3 |
| 27i | Quality reject chỉ fail milestone liên quan | `NON_CONFORMING` refund/phạt milestone bị reject nhưng contract vẫn active cho milestone khác; chỉ supersede/termination flow tường minh mới đổi contract lifecycle | milestone-escrow §3.3/§6 |

## 3. Ghi chú thực thi

- P0 #2, #4, #5: cần test đụng thẳng DB (tamper) — viết dạng integration test với Testcontainers MySQL, không mock repository.
- P0 #7 + P1 #17: dùng chung fixture maker-checker/replay của governance §8b.
- Matrix này là living doc: mỗi quyết định design mới phải tự hỏi "invariant nào mới sinh ra?" và thêm row trước khi code.
- Rows 19/07 (11g-11m, 26e-26h): fixture chung nên dựng quanh `BreachCase` + `remedy.finalized` — 1 helper tạo case + resolve với `finalBreachingRole`/`breachReasonCode` tuỳ ý sẽ phục vụ được cả 12 rows.

---

*Design session: 18/07/2026 · Cập nhật 23/07/2026: bổ sung quality dispute resolution rows 27-27i (deterministic disposition, mapping, quantity precedence, evidence boundary, per-fund conservation, single trigger, cost policy và milestone scope). · Chưa code · Test plan ưu tiên cao nhất trước khi implement.*
