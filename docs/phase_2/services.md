# Phase 2 — Service List

Phase 1: 5 services → Phase 2: 13 services (+8)

## Services mới

| # | Service | Port | Pain point dạy được |
|---|---|---|---|
| 6 | bank-service | 8086 | External API integration, idempotency với third-party, mock → real migration |
| 7 | inspection-service | 8087 | Role-based access granular (INSPECTOR), immutable evidence record, third-party actor |
| 8 | reputation-service | 8088 | Pure event-driven read model, aggregate từ nhiều source, eventually consistent |
| 9 | file-service | 8089 | Async processing, MinIO binary storage, cross-service reference by ID |
| 10 | search-service | 8090 | CQRS read model, cross-service query, eventual consistency ở read side |
| 11 | pricing-service | 8091 | Redis cache + invalidation, pub-sub broadcast, external data ingestion |
| 12 | audit-service | 8092 | Event store pattern, write-once read-many, EUDR compliance export |
| 13 | analytics-service | 8093 | CQRS analytics, time-series aggregation, pre-computed vs real-time tradeoff |

## Phase 2 infra evolution (từ architecture.md)

- Docker Compose → Kubernetes (replicas: 3)
- @Scheduled Outbox Poller → Debezium CDC
- X-Internal-Secret → mTLS
- Nginx → Cloudflare WAF
- Redis (rate limit Gateway, token blacklist, WebSocket Pub-Sub)
- MinIO (file-service)
- SendGrid (thay MailHog)
- Zipkin + Spring Cloud Sleuth
- ELK log aggregation
- Prometheus + Grafana

## Gaps còn lại (pattern level, không cần service mới)

1. **Circuit breaker** — Resilience4j `@CircuitBreaker` trên Feign calls
2. **Optimistic locking** — `@Version` trên ContractJpaEntity + EscrowAccountJpaEntity
3. **Event schema versioning** — khi payload thay đổi, consumer cũ handle thế nào

### Security gaps (Phase 2)

4. **Contract content hash** — khi `sign()` được gọi, SHA-256 toàn bộ `ContractTerms`, lưu vào `Contract.signedContentHash`. Mọi state transition sau SIGNED (`activate`, `dispute`, `arbitrate`) verify hash trước khi proceed — nếu content bị sửa trong DB, hash mismatch → operation reject. Implement trong `contract-service` domain layer, không cần service mới.

5. **Audit trail hash chain** — mỗi `AuditRecord` trong `audit-service` chứa `previousHash` + `recordHash` (SHA-256 của content + previousHash). Append-only enforcement: DB user của `audit-service` chỉ có `INSERT` + `SELECT` permission, không có `UPDATE`/`DELETE`. `audit-service` verify chain integrity khi export EUDR report — nếu record bị xóa hoặc sửa giữa chain, verification fail ngay.

6. **Inspection report hash** — khi INSPECTOR submit report, `inspection-service` tính SHA-256 của toàn bộ report content + timestamp + inspectorId, lưu `reportHash`. `contract-service` verify hash khi đọc report để advance state. `file-service` lưu file hash riêng để detect tampering ở MinIO storage layer.

7. **Multi-location hash storage** — `signedContentHash` và `reportHash` được lưu ở nhiều nơi độc lập: `contract-service` DB, `audit-service` DB, và file export gửi cho buyer/seller lúc ký/submit. Attacker phải compromise tất cả cùng lúc để cover được tampering.

8. **Email timestamp anchor** — sau mỗi contract SIGNED và mỗi inspection report SUBMITTED, `notification-service` gửi email cho cả buyer lẫn seller kèm hash của nội dung. Email history là external anchor độc lập với platform — dù toàn bộ DB bị compromise, bên nào cũng có bằng chứng hash trong email. Weekly digest hash của toàn bộ audit chain cũng được gửi cho Software Buyer (hiệp hội/DN deploy).

9. **Chữ ký số** — buyer/seller ký contract content bằng private key của họ (theo Luật GDĐT 2023, chữ ký số có giá trị pháp lý). Platform chỉ lưu signature, không thể forge chữ ký thay user. Giải quyết non-repudiation: user không thể deny đã ký sau khi tranh chấp xảy ra. **Lưu ý:** chữ ký số chứng minh "đúng người đó đã ký", không chứng minh "người đó có thẩm quyền đại diện pháp nhân". Thẩm quyền đại diện được đảm bảo bằng KYC — ADMIN verify giấy đăng ký kinh doanh và thẩm quyền ký kết trước khi active tài khoản (SELLER self-register + verification workflow đã có trong Phase 2 roadmap). Chữ ký số + KYC kết hợp mới cover hoàn toàn.

---

**Trade-off của Hash so với Blockchain:**

Hash detect tampering nhưng không prevent được nếu attacker có write access vào cả content lẫn hash storage. Blockchain giải quyết điều này bằng cách distribute hash ra nhiều node độc lập. AgriContract chọn hash vì:
- Platform có trusted operator (hiệp hội/DN deploy) — bài toán trustless consensus không tồn tại
- Multi-location storage + email anchor cover được attack vector tương đương với chi phí implementation thấp hơn nhiều
- Blockchain trong 5 tháng với team 3 người là không thực tế

Khi hội đồng hỏi: *"Hash chain detect tampering. Chữ ký số prevent repudiation. Email timestamp là external anchor độc lập. Ba lớp này cover được các attack vector mà blockchain giải quyết, với độ phức tạp tương xứng với context có trusted operator."*
