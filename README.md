# AgriContract

> A B2B agricultural forward-contract platform with a self-enforcing escrow layer, built on a 13-service event-driven microservice architecture (Java 21 / Spring Boot 3.3).

AgriContract digitizes the procurement contract lifecycle between Vietnamese cooperatives (HTX) and trading companies, and adds an escrow mechanism that aligns both parties' incentives **before** a transaction happens — not after a dispute lands in court.

The interesting part isn't the domain. It's the engineering problem underneath it: **moving money and state consistently across independent services with no distributed transaction, no dual-write, and no way to lose or double-apply a payment instruction** — the same class of problem real fintech and escrow systems solve.

---

## Why this project is worth a look (for engineers)

This is a graduation project, but it's built around production concerns, not "does it compile":

- **Choreography Saga + Transactional Outbox** for cross-service consistency — no 2PC, no distributed lock. State transitions are driven by events, and every event is written atomically with the business change it describes.
- **Idempotent money movement over at-least-once delivery** — RabbitMQ can redeliver the same instruction; the ledger is designed so a duplicated `lock`/`release`/`refund` can never double-apply.
- **Append-only ledger with derived balances (FBO/omnibus model)** — balances are never stored and mutated in place; they're always computed from immutable ledger entries. This is how real escrow/marketplace custody works, and it sidesteps a whole family of dual-write and race-condition bugs.
- **Tamper-evident audit trail via hash chaining** — each audit record links to the previous one; the DB user has `INSERT`/`SELECT` only. Chain integrity is verified on every EUDR export.
- **Event-driven read models (CQRS)** for reputation and analytics — writes and reads are decoupled across services.
- **Clean, enforced service boundaries** — `contract-service` owns delivery state, `escrow-service` owns money, `bank-service` is the only place funds physically sit. No service reaches across that line.

The full design reasoning behind these decisions lives in [`/docs`](docs/) as complete design documents, including the trade-offs we considered and rejected.

---

## Build status — what's actually built vs. designed

We're keeping this explicit on purpose.

| Phase | Scope | Status |
|---|---|---|
| **Phase 1 — MVP** | 6 services, full contract → escrow → settlement flow | **Built & runnable** (`docker compose up`) |
| **Phase 2 — Production** | +7 services (bank, inspection, reputation, file, pricing, audit, analytics) | **Designed in depth**, partial implementation — see [`/docs`](docs/) design specs |

Phase 2 services are documented as detailed design specs (domain models, event contracts, idempotency strategy, failure handling) before coding — the design docs are part of the deliverable.

---

## The problem it solves

Vietnam's agricultural exports hit **$70.09B in 2025 (+12% YoY)**, yet B2B procurement still runs on verbal agreements with no enforcement:

1. **Breaking a contract is often the rational move (*bẻ kèo*).** Contracts are signed 3–6 months before harvest. In 2024, Robusta coffee swung from VND 60M to 135M/ton — a 125% move that made breaking the contract more profitable than any penalty clause. Only ~30% of Vietnam's agricultural supply linkages reach a "stable" enforcement level.
2. **Perishable goods can't wait for courts.** Vietnamese commercial disputes take 1–3 years; VIAC logged a record 475 disputes in 2024.
3. **Cooperatives have no tools to protect themselves.** 90% of Mekong Delta rice moves through intermediaries; 57% of Vietnamese SMEs can't access formal financing for lack of verifiable transaction history.

AgriContract adds escrow, milestone-based settlement, tiered dispute resolution, and an immutable audit trail at exactly the layer where these failures happen — **the contract layer**. It intentionally does *not* do logistics, marketplace, or accounting.

---

## Architecture

![Architecture](docs/diagrams/AgriContract_Diagram_Architecture.svg)

### Phase 1 — MVP (built, 6 services)

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | JWT validation (Keycloak), routing, `X-Internal-Secret` injection |
| user-service | 8081 | User profiles, organization data, KYC status |
| product-service | 8082 | Commodity listings |
| contract-service | 8083 | Contract lifecycle state machine, Outbox Poller |
| escrow-service | 8084 | Escrow lock/release/penalty (Phase 1: mock DB balance) |
| notification-service | 8085 | Email notifications via MailHog |

### Phase 2 — Production (designed, +7 services)

| Service | Port | New Capability |
|---|---|---|
| bank-service | 8086 | Legal fund custody (FBO/ledger model); idempotent instruction handling |
| inspection-service | 8087 | INSPECTOR role; immutable evidence records with SHA-256 hash |
| reputation-service | 8088 | Event-driven read model; cross-contract reputation aggregation |
| file-service | 8089 | MinIO binary storage; inspection report files |
| pricing-service | 8091 | Redis-cached external commodity price feeds |
| audit-service | 8092 | Append-only event store; EUDR compliance export (PDF/CSV) |
| analytics-service | 8093 | Time-series aggregation; platform analytics |

> Listing search is handled as filter parameters inside `product-service` rather than a standalone service — port `8090` is intentionally unused.

### Infrastructure

| Service | Purpose |
|---|---|
| Keycloak | Authentication & RBAC (`BUYER`, `SELLER`, `ADMIN`, `INSPECTOR`) |
| RabbitMQ | Async events — Choreography Saga + Transactional Outbox |
| MySQL × 5 | One isolated DB per Phase 1 service |
| MailHog | Dev email catcher |
| MinIO / Redis | Phase 2: file storage / cache, rate limiting, pub-sub |

---

## Key engineering decisions

Short version of the reasoning that recruiters and interviewers usually ask about. Full write-ups in [`/docs`](docs/).

### 1. Append-only ledger, not account-per-contract

**Naive approach:** one balance row per contract, updated on every lock/release. This has two problems — a provisioning race (the account must exist before you can lock into it, but the trigger to create it comes *after* the moment you need the lock), and it doesn't match how real custody works.

**What's built instead:** a single pooled custody balance (FBO/omnibus — the same model PayPal, e-wallets, and real-estate escrow use) plus an **append-only `LedgerEntry`** table. Every lock/release/seize/refund is one immutable row. **Balances are never stored — they're always derived** from `SUM(amount)` filtered by contract/milestone/user/type. Nothing to keep in sync, nothing to corrupt in place.

### 2. Idempotency key = source event ID, not a business key

RabbitMQ is at-least-once, so the same "lock these funds" instruction can arrive twice (retry, consumer restart mid-processing). Without a guard, funds get locked twice.

The tempting key is `(contractId, milestoneId, entryType)` — but that same pair passes through *different* entry types over its lifetime (`LOCK` then later `RELEASE`), so it isn't unique per message. The key used is the **outbox message's own `sourceEventId`** (`UNIQUE` in the DB). It answers the right question — "have I processed *this request*?" — not "has this *kind of action* happened?" On a duplicate, the entry isn't re-inserted, but the confirmation event **is** re-published, because the redelivery might mean the *original confirmation* was lost, not that the request was resent.

### 3. Wait for confirmation, never fire-and-forget

`escrow-service` is the only actor that talks to `bank-service`. It publishes `bank.lock_requested` and **waits** for `bank.lock_completed` before setting state to `LOCKED` — it never optimistically sets state and hopes the bank succeeds. Firing and setting state immediately is a dual-write: if the bank fails, the two services silently disagree until a human notices.

### 4. External reference price only — never an internal average

An obvious "market price" feature would average `agreedPrice` across settled contracts. Rejected: on a new platform with sparse data it's statistically meaningless, and worse, a large buyer could deliberately underprice a few early deals to manufacture a fake "reference price," then use that number to squeeze cooperatives — the exact power asymmetry the platform exists to fix. Pricing instead ingests **external** reference prices (government VNSAT feed for coffee/rice, admin entry for rubber/cashew), cached with cache-aside (MySQL = source of truth, Redis = self-healing cache, no hard TTL).

---

## Security model

Phase 2 layers tamper-evidence onto the audit trail:

1. **Contract content hash** — SHA-256 of `ContractTerms` at signing; every later state transition re-verifies it. DB tampering ⇒ hash mismatch ⇒ operation rejected.
2. **Audit hash chain** — each record carries `previousHash` + `recordHash`; the audit DB user has `INSERT`/`SELECT` only. Verified on every export.
3. **Inspection report hash** — reports hashed (content + timestamp + inspectorId) at submission; immutable after.
4. **Multi-location hash storage** — hashes stored independently in `contract-service`, `audit-service`, and the signed PDF held by both parties. An attacker must compromise all three at once.
5. **Email timestamp anchor** — content hashes emailed to both parties, so evidence survives even a full DB compromise.

> Hash chains detect tampering, digital signatures prevent repudiation, email timestamps provide an external anchor — covering the attack surface blockchain addresses, at a complexity appropriate for a trusted-operator deployment.

---

## Tech stack

- **Java 21** / Spring Boot 3.3
- **Spring Cloud Gateway** — API gateway with JWT validation
- **Spring Security** — Keycloak RS256 JWT
- **Spring Data JPA** + **Flyway** — per-service MySQL schema migrations
- **Spring AMQP / RabbitMQ** — Choreography Saga + Transactional Outbox
- **OpenFeign** — synchronous inter-service calls
- **Keycloak 24** — identity provider, RBAC
- **Docker** + Docker Compose — local orchestration
- **Phase 2:** Kubernetes (deploy & self-heal), MinIO (file storage), Redis (cache / rate limiting / pub-sub), Zipkin (distributed tracing across service hops)

---

## Getting started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) + [Docker Compose](https://docs.docker.com/compose/install/) — no local Java or Maven needed.

### 1. Clone and configure

```bash
git clone https://github.com/RedAvocado22/AgriContract.git
cd AgriContract
cp .env.example .env   # defaults work out of the box for local dev
```

### 2. Start all services

```bash
docker compose up --build
```

First build downloads dependencies and compiles all services (~5–10 min). Later builds are ~30s per service.

### 3. Set up Keycloak

Once Keycloak is healthy at http://localhost:8180:

1. Log in with `admin` / `admin` (or your `.env` values).
2. **Realm settings → Import realm**.
3. Upload `infra/keycloak/agricontract-realm.json` — creates the `agricontract` realm with pre-configured clients and roles.

### 4. Access the services

| URL | Service |
|---|---|
| http://localhost:8080 | API Gateway |
| http://localhost:8180 | Keycloak admin console |
| http://localhost:15672 | RabbitMQ management (`guest`/`guest`) |
| http://localhost:8025 | MailHog — outgoing emails |
| http://localhost:8083/swagger-ui.html | contract-service API docs |
| http://localhost:8084/swagger-ui.html | escrow-service API docs |

---

## Domain flow (Phase 1 — two-phase lock)

| Step | Action | Contract State | Escrow State |
|---|---|---|---|
| 1 | Seller posts listing (after Admin KYC) | — | — |
| 2 | Buyer submits offer | OFFERED | — |
| 3 | Parties negotiate; all changes recorded | NEGOTIATING | — |
| 4 | Both parties sign | SIGNED | — |
| 5 | Buyer locks payment; Seller locks deposit | ACTIVE | LOCKED |
| 6 | Seller delivers | ACTIVE | LOCKED |
| 7 | Buyer confirms receipt | DELIVERED | LOCKED |
| 8 | Escrow auto-releases to Seller | SETTLED | RELEASED |

> **Phase 2 replaces this single-delivery flow with milestone escrow:** the contract stays `ACTIVE` across N delivery batches (no single `DELIVERED` state), each milestone locking/releasing its own amount, with an INSPECTOR-backed tiered dispute path and force-majeure handling per batch. Seller deposit becomes optional (negotiated per contract). See [`/docs`](docs/) for the full milestone state machine.

**Cancellation & penalty** are executed automatically from the signed `ContractTerms` (deposit forfeit, full refund, or dispute routing), grounded in Civil Code 2015 Art. 328 and Commercial Law 2005 Art. 300–302 — the platform *enforces an agreement*, it does not *issue a ruling*.

---

## Business & legal context

The platform sells to **industry associations** (VICOFA, VRA, VINACAS) and large procurement companies — not to cooperatives directly — which solves both the sales problem (small HTX don't buy software) and the trust problem (HTX trust the association that deployed it, and in Phase 2, trust the bank holding the funds).

It's positioned against the **EUDR 2026** compliance deadline (deforestation-free audit trail for EU coffee/rubber exports) and Vietnam's agricultural digitalization policy. The platform holds **no real money** — Phase 1 uses mock balances, and Phase 2's `bank-service` is designed around a real bank's custody model (FBO/ledger) but stays mock-backed within the capstone scope, behind a clean interface so the mock can later be swapped for a real bank API (Agribank/BIDV) without changing business logic. This keeps it outside payment-service licensing requirements (Decree 52/2024, Art. 8.7).

Full market analysis, competitive gap, and legal framework: [`/docs`](docs/).
