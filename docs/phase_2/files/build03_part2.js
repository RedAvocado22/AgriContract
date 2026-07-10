const fs = require("fs");
const { D, T, runs, P, H1, H2, H3, bullet, numbered, quote, callout, legal, risk, src, table, spacer, cover, toc, endMark, buildDoc } = require("./acdocx.js");
const { body, push, code, svcTable, codeblock } = require("./build03_part1.js");
const { Packer, AlignmentType } = D;

// 3.8 audit
push(H2("3.8 audit-service"));
push(svcTable([
  ["Port · DB", "8092 · audit_db (DB user chỉ có INSERT + SELECT)"],
  ["Trách nhiệm", "Chuỗi hash bất biến; neo Bitcoin (OpenTimestamps); verify định kỳ; xuất báo cáo EUDR"],
  ["Aggregate", "AuditRecord (dual chain: prevHashGlobal + prevHashContract trên cùng 1 bảng; ots_proof)"],
]));
push(P("audit-service nhận và nối các hash từ nhiều nguồn (signedContentHash từ contract-service, reportHash từ inspection-service, các milestone event mang số liệu tranh chấp được) vào một chuỗi append-only. Không phải mọi event vào chain — tiêu chí là event phải mang số liệu/quyết định có thể bị tranh chấp làm bằng chứng. Dùng một bảng duy nhất với hai cột prevHash khác mục đích (chuỗi toàn cục + chuỗi per-contract) thay vì hai bảng riêng."));
push(P([runs("Bất biến ở tầng quyền DB, không chỉ ở code. ", { bold: true }), runs("Tài khoản DB của audit-service chỉ có quyền INSERT + SELECT — không UPDATE/DELETE. VerifyChainJob chạy hàng tuần (2–3h sáng Chủ nhật) đối chiếu chuỗi và so với OTS proof neo trên Bitcoin — bắt được cả cascade tampering (xoá rồi viết lại toàn bộ prevHash phía sau). Verify fail → alert tự động song song tới Admin và nhiều contact phía Software Buyer, không qua một gatekeeper duy nhất.", {})]));
push(legal("Luật GDĐT 2023, Điều 14.2", "Giá trị chứng cứ của thông điệp dữ liệu dựa trên độ tin cậy của phương thức khởi tạo, lưu trữ và bảo toàn tính nguyên vẹn. Chuỗi hash append-only + quyền DB INSERT-only + neo độc lập (email, Bitcoin) là hiện thực hoá kỹ thuật của yêu cầu “bảo toàn tính nguyên vẹn” này."));

// 3.9 file
push(H2("3.9 file-service"));
push(svcTable([
  ["Port · DB", "8089 · file_db · MinIO object storage"],
  ["Trách nhiệm", "Lưu trữ file tập trung agnostic với nghiệp vụ; xử lý async (virus-scan, email-parse); retention EUDR"],
  ["Aggregate", "File (fileId plain UUID, storageHash, ingestChannel, status PROCESSING/READY/FAILED, attached)"],
]));
push(P("file-service chỉ biết blob + metadata kỹ thuật, không biết ý nghĩa nghiệp vụ của file (evidence, cadastral, report…) — ý nghĩa nằm ở tên field bên dịch vụ giữ fileId. Ba entrypoint tách theo trust boundary (không phải một API generic) để ingestChannel không bị giả mạo: nếu để caller tự khai channel, bất kỳ ai cũng có thể tự xưng SYSTEM_GENERATED để né virus-scan. Trạng thái kỹ thuật (file toàn vẹn chưa) tách hoàn toàn khỏi quyết định duyệt nghiệp vụ (thuộc dịch vụ khác). Retention neo vào EUDR (tối thiểu 5 năm); orphan-cleanup qua cờ attached theo pattern ConfirmAttached."));
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
  ["Trách nhiệm", "Read model CQRS thuần; tổng hợp time-series phục vụ dashboard quản trị (hiệp hội/Admin)"],
  ["Aggregate", "Star schema thu nhỏ: dim_contract, fact_milestone_performance, fact_contract_settlement, fact_contract_cancellation, agg_monthly_commodity_stats"],
  ["Consumes", "contract.signed (dim_contract), contract.settled, contract.cancelled, milestone.settled, milestone.cancelled_with_penalty (+ force majeure flags)"],
]));
push(P("Pure consumer — không gọi ngược (Feign) về dịch vụ nào; mọi dữ liệu đến từ việc nghe RabbitMQ. Thiết kế cứng này để analytics (non-critical path) dù sập/quá tải vì query báo cáo nặng cũng không tạo tải ngược lên contract/bank đang xử lý giao dịch. Hybrid: fact tables ghi event thô incremental (giải nghẽn ghi) + agg tables pre-computed bằng @Scheduled 1h sáng (giải nghẽn đọc/tính %). Bắt buộc idempotency log theo message_id (at-least-once làm lặp số tiền). Metric tập trung vào B2B value: tỷ lệ phá vỡ hợp đồng, hiệu quả ký quỹ, xu hướng bất khả kháng."));
push(P([runs("Hai điểm sửa sau rà soát (06/07/2026). ", { bold: true }), runs("Thứ nhất, đếm \"hợp đồng đã hoàn tất\" phải lấy từ fact_contract_settlement (nguồn contract.settled, đúng granularity Contract) — không được suy từ fact_milestone_performance (granularity Milestone, sai bất cứ khi nào hợp đồng còn dở dang). Thứ hai, has_force_majeure gần như luôn sai nếu chỉ UPDATE trực tiếp, vì event force_majeure_resolved luôn tới trước khi milestone đạt trạng thái cuối — cần bảng staging trung gian để merge đúng thời điểm INSERT.", {})]));

// 3.12 notification
push(H2("3.12 notification-service"));
push(svcTable([
  ["Port · DB", "8085 · notification_db"],
  ["Trách nhiệm", "Thông báo hướng sự kiện (email/in-app); gửi OTP ký; neo hash qua email; weekly digest"],
  ["Aggregate", "NotificationLog (eventId dedup key), EmailTemplate"],
]));
push(P("Pure consumer sự kiện. Ngoài thông báo thường, đảm nhận hai vai trò bảo mật: gửi OTP kèm signedContentHash lúc ký (vừa xác thực người, vừa gắn đúng nội dung), và neo hash qua email — gửi hash cho cả hai bên sau mỗi lần ký/nộp report làm điểm neo ngoài platform. Weekly digest hash toàn chuỗi gửi cho Software Buyer. Retry 3 lần exponential backoff trước khi vào DLX; idempotency theo eventId."));

// ============================================================
// 4. COMMUNICATION
// ============================================================
push(H1("4. Giao tiếp và đảm bảo nhất quán"));
push(H2("4.1 Đồng bộ vs bất đồng bộ"));
push(table(
  [1900, 3100, 4638],
  ["Loại", "Công cụ", "Khi nào dùng · ví dụ"],
  [
    ["Sync REST", "Spring Cloud OpenFeign", "Caller cần response ngay để tiếp tục business logic — vd contract-service validate + đóng listing (product-service) khi SIGNED"],
    ["Async Event", "RabbitMQ + @RabbitListener", "Trigger downstream không cần chờ kết quả — vd contract.signed → escrow-service khoá tiền; escrow → notification gửi email"],
  ],
  { size: 18 }
));
push(P("Saga theo mô hình choreography: mỗi dịch vụ publish event khi hoàn thành một bước, dịch vụ tiếp theo subscribe và phản ứng — không orchestrator. Bù trừ (compensation) cũng qua event ngược, không rollback phân tán."));

push(H2("4.2 Transactional Outbox Pattern"));
push(P("Vấn đề cốt lõi: nếu một dịch vụ lưu DB thành công nhưng publish RabbitMQ thất bại (hoặc ngược lại), hệ thống rơi vào trạng thái inconsistent — đây là dual-write problem. Giải pháp: ghi event vào bảng outbox trong cùng transaction với việc đổi trạng thái nghiệp vụ; một @Scheduled poller đọc bảng này và publish lên RabbitMQ, đánh dấu PUBLISHED sau khi thành công. DB commit là atomic nên không bao giờ có chuyện đổi state mà thiếu event, hoặc gửi event mà state chưa đổi."));
push(P([runs("Hai biến thể trong hệ thống. ", { bold: true }), runs("Outbox qua RabbitMQ (giữa các dịch vụ, có network hop) và Local Outbox (đồng bộ Milestone → Contract trong cùng contract-service, không qua RabbitMQ — xem Mục 3.3). Cùng nguyên tắc atomic-commit-then-poll, khác phạm vi.", {})]));

push(H2("4.3 Idempotency"));
push(P("RabbitMQ đảm bảo at-least-once — consumer có thể nhận cùng một message nhiều lần (retry, restart giữa chừng). Không khử trùng lặp thì tiền bị khoá/trừ hai lần, số báo cáo bị nhân đôi. Mỗi consumer nhạy cảm có khoá idempotency riêng: bank-service dùng sourceEventId (UNIQUE trên ledger_entry), analytics-service có bảng idempotency_log theo message_id, notification-service dedup theo eventId. Điểm tinh tế ở bank-service: khi nhận trùng thì không insert lại nhưng vẫn re-publish confirmation — vì lý do gửi lại có thể là confirmation lần trước bị mất trên đường về, silent-drop sẽ khiến bên gọi treo mãi."));

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
    ["contract.signed", "contract-service", "escrow-service (trigger lock cọc, mang buyerDepositAmount/sellerDepositAmount tính sẵn), notification-service, audit-service, analytics-service"],
    ["contract.settled", "contract-service", "escrow-service (hoàn buyerDepositRate), reputation-service, analytics-service"],
    ["contract.cancelled", "contract-service", "escrow-service (seize/refund cọc), notification-service, analytics-service"],
    ["milestone.seller_weighed", "contract-service", "file-service, notification-service, audit-service"],
    ["milestone.buyer_confirmed", "contract-service", "notification-service, audit-service (sửa 08/07/2026 — bỏ escrow-service, tránh release tiền 2 lần; release thật đi qua milestone.settled)"],
    ["milestone.settled", "contract-service", "escrow-service, notification-service, reputation-service, analytics-service, audit-service"],
    ["milestone.cancelled_with_penalty", "contract-service", "escrow-service, reputation-service (tính lockDurationDays), analytics-service, audit-service"],
    ["milestone.force_majeure_claimed / _resolved", "contract-service", "escrow-service, notification-service, audit-service"],
    ["milestone.dispute_resolved (mới, 08/07/2026)", "contract-service", "reputation-service (tín hiệu chống lạm dụng FLAG_ISSUE, §3.7)"],
    ["milestone.level2_provisional_settled / _buffer_reconciled / _terminal_settled (mới, 08/07/2026)", "contract-service", "escrow-service, notification-service"],
    ["escrow.deposit_locked (mới, 08/07/2026)", "escrow-service", "contract-service (chuyển ACTIVE); escrow-service tự dùng để lock batchAmount milestone đầu"],
    ["bank.lock_requested / release_ / seize_ / refund_ (entryType tách LOCK_BUYER_DEPOSIT/LOCK_SELLER_DEPOSIT, 08/07/2026)", "escrow-service", "bank-service"],
    ["bank.lock_completed / _failed (+ release/seize/refund)", "bank-service", "escrow-service"],
    ["bank.large_transaction_flagged (mới, 08/07/2026)", "bank-service", "reputation-service (1 input AML), audit-service — báo cáo ≥500tr, không hold"],
    ["file.ready / file.failed", "file-service", "dịch vụ sở hữu fileId tương ứng"],
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
  "  entry_id        UUID PRIMARY KEY,",
  "  source_event_id UUID NOT NULL UNIQUE,   -- idempotency key",
  "  contract_id     UUID NOT NULL,",
  "  milestone_id    UUID NULL,              -- NULL = buyerDepositRate",
  "  user_id         UUID NOT NULL,",
  "  entry_type      VARCHAR(20) NOT NULL,   -- LOCK_* | RELEASE_* | SEIZE_* | REFUND_*",
  "  amount          DECIMAL(15,2) NOT NULL,",
  "  created_at      TIMESTAMP NOT NULL DEFAULT now()",
  ");",
]));
push(P("Chuỗi hash audit (audit-service) — dual chain trên một bảng, DB user chỉ INSERT + SELECT:"));
push(codeblock([
  "CREATE TABLE audit_record (",
  "  record_id         UUID PRIMARY KEY,",
  "  contract_id       UUID NOT NULL,",
  "  source_type       VARCHAR(50) NOT NULL,",
  "  content           JSON NOT NULL,",
  "  record_hash       VARCHAR(64) NOT NULL,",
  "  prev_hash_global  VARCHAR(64),   -- chuỗi toàn cục",
  "  prev_hash_contract VARCHAR(64),  -- chuỗi per-contract",
  "  ots_proof         BLOB NULL      -- OpenTimestamps",
  ");",
]));
push(table(
  [2400, 7238],
  ["Database", "Bảng chính"],
  [
    ["user_db", "user_profiles (userId = Keycloak sub, authorization_expires_at)"],
    ["product_db", "products (+ variety_name), listings, product_plot (GEOMETRY), plot_registry_entry (GEOMETRY)"],
    ["contract_db", "contracts, contract_terms, milestones, signatures, contract_domain_events (Outbox), milestone_sync_outbox (Local Outbox)"],
    ["escrow_db", "escrow_accounts, escrow_milestones (state-only)"],
    ["bank_db", "ledger_entry (append-only)"],
    ["inspection_db", "inspection_report, level2_inspection_commission"],
    ["reputation_db", "lock_entry (insert-only), reputation_score"],
    ["file_db", "file, email_parse_failure"],
    ["pricing_db", "price_history (append-only), price_ingestion_failure"],
    ["audit_db", "audit_record (dual chain, INSERT+SELECT only)"],
    ["analytics_db", "dim_contract, fact_*, agg_monthly_commodity_stats, analytics_idempotency_log"],
    ["notification_db", "notification_logs (eventId dedup), email_templates"],
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

// ============================================================
// 8. SECURITY
// ============================================================
push(H1("8. Mô hình bảo mật"));
push(H2("8.1 Xác thực và phân quyền"));
push(P("Keycloak (:8180, realm agricontract) là IAM server, cấp JWT RS256 với các vai trò BUYER/SELLER/ADMIN/INSPECTOR. Luồng: Frontend đăng nhập Keycloak → nhận JWT → gửi kèm Authorization: Bearer → Nginx → API Gateway validate token qua Keycloak JWKS → tiêm định danh (X-User-Id, X-User-Email, X-User-Role) xuống downstream. Downstream tin các header này vì request đến từ Gateway trong mạng nội bộ; ranh giới tin cậy nội bộ được siết bằng X-Internal-Secret, tiến hoá lên mTLS khi triển khai thật."));
push(H2("8.2 Bảo vệ tính toàn vẹn và chống chối bỏ"));
push(P("Sáu lớp phối hợp, mỗi lớp phủ một attack vector khác nhau:"));
push(numbered("Hash nội dung hợp đồng — SHA-256 toàn bộ ContractTerms lúc ký; mọi state transition sau đó verify hash trước khi proceed. Sửa DB → hash mismatch → operation reject."));
push(numbered("Chuỗi hash audit trail — previousHash + recordHash; DB user chỉ INSERT + SELECT; verify khi export EUDR và định kỳ hàng tuần."));
push(numbered("Hash inspection report — SHA-256(content + timestamp + inspectorId) lúc submit; contract-service verify trước khi advance state; bất biến sau submit."));
push(numbered("Lưu hash nhiều nơi — signedContentHash và reportHash lưu độc lập ở contract_db, audit_db, và file gửi hai bên; attacker phải compromise cả ba cùng lúc."));
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
    ["12 dịch vụ nghiệp vụ", "8081–8093", "custom Spring Boot build (8090 bỏ trống)"],
    ["keycloak", "8180", "IAM server, RBAC"],
    ["rabbitmq", "5672 / 15672", "Message broker (Saga + Outbox); 15672 = management UI"],
    ["mysql (per-service)", "—", "Mỗi dịch vụ một container MySQL riêng; bật binlog ROW cho CDC tương lai"],
    ["MinIO", "—", "Object storage cho file-service"],
    ["Redis", "—", "Cache pricing, rate-limit, token blacklist, pub-sub"],
    ["SendGrid", "—", "Email + Inbound Parse (report Level 2); thay MailHog"],
  ],
  { size: 18 }
));
push(P("Tất cả container trên cùng bridge network, giao tiếp bằng service name. Dịch vụ Spring Boot depends_on MySQL và RabbitMQ với condition service_healthy."));
push(H2("9.2 Hướng tiến hoá hạ tầng"));
push(P("Các quyết định pragmatic phục vụ phạm vi đồ án, có đường nâng cấp rõ ràng: Docker Compose → Kubernetes (replicas); @Scheduled Outbox Poller → Debezium CDC (đọc binlog thay vì poll); X-Internal-Secret → mTLS; Nginx → Cloudflare WAF; thêm Zipkin + Spring Cloud Sleuth (distributed tracing), ELK (log aggregation), Prometheus + Grafana (metrics)."));

// ============================================================
// 10. FRONTEND
// ============================================================
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
    ["Một endpoint user-service còn lộ email (IDOR sót)", "Phát hiện lúc rà code, không thuộc đường tiền tệ", "Bổ sung ownership/role check trước khi đưa vào vận hành thật"],
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
  "Luật Giao dịch Điện tử 2023 (20/2023/QH15) — Điều 14.2, 22–23, 34.",
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
Packer.toBuffer(doc).then(buf => { fs.writeFileSync("/tmp/AgriContract_Architecture_v2.docx", buf); console.log("written", buf.length); });
