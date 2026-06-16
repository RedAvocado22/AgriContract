# AgriContract — Shared Contracts

> Source of truth cho API contracts và event payloads giữa các services.
> Mọi thay đổi phải được thảo luận và cập nhật file này trước khi implement.

---

## 1. REST API Contracts

### 1.1 product-service — `GET /api/v1/listings/{listingId}`

Response body (NHL implement, NMC consume qua Feign):

```json
{
  "listingId": "string",
  "sellerId": "string",
  "productId": "string",
  "productName": "string",
  "quantity": 100.0,
  "quantityUnit": "string",
  "priceFloor": 50000.0,
  "currency": "VND",
  "deliveryDeadline": "2026-08-01",
  "status": "ACTIVE"
}
```

`status` values: `ACTIVE` | `CLOSED` | `EXPIRED`

### 1.2 user-service — `GET /api/v1/users/{userId}`

Response body (NHL implement, NMC consume qua Feign):

```json
{
  "userId": "string",
  "organizationName": "string",
  "email": "string",
  "role": "BUYER"
}
```

`role` values: `BUYER` | `SELLER` | `ADMIN`

---

## 2. RabbitMQ — Exchange & Queue Names

**Exchange:** `agricontract.events` (topic, durable)

**Queue naming convention:** `{service}.{routing-key}`

| Routing Key | Publisher | Queue (Consumer) |
|---|---|---|
| `contract.signed` | contract-svc | `escrow-svc.contract.signed` |
| | | `notification-svc.contract.signed` |
| `contract.delivered` | contract-svc | `escrow-svc.contract.delivered` |
| | | `notification-svc.contract.delivered` |
| `contract.cancelled` | contract-svc | `escrow-svc.contract.cancelled` |
| | | `notification-svc.contract.cancelled` |
| `contract.disputed` | contract-svc | `escrow-svc.contract.disputed` |
| | | `notification-svc.contract.disputed` |
| `escrow.locked` | escrow-svc | `contract-svc.escrow.locked` |
| | | `notification-svc.escrow.locked` |
| `escrow.released` | escrow-svc | `contract-svc.escrow.released` |
| | | `notification-svc.escrow.released` |
| `escrow.penalized` | escrow-svc | `notification-svc.escrow.penalized` |

---

## 3. Event Payloads

> Mọi event đều có `eventId` (UUID) và `occurredAt` (ISO-8601 instant) ở top-level.
> Consumer dùng `eventId` để dedup trước khi xử lý.

### 3.1 contract.signed

```json
{
  "eventId": "uuid",
  "eventType": "contract.signed",
  "contractId": "string",
  "buyerId": "string",
  "sellerId": "string",
  "agreedPrice": 5000000.0,
  "currency": "VND",
  "deliveryDeadline": "2026-08-01",
  "occurredAt": "2026-06-11T10:00:00Z"
}
```

### 3.2 contract.delivered

```json
{
  "eventId": "uuid",
  "eventType": "contract.delivered",
  "contractId": "string",
  "buyerId": "string",
  "sellerId": "string",
  "occurredAt": "2026-06-11T10:00:00Z"
}
```

### 3.3 contract.cancelled

```json
{
  "eventId": "uuid",
  "eventType": "contract.cancelled",
  "contractId": "string",
  "buyerId": "string",
  "sellerId": "string",
  "cancelledBy": "BUYER",
  "reason": "string",
  "occurredAt": "2026-06-11T10:00:00Z"
}
```

`cancelledBy` values: `BUYER` | `SELLER`

### 3.4 contract.disputed

```json
{
  "eventId": "uuid",
  "eventType": "contract.disputed",
  "contractId": "string",
  "buyerId": "string",
  "sellerId": "string",
  "reason": "string",
  "occurredAt": "2026-06-11T10:00:00Z"
}
```

### 3.5 escrow.locked

```json
{
  "eventId": "uuid",
  "eventType": "escrow.locked",
  "contractId": "string",
  "amount": 5000000.0,
  "currency": "VND",
  "occurredAt": "2026-06-11T10:00:00Z"
}
```

### 3.6 escrow.released

```json
{
  "eventId": "uuid",
  "eventType": "escrow.released",
  "contractId": "string",
  "amount": 5000000.0,
  "currency": "VND",
  "occurredAt": "2026-06-11T10:00:00Z"
}
```

### 3.7 escrow.penalized

```json
{
  "eventId": "uuid",
  "eventType": "escrow.penalized",
  "contractId": "string",
  "penalizedParty": "BUYER",
  "penaltyAmount": 500000.0,
  "refundAmount": 4500000.0,
  "currency": "VND",
  "occurredAt": "2026-06-11T10:00:00Z"
}
```

`penalizedParty` values: `BUYER` | `SELLER`
