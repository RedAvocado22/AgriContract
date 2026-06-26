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
