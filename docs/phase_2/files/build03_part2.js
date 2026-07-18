const fs = require("fs");
const { writeDocx } = require("./docx_output.js");
const { D, T, runs, P, H1, H2, H3, bullet, numbered, quote, callout, legal, risk, src, table, spacer, cover, toc, endMark, buildDoc } = require("./acdocx.js");
const { body, push, code, svcTable, codeblock } = require("./build03_part1.js");
const { Packer, AlignmentType } = D;

// 3.8 audit
push(H2("3.8 audit-service"));
push(svcTable([
  ["Port · DB", "8092 · audit_db (DB user chỉ có INSERT + SELECT)"],
  ["Trách nhiệm", "Chuỗi hash bất biến; neo OpenTimestamps; verify định kỳ; xuất gói bằng chứng DDS-supporting"],
  ["Aggregate", "AuditRecord (prevHashGlobal + prevHashSubject) + AuditAnchor append-only; sourceHash tách recordHash"],
]));
push(P("audit-service là writer duy nhất của audit_db: consume event có giá trị chứng cứ, chuẩn hoá subject/source/content tối giản rồi nối dual chain append-only. AuditRecord dùng prevHashGlobal + prevHashSubject; sourceHash cam kết artefact nguồn, recordHash cam kết bản ghi canonical; OTS proof nằm trong AuditAnchor riêng, không UPDATE audit_record."));
push(P([runs("Bất biến ở tầng quyền DB, không chỉ ở code. ", { bold: true }), runs("Tài khoản DB của audit-service chỉ có quyền INSERT + SELECT — không UPDATE/DELETE. VerifyChainJob chạy hàng tuần (2–3h sáng Chủ nhật) đối chiếu chuỗi và so với OTS proof neo trên Bitcoin — bắt được cả cascade tampering (xoá rồi viết lại toàn bộ prevHash phía sau). Verify fail → alert tự động song song tới Admin và nhiều contact phía Software Buyer, không qua một gatekeeper duy nhất.", {})]));
push(legal("Luật GDĐT 2023, khoản 2 Điều 14", "Giá trị chứng cứ của thông điệp dữ liệu dựa trên độ tin cậy của phương thức khởi tạo, lưu trữ và bảo toàn tính nguyên vẹn. Chuỗi hash append-only + quyền DB INSERT-only + neo độc lập (email, Bitcoin) là hiện thực hoá kỹ thuật của yêu cầu “bảo toàn tính nguyên vẹn” này."));

// 3.9 file
push(H2("3.9 file-service"));
push(svcTable([
  ["Port · DB", "8089 · file_db · MinIO object storage"],
  ["Trách nhiệm", "Lưu trữ file tập trung agnostic với nghiệp vụ; xử lý async (virus-scan, email-parse); retention EUDR"],
  ["Aggregate", "File (fileId, storageHash, ingestChannel, status, attached, retentionUntil, legalHold, deletedAt)"],
]));
push(P("file-service chỉ biết blob + metadata kỹ thuật, không biết ý nghĩa nghiệp vụ của file (evidence, cadastral, report…) — ý nghĩa nằm ở tên field bên dịch vụ giữ fileId. Ba entrypoint tách theo trust boundary (không phải một API generic) để ingestChannel không bị giả mạo: nếu để caller tự khai channel, bất kỳ ai cũng có thể tự xưng SYSTEM_GENERATED để né virus-scan. Trạng thái kỹ thuật (file toàn vẹn chưa) tách hoàn toàn khỏi quyết định duyệt nghiệp vụ (thuộc dịch vụ khác). Retention theo ma trận domain; legalHold chặn xoá. Owning service gọi DeleteFile hai bước tombstone→MinIO; orphan-cleanup chỉ áp file attached=false quá một tuần theo ConfirmAttached."));
push(callout("Async processing là pain point chính.", "Ít nhất hai luồng không chạy đồng bộ trong request: quét virus và parse email report. Với file từ hòm thư ngoài (EMAIL_INTAKE), hai luồng nối chuỗi (parse → virus-scan), không chạy song song độc lập — nếu tách nhầm theo nguồn gốc thay vì theo loại công việc, file từ internet sẽ lọt virus-scan. Retry/DLQ dùng cơ chế dead-letter-exchange + TTL built-in của RabbitMQ.", "note"));

// 3.10 pricing
push(H2("3.10 pricing-service"));
push(svcTable([
  ["Port · DB", "8091 · pricing_db · Redis cache"],
  ["Trách nhiệm", "Ingest giá tham chiếu nông sản từ nguồn ngoài (VNSAT scrape + admin nhập tay); phục vụ hiển thị lúc tạo listing"],
  ["Aggregate", "PriceQuote (commodity, itemName, province?, price, source, capturedAt, ingestedAt)"],
]));
push(P("Chỉ ingest external reference price — không tự tính giá từ data nội bộ (data thưa, và một con số “giá thị trường” tính từ chính vài giao dịch trong platform có thể bị buyer lớn lợi dụng tạo giá giả). Cà phê/lúa gạo scrape từ thitruongnongsan.gov.vn (VNSAT) qua Jsoup 2 bước GET→POST mô phỏng ASP.NET WebForms postback; cao su/điều Admin nhập tay. MySQL append-only (price_history) là nguồn thật, Redis chỉ là cache (cache-aside, không TTL cứng — “cũ hay mới” tính động bằng ngưỡng stale theo từng commodity). Ingest job @Scheduled sống sót qua lỗi (ghi price_ingestion_failure, không throw kéo sập scheduler)."));

// 3.11 analytics
push(H2("3.11 analytics-service"));
push(svcTable([
  ["Port · DB", "8093 · analytics_db"],
  ["Trách nhiệm", "Event-driven projection/CQRS read model kiêm derived-signal producer; dashboard quản trị + AML pattern signal"],
  ["Aggregate", "Star schema thu nhỏ: dim_contract, fact_milestone_performance, fact_contract_settlement, fact_contract_cancellation, agg_monthly_commodity_stats"],
  ["Consumes", "contract.signed (dim_contract), contract.settled, contract.cancelled, milestone.settled, milestone.cancelled_with_penalty (+ force majeure flags)"],
]));
push(P("Analytics không nằm trên transaction critical path và không gọi đồng bộ (REST/Feign) ngược core service. Hybrid fact incremental + agg pre-computed; bắt buộc idempotency log. AmlPatternScanJob dùng MySQL 8 DATE_SUB + JSON_ARRAYAGG và publish analytics.structuring_pattern_detected cho reputation/bank. Outage ngắn catch-up queue backlog; không có cold rebuild/full-history, nên analytics_db phải backup/restore."));
push(P([runs("Hai điểm sửa sau rà soát (06/07/2026). ", { bold: true }), runs("Thứ nhất, đếm \"hợp đồng đã hoàn tất\" phải lấy từ fact_contract_settlement (nguồn contract.settled, đúng granularity Contract) — không được suy từ fact_milestone_performance (granularity Milestone, sai bất cứ khi nào hợp đồng còn dở dang). Thứ hai, has_force_majeure gần như luôn sai nếu chỉ UPDATE trực tiếp, vì event force_majeure_resolved luôn tới trước khi milestone đạt trạng thái cuối — cần bảng staging trung gian để merge đúng thời điểm INSERT.", {})]));

// 3.12 notification
push(H2("3.12 notification-service"));
push(svcTable([
  ["Port · DB", "8085 · notification_db"],
  ["Trách nhiệm", "Thông báo hướng sự kiện; internal sync OTP delivery; neo hash qua email; weekly digest/integrity alert"],
  ["Aggregate", "NotificationLog (dedup eventId + recipientEmail + notificationType; templateVersion)"],
]));
push(P("Notification nghiệp vụ/evidence là consumer RabbitMQ; riêng OTP dùng POST /internal/v1/notifications/otp-email từ contract-service để caller chỉ báo “đã gửi” khi provider accepted. Không có external Gateway route. Publisher mang recipient/attachment reference; retry async 3 lần trước DLX; dedup theo (eventId/sourceEventId, recipientEmail, notificationType). OTP plaintext không log và không gửi muộn sau response failure."));

// ============================================================
// 4. COMMUNICATION
// ============================================================
push(H1("4. Giao tiếp và đảm bảo nhất quán"));
push(H2("4.1 Đồng bộ vs bất đồng bộ"));
push(table(
  [1900, 3100, 4638],
  ["Loại", "Công cụ", "Khi nào dùng · ví dụ"],
  [
    ["Sync REST", "Spring Cloud OpenFeign", "Caller cần response ngay — vd contract-service validate user/KYC hoặc gọi internal OTP delivery trước khi báo OTP đã gửi"],
    ["Async Event", "RabbitMQ + @RabbitListener", "Trigger downstream không cần chờ kết quả — vd contract.signed → escrow lock; owner service → notification.*_requested để gửi mail"],
  ],
  { size: 18 }
));
push(P("Saga theo mô hình choreography: mỗi dịch vụ publish event khi hoàn thành một bước, dịch vụ tiếp theo subscribe và phản ứng — không orchestrator. Bù trừ (compensation) cũng qua event ngược, không rollback phân tán."));

push(H2("4.2 Transactional Outbox Pattern"));
push(P("Vấn đề cốt lõi: nếu một dịch vụ lưu DB thành công nhưng publish RabbitMQ thất bại (hoặc ngược lại), hệ thống rơi vào trạng thái inconsistent — đây là dual-write problem. Giải pháp: ghi event vào bảng outbox trong cùng transaction với việc đổi trạng thái nghiệp vụ; một @Scheduled poller đọc bảng này và publish lên RabbitMQ, đánh dấu PUBLISHED sau khi thành công. DB commit là atomic nên không bao giờ có chuyện đổi state mà thiếu event, hoặc gửi event mà state chưa đổi."));
push(P([runs("Hai biến thể trong hệ thống. ", { bold: true }), runs("Outbox qua RabbitMQ (giữa các dịch vụ, có network hop) và Local Outbox (đồng bộ Milestone → Contract trong cùng contract-service, không qua RabbitMQ — xem Mục 3.3). Cùng nguyên tắc atomic-commit-then-poll, khác phạm vi.", {})]));

push(H2("4.3 Idempotency"));
push(P("RabbitMQ đảm bảo at-least-once — consumer có thể nhận cùng message nhiều lần. Mỗi consumer nhạy cảm có khoá riêng: bank dùng sourceEventId, analytics dùng message_id, notification dùng (eventId, recipientEmail, notificationType) vì một event có thể gửi nhiều người/loại mail. Bank nhận trùng không insert lại nhưng vẫn re-publish confirmation để tránh caller treo khi confirmation cũ bị mất."));

push(H2("4.4 Các pattern chống lỗi bổ sung"));
push(bullet([runs("Optimistic locking ", { bold: true }), runs("(@Version trên ContractJpaEntity và EscrowAccountJpaEntity) — chống lost-update khi cập nhật đồng thời.", {})]));
push(bullet([runs("Circuit breaker ", { bold: true }), runs("(Resilience4j @CircuitBreaker trên Feign call) — chặn cascading failure khi một dịch vụ downstream chậm/chết.", {})]));
push(bullet([runs("Event schema versioning ", { bold: true }), runs("— khi payload event đổi, consumer cũ cần xử lý được version cũ lẫn mới (tolerant reader).", {})]));

// ============================================================
// 5. EVENT CATALOG
// ============================================================
push(H1("5. Event Catalog"));
push(P("Exchange theo aggregate (topic), cộng một dead-letter exchange (fanout) nhận message fail sau 3 retry. Mỗi message mang eventId (UUID) + occurredAt ở top-level để consumer dedup. Convention đặt tên: {aggregate}.{actor}_{past_tense_verb}."));
push(table(
  [3100, 2400, 4138],
  ["Routing key", "Publisher", "Consumer(s)"],
  [
    ["contract.signed", "contract-service", "escrow-service (trigger lock cọc, mang buyerDepositAmount/sellerDepositAmount tính sẵn), audit-service, analytics-service"],
    ["contract.settled", "contract-service", "escrow-service (hoàn buyerDepositRate), reputation-service, analytics-service"],
    ["contract.cancelled", "contract-service", "escrow-service (seize/refund cọc), analytics-service"],
    ["milestone.seller_weighed", "contract-service", "file-service, audit-service"],
    ["milestone.buyer_confirmed", "contract-service", "audit-service (sửa 08/07/2026 — bỏ escrow-service, tránh release tiền 2 lần; release thật đi qua milestone.settled)"],
    ["milestone.settled", "contract-service", "escrow-service, reputation-service, analytics-service, audit-service"],
    ["milestone.cancelled_with_penalty", "contract-service", "escrow-service, reputation-service (tính lockDurationDays), analytics-service, audit-service"],
    ["milestone.force_majeure_claimed / _resolved", "contract-service", "escrow-service, audit-service"],
    ["milestone.dispute_resolved (mới, 08/07/2026)", "contract-service", "reputation-service (tín hiệu chống lạm dụng FLAG_ISSUE, §3.7)"],
    ["milestone.level2_provisional_settled / _buffer_reconciled / _terminal_settled (mới, 08/07/2026)", "contract-service", "escrow-service"],
    ["escrow.deposit_locked (mới, 08/07/2026)", "escrow-service", "contract-service (chuyển ACTIVE); escrow-service tự dùng để lock batchAmount milestone đầu"],
    ["bank.lock_requested / release_ / seize_ / refund_ (entryType tách LOCK_BUYER_DEPOSIT/LOCK_SELLER_DEPOSIT, 08/07/2026)", "escrow-service", "bank-service"],
    ["bank.lock_completed / _failed (+ release/seize/refund)", "bank-service", "escrow-service"],
    ["bank.large_transaction_flagged (mới, 08/07/2026)", "bank-service", "reputation-service (1 input AML), audit-service — báo cáo ≥500tr, không hold"],
    ["file.ready / file.failed", "file-service", "dịch vụ sở hữu fileId tương ứng"],
    ["notification.*_requested", "user / contract / inspection / audit / bank", "notification-service — payload đã có recipient + template data; không làm phình domain event"],
  ],
  { size: 16 }
));
push(P([runs("Hai loại event tách cấp. ", { bold: true }), runs("Event cấp milestone (milestone.*) xử lý từng đợt giao hàng; event cấp contract (contract.settled/cancelled) xử lý phần buyerDepositRate ở cấp hợp đồng. Một lần Contract.cancel() bắn đúng một contract.cancelled cho phần cọc, độc lập với các milestone.cancelled_with_penalty riêng lẻ của từng đợt còn lại.", {})]));

// ============================================================
// 6. DATABASE
// ============================================================
push(H1("6. Thiết kế cơ sở dữ liệu"));
push(H2("6.1 Nguyên tắc database-per-service"));
push(P("Mỗi dịch vụ sở hữu DB riêng, không dịch vụ nào truy cập DB của dịch vụ khác. Dữ liệu chia sẻ qua REST (sync), event (async), hoặc denormalization lúc tạo bản ghi. Mọi tham chiếu cross-service là plain UUID (userId, fileId, reportId…) — không REFERENCES cross-database, integrity giữ ở application layer."));
push(P([runs("Denormalization có chủ đích. ", { bold: true }), runs("Contract snapshot productName, quantity, agreedPrice, tên hai bên tại thời điểm tạo offer — đây là domain invariant: hợp đồng đã ký không đổi khi listing hay tên tổ chức bị sửa sau. Cùng nguyên tắc snapshot áp cho milestoneSchedule, ProductPlot, level2InspectorOrg.", {})]));

push(H2("6.2 Một số schema then chốt"));
push(P("Sổ cái tiền (bank-service) — append-only, số dư luôn derive từ SUM, không lưu sẵn:"));
push(codeblock([
  "CREATE TABLE ledger_entry (",
  "  entry_id        CHAR(36) PRIMARY KEY,",
  "  source_event_id CHAR(36) NOT NULL UNIQUE,   -- idempotency key",
  "  contract_id     CHAR(36) NOT NULL,",
  "  milestone_id    CHAR(36) NULL,              -- NULL = buyerDepositRate",
  "  user_id         CHAR(36) NOT NULL,",
  "  entry_type      VARCHAR(20) NOT NULL,   -- LOCK_* | RELEASE_* | SEIZE_* | REFUND_*",
  "  amount          DECIMAL(15,2) NOT NULL,",
  "  created_at      TIMESTAMP NOT NULL DEFAULT now()",
  ");",
]));
push(P("Chuỗi hash audit (audit-service) — AuditRecord + AuditAnchor append-only, DB user chỉ INSERT + SELECT:"));
push(codeblock([
  "CREATE TABLE audit_record (",
  "  record_id         CHAR(36) PRIMARY KEY,",
  "  subject_type      VARCHAR(20) NOT NULL, -- CONTRACT|USER_PAIR|SYSTEM",
  "  subject_id        VARCHAR(255) NOT NULL,",
  "  source_type       VARCHAR(50) NOT NULL,",
  "  source_event_type VARCHAR(100) NOT NULL,",
  "  source_hash       VARCHAR(64) NULL,",
  "  content           JSON NOT NULL, -- minimalized, không PII",
  "  record_hash       VARCHAR(64) NOT NULL,",
  "  prev_hash_global  VARCHAR(64) NULL,",
  "  prev_hash_subject VARCHAR(64) NULL,",
  "  created_at        TIMESTAMP(3) NOT NULL",
  ");",
  "CREATE TABLE audit_anchor (",
  "  anchor_id CHAR(36) PRIMARY KEY, record_id CHAR(36) NOT NULL,",
  "  anchored_hash VARCHAR(64) NOT NULL, anchor_type VARCHAR(20) NOT NULL,",
  "  proof BLOB NOT NULL, created_at TIMESTAMP(3) NOT NULL",
  ");",
]));
push(P("recordHash = SHA256(canonicalJson({recordId, subjectType, subjectId, sourceType, sourceEventType, content, prevHashGlobal, prevHashSubject, createdAt})); canonical JSON sort key, UTF-8, không whitespace, UTC millisecond, decimal fixed-scale và giữ null key."));
push(table(
  [2400, 7238],
  ["Database", "Bảng chính"],
  [
    ["user_db", "user_profiles (role OPERATOR, verified_by_actor_id, last_lock_revision)"],
    ["product_db", "products (+ variety_name), listings, product_plot (GEOMETRY), plot_registry_entry (GEOMETRY)"],
    ["contract_db", "contracts, contract_terms, milestones, signatures BUYER/SELLER, signature_otp, outbox/local outbox"],
    ["escrow_db", "escrow_accounts, escrow_milestones (state-only)"],
    ["bank_db", "ledger_entry (append-only)"],
    ["inspection_db", "inspection_report, inspector_signature, level2_inspection_commission"],
    ["reputation_db", "lock_entry, lock_override_event, governance_action_request, pair_risk_state, reputation_score"],
    ["file_db", "file (retention/legal_hold/tombstone), email_parse_failure"],
    ["pricing_db", "price_history (append-only), price_ingestion_failure"],
    ["audit_db", "audit_record + audit_anchor (append-only, INSERT+SELECT only)"],
    ["analytics_db", "dim_contract, fact_*, agg_monthly_commodity_stats, analytics_idempotency_log"],
    ["notification_db", "notification_logs (eventId + recipientEmail + notificationType dedup; templateVersion; providerMessageId)"],
  ],
  { size: 18 }
));

// ============================================================
// 7. SAGA FLOW
// ============================================================
push(H1("7. Luồng nghiệp vụ chính — Choreography Saga"));
push(P("Luồng đầy đủ của một milestone từ lúc hợp đồng kích hoạt tới khi quyết toán, minh hoạ cách các dịch vụ phản ứng dây chuyền qua event mà không có điều phối trung tâm:"));
push(table(
  [700, 2500, 3400, 3038],
  ["#", "Actor / Dịch vụ", "Hành động / Event", "Kết quả"],
  [
    ["1", "contract-service", "Hợp đồng đủ 2 chữ ký → publish contract.signed", "buyerDepositRate (+ sellerDepositRate nếu có) được yêu cầu khoá"],
    ["2", "escrow → bank-service", "bank.lock_requested (LOCK_BUYER_DEPOSIT, + LOCK_SELLER_DEPOSIT nếu có) → bank.lock_completed → publish escrow.deposit_locked", "Ledger ghi LOCK_BUYER_DEPOSIT/LOCK_SELLER_DEPOSIT; contract ACTIVE"],
    ["3", "escrow → bank-service", "Khoá batchAmount milestone đầu (lock sớm)", "Ledger ghi LOCK_MILESTONE"],
    ["4", "Seller (API)", "Cân hàng + upload ảnh → milestone.seller_weighed", "file-service lưu evidence; audit ghi hash"],
    ["5", "Buyer (API)", "Cân lại + upload ảnh → milestone.buyer_confirmed / CONFIRM_CLEAN", "Tính pro-rata Delta 2"],
    ["6", "escrow → bank-service", "milestone.settled (lockedAmount, actualAmount) → RELEASE_TO_SELLER (+ REFUND_TO_BUYER nếu chênh)", "Ledger ghi release/refund; milestone SETTLED"],
    ["7", "contract-service", "Milestone cuối SETTLED → Local Outbox → completeAllMilestones()", "Contract SETTLED; publish contract.settled"],
    ["8", "escrow → bank-service", "contract.settled → REFUND_TO_BUYER (buyerDepositRate) + RELEASE_TO_SELLER (sellerDepositRate nếu có)", "Hoàn/giải cọc; reputation ghi input tích cực"],
  ],
  { size: 17, colAlign: [AlignmentType.CENTER, null, null, null] }
));
push(P("Nhánh bù trừ: nếu buyer flag vấn đề ở bước 5, milestone vào AWAITING_SELLER_RESPONSE; seller im lặng quá hạn → auto-settle theo số buyer báo, hoặc contest → DisputeRoutingService (INSPECTOR 3 cấp). Nếu một bên cancel, contract.cancelled kích hoạt seize/refund cọc theo initiatedBy; các milestone chưa settle bắn milestone.cancelled_with_penalty riêng."));
push(P("Nhánh kích hoạt thất bại: retry từng leg khoá cọc tối đa 3 lần (5m/30m/2h) với cùng sourceEventId. ADMIN có RetryDepositLock hoặc MarkActivationFailed; leg đã khoá được refund và Contract SIGNED → ACTIVATION_REFUND_PENDING → ACTIVATION_FAILED chỉ sau mọi refund confirmed. Terminal invariant: tổng lock = 0, không penalty/reputation."));

// ============================================================
// 8. SECURITY
// ============================================================
push(H1("8. Mô hình bảo mật"));
push(H2("8.1 Xác thực và phân quyền"));
push(P("Keycloak (:8180, realm agricontract) là IAM server, cấp JWT RS256 với các vai trò BUYER/SELLER/ADMIN/OPERATOR/INSPECTOR. Luồng: Frontend đăng nhập Keycloak → nhận JWT → gửi kèm Authorization: Bearer → Nginx → API Gateway validate token qua Keycloak JWKS → tiêm định danh (X-User-Id, X-User-Role, X-Correlation-Id, X-Gateway-Secret) xuống downstream. Downstream tin các header này vì request đến từ Gateway trong mạng nội bộ; ranh giới tin cậy nội bộ được siết bằng X-Internal-Secret, tiến hoá lên mTLS khi triển khai thật."));
push(P("Gateway dùng explicit route policy: public chỉ GET listing/product catalog, pricing và audit-hash; reputation public-summary vẫn authenticated. Gateway strip Authorization, X-User-* và internal secret rồi inject đúng bốn header; X-Api-Key audit-hash được giữ nguyên. Daily admin paths cho ADMIN|OPERATOR; reputation propose cho ADMIN|OPERATOR nhưng approve/reject, security/audit/analytics chỉ ADMIN. /internal/**, notification, raw file/bank và OTP không có external route; ownership/KYC/lock/state do owner service enforce."));
push(table(
  [2500, 3100, 4038],
  ["Nhóm route", "Ví dụ", "Policy"],
  [
    ["Public read", "GET listings/products catalog; prices; GET security/audit-hash", "Exact method + path; audit-hash giữ X-Api-Key + rate limit"],
    ["Authenticated", "users, products/listings write, contracts/milestones, escrow, inspections, reputation", "JWT bắt buộc; ownership/state ở owner service"],
    ["Role-gated", "daily ops; reputation propose/approve; security/audit/analytics; inspector", "ADMIN|OPERATOR cho daily/propose; approve/security/audit/analytics chỉ ADMIN; inspector chỉ INSPECTOR"],
    ["External Verifier", "POST security/emergency-lock | emergency-unlock", "Chữ ký bất đối xứng; không JWT/Admin bypass; Gateway không retry"],
    ["Internal-only", "users/notification/OTP/raw file/bank ledger/audit reconciliation", "Không external route; request ngoài trả 404/403"],
  ],
  { size: 17 }
));
push(H2("8.2 Bảo vệ tính toàn vẹn và chống chối bỏ"));
push(P("Sáu lớp phối hợp, mỗi lớp phủ một attack vector khác nhau:"));
push(numbered("Hash nội dung hợp đồng — SHA-256 toàn bộ ContractTerms lúc ký; mọi state transition sau đó verify hash trước khi proceed. Sửa DB → hash mismatch → operation reject."));
push(numbered("Chuỗi hash audit trail — canonical recordHash + prevHashGlobal + prevHashSubject; AuditAnchor tách riêng; verify trước export DDS-supporting và định kỳ."));
push(numbered("Hash inspection report — SHA-256(content + timestamp + inspectorId) lúc submit; contract-service verify trước khi advance state; bất biến sau submit."));
push(numbered("sourceHash trong audit phải khớp signedContentHash/reportHash và hash trong email anchor; recordHash là chain integrity riêng, không dùng thay sourceHash."));
push(numbered("Neo timestamp qua email + Bitcoin — email cho hai bên sau mỗi lần ký/submit là điểm neo ngoài platform; OTS neo hash cam kết toàn cục lên Bitcoin, tồn tại độc lập kể cả khi platform sập."));
push(numbered("Emergency Lock — Zero-Trust Kill Switch cho External Verifier (mới, 08/07/2026) — REST endpoint ký bất đối xứng (RSA/ECDSA), độc lập RabbitMQ, không qua Admin. Chỉ 1 gate chặn (system_lock trước mọi bank.*_requested) vì escrow-service là actor duy nhất gọi bank-service. Đóng băng toàn hệ thống khi External Verifier tự phát hiện tampering qua self-service query hash — không phụ thuộc duy nhất vào job nội bộ platform (chi tiết bank-service §3.5)."));
push(P([runs("Vì sao hash thay vì blockchain. ", { bold: true }), runs("Platform có trusted operator (hiệp hội/DN triển khai) — bài toán trustless consensus không tồn tại. Sáu lớp phủ được attack vector tương đương blockchain với chi phí thấp hơn nhiều, phù hợp ràng buộc 5 tháng của dự án. Kill Switch thu hẹp — không đóng hoàn toàn — giới hạn collusion Admin+External Verifier vẫn là giới hạn cố hữu của mô hình trusted-operator (§11).", {})]));

// ============================================================
// 9. INFRA
// ============================================================
push(H1("9. Hạ tầng và triển khai"));
push(H2("9.1 Thành phần triển khai"));
push(table(
  [2600, 2000, 5038],
  ["Thành phần", "Port", "Ghi chú"],
  [
    ["nginx", "80", "Reverse proxy, rate limit per IP — điểm vào; tiến hoá: Cloudflare WAF"],
    ["api-gateway", "8080", "Spring Cloud Gateway; validate JWT, định tuyến, tiêm định danh"],
    ["12 dịch vụ nghiệp vụ", "8081–8089, 8091–8093", "custom Spring Boot build"],
    ["keycloak", "8180", "IAM server, RBAC"],
    ["rabbitmq", "5672 / 15672", "Message broker (Saga + Outbox); 15672 = management UI"],
    ["mysql (per-service)", "—", "Mỗi dịch vụ một container MySQL riêng; bật binlog ROW cho CDC tương lai"],
    ["MinIO", "—", "Object storage cho file-service"],
    ["Redis", "—", "Cache pricing, rate-limit, token blacklist, pub-sub"],
    ["Email + mailbox", "—", "SMTP/SendGrid outbound; MailHog local; intake@ qua IMAP polling của file-service (không inbound webhook)"],
  ],
  { size: 18 }
));
push(P("Tất cả container trên cùng bridge network, giao tiếp bằng service name. Dịch vụ Spring Boot depends_on MySQL và RabbitMQ với condition service_healthy."));
push(H2("9.2 Hướng tiến hoá hạ tầng"));
push(P("Các quyết định pragmatic phục vụ phạm vi đồ án, có đường nâng cấp rõ ràng: Docker Compose → Kubernetes (replicas); @Scheduled Outbox Poller → Debezium CDC (đọc binlog thay vì poll); X-Internal-Secret → mTLS; Nginx → Cloudflare WAF; thêm Micrometer Tracing với OpenTelemetry hoặc Brave, export sang Zipkin/OTLP; ELK (log aggregation); Prometheus + Grafana (metrics)."));

// ============================================================
// 10. FRONTEND
// ============================================================
push(H2("9.3 Mô hình triển khai custodian — bank-service là adapter, không phải điểm mù"));
push(P([runs("bank-service mock là quyết định kiến trúc có chủ đích, và kiến trúc đã trả giá sẵn cho ngày thay thật: ", { bold: true }), runs("escrow-service là sole caller của bank-service; ledger FBO/Omnibus append-only là nguồn sự thật đối soát độc lập với custodian; mọi operation đi qua một interface hẹp (lock/release/refund, idempotent theo sourceEventId). Thay mock bằng tích hợp thật là thay một adapter — không tầng nào khác của hệ thống thay đổi.", {})]));
push(P("Ba phương án pháp lý khi thương mại hoá, xếp theo mức ưu tiên của nhóm:"));
push(bullet([runs("(a) Hợp tác ngân hàng — phương án chính. ", { bold: true }), runs("Tiền của các bên nằm trong tài khoản chuyên biệt/phong toả mở tại ngân hàng đối tác; nền tảng không chạm tiền, chỉ ra lệnh lock/release qua API ngân hàng theo đúng logic hợp đồng, và đối soát bằng ledger nội bộ. Ngân hàng là bên có phép giữ tiền; nền tảng bán tầng logic. Đây là mô hình các nền tảng escrow thương mại vận hành hợp pháp mà không cần tự xin giấy phép.", {})]));
push(bullet([runs("(b) Giấy phép trung gian thanh toán (Nghị định 52/2024). ", { bold: true }), runs("Tự vận hành ví/thanh toán — chủ động nhất nhưng chi phí xin phép, vốn pháp định và nghĩa vụ tuân thủ nặng nhất; chỉ hợp lý khi quy mô giao dịch đã chứng minh. Ghi nhận là hướng dài hạn, không phải điều kiện khởi động.", {})]));
push(bullet([runs("(c) Không chạm tiền. ", { bold: true }), runs("Nền tảng chỉ làm hợp đồng + bằng chứng + đối soát; hai bên tự thanh toán ngoài hệ thống. Bỏ được toàn bộ rủi ro giấy phép nhưng mất cơ chế tự thực thi — escrow chính là thứ khiến penalty có răng. Giữ làm phương án lùi, không phải mặc định.", {})]));
push(P("Vì sao chọn (a): giữ trọn giá trị tự thực thi của escrow với rủi ro pháp lý thấp nhất, và khớp đúng đường cắt kiến trúc hiện tại — bank-service mock hôm nay chính là chỗ đứng của API ngân hàng đối tác ngày mai, cùng interface, cùng ledger đối soát."));

push(H1("10. Frontend"));
push(table(
  [2400, 2600, 4638],
  ["Hạng mục", "Quyết định", "Lý do"],
  [
    ["Framework", "React + Vite", "SPA, dev server nhanh, HMR"],
    ["Ngôn ngữ", "TypeScript (bắt buộc)", "State machine hợp đồng phức tạp — bắt lỗi lúc compile thay vì runtime"],
    ["State management", "Zustand", "Lightweight, không boilerplate; tránh re-render toàn cây như Context API"],
    ["HTTP client", "Axios", "Interceptor auto-attach JWT, global error handling"],
    ["Auth", "Keycloak JS adapter", "Login redirect → JWT lưu trong memory (không localStorage)"],
    ["Routing", "React Router v6", "Route-level code splitting"],
  ],
  { size: 18 }
));
push(P("Mọi call đều qua API Gateway (không gọi trực tiếp service port). JWT lưu trong memory thay vì localStorage để giảm bề mặt tấn công XSS."));

// ============================================================
// 11. LIMITATIONS
// ============================================================
push(H1("11. Giới hạn kiến trúc và hướng mở rộng"));
push(P("Các giới hạn có chủ đích, đánh đổi phù hợp ràng buộc thời gian/nhân lực của dự án — không phải điểm mù."));
push(table(
  [3100, 3300, 3238],
  ["Giới hạn", "Vì sao chấp nhận được", "Hướng mở rộng"],
  [
    ["bank-service là mock, không tích hợp ngân hàng thật", "Không tổ chức tín dụng nào ký API cho đồ án; interface (event contract) thiết kế sạch, business logic không đổi khi swap", "Tích hợp Agribank/BIDV API — chỉ thay implementation bên trong bank-service"],
    ["Outbox Poller dùng @Scheduled (latency ~1–2s)", "Functional correctness đảm bảo; độ trễ chấp nhận được", "Debezium CDC đọc binlog"],
    ["Eventual consistency ở read side (analytics, reputation score)", "Non-critical path; đánh đổi kinh điển của CQRS/event-driven", "Chấp nhận có chủ đích; tài liệu hoá rõ data lag"],
    ["Chống thông đồng Admin + External Verifier (mới, 08/07/2026 — thay khung cũ \"Admin + đa số Software Buyer\")", "Kill Switch (bank-service §3.5, bảo mật §8.2) đã thu hẹp: 1 bên độc lập ngoài platform tự query hash + tự đóng băng được, không phụ thuộc duy nhất vào job nội bộ. Nhưng nếu chính External Verifier thông đồng với Admin thì vẫn hở — đúng bài toán trustless consensus mà nhóm chủ đích không theo hướng blockchain", "Neo email + Bitcoin đảm bảo bằng chứng tồn tại độc lập cho bên thứ ba kiểm tra; đa External Verifier (quorum) là hướng mở rộng nếu cần siết thêm"],
    ["Chữ ký điện tử cơ bản, chưa có chứng thư CA", "Hợp đồng vẫn hiệu lực; gánh nặng chứng minh bù bằng audit trail", "Tích hợp chữ ký số CA / WebAuthn — nâng chất lượng bằng chứng"],
    ["PII user-service từng lộ qua DTO dùng chung", "Đã đóng ở design Phase 2 bằng boundary rõ", "/users/me trả contact chính chủ; /users/{id} không email/phone/address; /internal/v1/users/{id} chỉ service-to-service và Gateway không route"],
  ],
  { size: 17 }
));

// ============================================================
// 12. SOURCES / REFERENCES
// ============================================================
push(H1("12. Tham chiếu"));
push(H3("Nguyên tắc & pattern"));
[
  "Chris Richardson — Microservices Patterns (decompose by business capability, Saga, Transactional Outbox, API Gateway).",
  "Vaughn Vernon — Implementing Domain-Driven Design (Effective Aggregate Design).",
].forEach(s => push(bullet(s)));
push(H3("Văn bản pháp luật liên quan tới quyết định kiến trúc"));
[
  "Luật Giao dịch Điện tử 2023 (20/2023/QH15) — Điều 8, khoản 2 Điều 14, Điều 22–23 và Điều 34–36.",
  "Nghị định 52/2024/NĐ-CP — Điều 8 (trung gian thanh toán, bảo mật thông tin).",
  "Nghị định 98/2018/NĐ-CP — Điều 15 (giải quyết tranh chấp).",
  "Bộ luật Dân sự 2015 — Điều 142, 156, 351.",
  "EU 2023/1115 sửa đổi 2025/2650 (EUDR) — retention và truy xuất geolocation.",
].forEach(s => push(bullet(s)));

push(endMark());

const doc = buildDoc(body, {
  title: "AgriContract — Kiến Trúc Kỹ Thuật Hệ Thống v2.0",
  headerText: "AgriContract · Kiến trúc kỹ thuật",
  footerText: "v2.0 · Tháng 7/2026",
});
Packer.toBuffer(doc).then(buf => { writeDocx("/tmp/AgriContract_Architecture_v2.docx", buf); });
