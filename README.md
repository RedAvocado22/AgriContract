# AgriContract

> B2B agricultural contract platform with escrow for Vietnamese commodity markets.

AgriContract digitizes the procurement contract lifecycle between cooperatives (HTX) and trading companies — adding a self-enforcing escrow layer that aligns both parties' incentives before a transaction happens, not after a dispute occurs.

**Scope is intentionally limited to the contract layer.** AgriContract does not handle logistics, is not an e-commerce marketplace, and does not replace enterprise accounting systems. It solves one specific problem: the absence of a self-enforcing mechanism in B2B forward contracts for agricultural commodities.

---

## The Problem

Vietnam's agricultural exports reached **$70.09 billion USD in 2025** (+12% YoY), yet B2B procurement still relies on verbal agreements with no enforcement mechanism. Three structural failures make this dangerous:

**1. Contract breaking is a rational economic decision (bẻ kèo)**

Contracts are signed 3–6 months before harvest. In 2024, Robusta coffee prices surged from VND 60M to VND 135M per ton within months — a 125% swing that made contract-breaking more profitable than any penalty clause. The result:

> *"A large proportion of suppliers did not deliver, leaving exporters struggling."*
> — Phan Minh Thong, Chairman, Phuc Sinh Group

> *"We had to cut our plan from 125,000 tons to 105,000 tons to control capital risk."*
> — Le Duc Huy, CEO, XNK 2/9 Dak Lak Company

Only ~30% of agricultural supply linkages in Vietnam achieve a "stable" level of enforcement. The rest are informal agreements with no binding mechanism (Agricultural Value Chain Forum, 8/2024).

**2. Perishable goods cannot wait for courts**

Vietnamese commercial courts take 1–3 years. VIAC recorded 475 commercial disputes in 2024 — a record high since its founding — with goods trading as the largest category (25%). Every day of delay in dispute resolution is unrecoverable loss for perishable commodities.

**3. Cooperatives have no tools to protect themselves**

90% of Mekong Delta rice is sold through intermediaries. Small HTX have no market information, no legal resources, and no credit history — 57% of Vietnamese SMEs cannot access formal financing (We-Fi/OCB, 2022), primarily due to lack of collateral and verifiable transaction history.

---

## Why Now

Three external forces have converged to create an unprecedented market window:

**EUDR 2026 — Hard legal deadline**

EU Regulation 2023/1115 (amended by 2025/2650) requires all coffee, rubber, timber, and cocoa shipments entering the EU to carry deforestation-free proof with an audit trail traceable to GPS coordinates of each plot. Deadlines: **30/12/2026** for large and medium enterprises; **30/6/2027** for small enterprises.

Vietnam is classified "low-risk" in EUDR benchmarking — a competitive advantage that can only be realized if exporters can prove a complete audit trail at the procurement layer from HTX. AgriContract addresses exactly this layer.

**$70B export scale demands standardization**

At this scale, standardizing procurement processes is no longer optional — it is a condition for maintaining international market access. BIDV and Techcombank have already joined the Agrichain blockchain platform (Exabyte) as data validators, signaling that financial institutions are moving from capital providers to data-layer participants in agricultural supply chains.

**National digitalization policy**

Decision 749/QD-TTg (2020) designates agriculture as one of eight national digitalization priorities, targeting 50% of agricultural enterprises adopting digital technology by 2025. Actual adoption reached only ~30% — a 20-percentage-point gap that represents the addressable market.

---

## Market Gap

| Solution | Capability | Gap |
|---|---|---|
| Traditional paper contracts | Legal validity under Civil Code 2015 | No escrow; no digital audit trail; no self-enforcing penalty |
| Agrichain (Exabyte) | Blockchain traceability; BIDV + Techcombank integrated | No contract negotiation; no escrow; no penalty mechanism |
| Kamereo | B2B procurement for restaurants and retail; $7.8M Series B | Not a forward contract platform; does not serve HTX |
| Alibaba Trade Assurance | B2B escrow; 160M+ orders protected; 37M buyers | No Vietnamese localization; no EUDR workflow; not accessible to HTX |
| Koina (closed 2024) | Farm-to-business supply chain; VinaCapital-backed; >$1M raised | No contract layer; no escrow; full-stack model burned capital before revenue |
| **AgriContract** | **Contract lifecycle + Escrow + Audit trail + Dispute resolution** | Does not handle logistics — intentional scope limit |

No existing solution in Vietnam addresses the contract layer simultaneously with escrow, dispute resolution, and EUDR-ready audit trail at the HTX/SME level. Koina's failure (wrong target segment: individual farmers instead of associations; no revenue model) validates the approach, not the market.

---

## Solution

AgriContract adds a **contract + escrow layer** to B2B commodity forward contracts:

1. Seller lists verified commodity offering after Admin confirms legal standing
2. Buyer submits offer; both parties negotiate terms — every change timestamped in immutable audit trail
3. Both parties sign digital contract — legally equivalent to paper under Law on Electronic Transactions 2023, Article 34
4. Buyer locks payment in escrow; Seller locks deposit — before any delivery begins
5. Seller delivers; Buyer confirms receipt (Phase 2: based on INSPECTOR report)
6. Escrow releases automatically to Seller; or penalty logic executes on cancellation
7. Both parties rate each other — reputation accumulates as a verifiable asset

Every action writes to an append-only audit trail exportable as PDF/CSV — EUDR Due Diligence Statement on demand.

### Transaction Flow

| Step | Action | Contract State | Escrow State |
|---|---|---|---|
| 1 | Seller posts listing (after Admin KYC) | — | — |
| 2 | Buyer submits offer | OFFERED | — |
| 3 | Parties negotiate; all changes recorded | NEGOTIATING | — |
| 4 | Both parties sign digital contract | SIGNED | — |
| 5 | Buyer locks payment; Seller locks deposit | ACTIVE | LOCKED |
| 6 | Seller delivers goods | ACTIVE | LOCKED |
| 7 | Buyer confirms receipt (Phase 2: INSPECTOR report) | DELIVERED | LOCKED |
| 8 | Escrow auto-releases to Seller | SETTLED | RELEASED |

### Cancellation & Penalty

| Scenario | Escrow Outcome | Legal Basis |
|---|---|---|
| Cancel before signing | No escrow; no financial obligation | Civil Code 2015, Art. 403 |
| Buyer cancels after escrow active | Buyer forfeits deposit per penalty rate in ContractTerms | Civil Code 2015, Art. 328; Commercial Law 2005, Art. 300 |
| Seller cancels after escrow active | Full refund to Buyer + Seller absorbs loss | Commercial Law 2005, Art. 302 |
| Dispute after delivery | Tiered resolution → INSPECTOR report → Admin executes | Decree 98/2018, Art. 15 |

---

## Five User Tiers

The platform has five legally distinct user categories. Conflating them leads to misunderstanding the revenue model, trust mechanism, and legal risk profile.

| Tier | Who | Role |
|---|---|---|
| 1 — Software Buyer | Industry associations (VICOFA, VRA, VINACAS) or large procurement companies (Intimex, Phuc Sinh Group) | Purchases software license/subscription; deploys platform for their member network; designates Admin |
| 2 — Platform Buyer | Commodity procurement companies, export conglomerates | Creates offers; locks payment in escrow; confirms receipt |
| 3 — Platform Seller | Agricultural cooperatives (HTX), linked farming households | Posts listings; negotiates; signs contract; delivers; receives payment |
| 4 — INSPECTOR | State-recognized inspection body: Vinacontrol, Quatest, SGS, Bureau Veritas | Independent quality and quantity verification at both delivery points; submits tamper-proof report |
| 5 — Escrow Holder | Agribank or BIDV (Phase 2) | Holds actual funds; executes lock/release/penalty instructions from platform — platform never holds money |

> **Why sell to associations, not cooperatives directly?**
> Small HTX don't buy software. VICOFA or a large procurement company deploys the platform; their members use it. This also resolves the trust problem: HTX don't need to trust an unknown startup — they trust VICOFA, who deployed it. When Agribank holds the funds in Phase 2, trust is entirely independent of the platform's brand.

---

## Architecture

![Architecture](docs/diagrams/AgriContract_Diagram_Architecture.svg)

### Phase 1 — MVP (5 services)

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | JWT validation (Keycloak), routing, X-Internal-Secret injection |
| user-service | 8081 | User profiles, organization data, KYC status |
| product-service | 8082 | Commodity listings |
| contract-service | 8083 | Contract lifecycle state machine, Outbox Poller |
| escrow-service | 8084 | Mock escrow lock/release/penalty (Phase 1: DB balance) |
| notification-service | 8085 | Email notifications via MailHog |

### Phase 2 — Production (13 services, +8)

| Service | Port | New Capability |
|---|---|---|
| bank-service | 8086 | Real Agribank/BIDV integration; idempotency with third-party APIs |
| inspection-service | 8087 | INSPECTOR role; immutable evidence records with SHA-256 hash |
| reputation-service | 8088 | Event-driven read model; cross-contract reputation aggregation |
| file-service | 8089 | MinIO binary storage; inspection report files |
| search-service | 8090 | CQRS read model; cross-service listing search |
| pricing-service | 8091 | Redis-cached commodity price feeds |
| audit-service | 8092 | Append-only event store; EUDR compliance export (PDF/CSV) |
| analytics-service | 8093 | Time-series aggregation; platform analytics |

### Infrastructure

| Service | Dev Port | Purpose |
|---|---|---|
| Keycloak | 8180 | Authentication & RBAC (roles: BUYER, SELLER, ADMIN, INSPECTOR) |
| RabbitMQ | 5672 / 15672 | Async events — Saga + Transactional Outbox pattern |
| MailHog | 8025 | Email catcher for dev (SMTP on 1025) |
| MySQL × 5 | 3307–3311 | One isolated DB per Phase 1 service |
| MinIO | — | Phase 2: inspection report file storage |
| Redis | — | Phase 2: rate limiting, token blacklist, pub-sub |

### Phase 2 Infrastructure Evolution

- Docker Compose → Kubernetes (replicas: 3)
- `@Scheduled` Outbox Poller → Debezium CDC
- X-Internal-Secret header → mTLS between services
- MailHog → SendGrid
- Nginx → Cloudflare WAF
- Zipkin + Spring Cloud Sleuth distributed tracing
- ELK log aggregation
- Prometheus + Grafana metrics

---

## Security Model

**Phase 2 adds five security layers to the audit trail:**

1. **Contract content hash** — SHA-256 of full ContractTerms stored at signing. Every subsequent state transition verifies hash before proceeding. DB tampering = hash mismatch = operation rejected.

2. **Audit trail hash chain** — each AuditRecord contains `previousHash` + `recordHash`. The audit-service DB user has INSERT + SELECT only — no UPDATE/DELETE. Chain integrity is verified on every EUDR export.

3. **Inspection report hash** — INSPECTOR-submitted reports are hashed (content + timestamp + inspectorId) at submission. contract-service verifies hash before advancing contract state. Immutable after submit.

4. **Multi-location hash storage** — `signedContentHash` and `reportHash` are stored independently in contract-service DB, audit-service DB, and the PDF sent to both parties at signing. An attacker must compromise all three simultaneously.

5. **Email timestamp anchor** — notification-service emails the content hash to both parties after each signing and after each inspection report submission. Email history is an external anchor independent of the platform — even if the entire DB is compromised, both parties hold hash evidence in their inbox.

> Hash chain detects tampering. Digital signatures prevent repudiation. Email timestamps are an external anchor. These three layers cover the attack vectors that blockchain addresses, at a complexity appropriate for a trusted-operator context (industry association deployment).

---

## Phase 2 Dispute Resolution

Three levels — auto-routed by DisputeRoutingService based on contract value, commodity type, and EU export flag (configurable per deployment in `application.yml`):

| Level | Trigger | Process | Fee |
|---|---|---|---|
| 1 — Internal Admin | Small value AND standard commodity | Buyer uploads photo/video evidence → Admin rules within deadline | None |
| 1.5 — Local Inspector | Medium value OR needs quantity/quality confirmation | Vinacontrol, Quatest, or state-certified provincial inspection center | `inspectionFeeRate × contractValue` (e.g. 0.1–0.3%) |
| 2 — International Inspector | Large value OR complex commodity (specialty coffee, technical rubber, cashew for EU) | SGS, Bureau Veritas, Intertek | `inspectionFeeRate × contractValue` (e.g. 0.2–0.5%) |

Both parties deposit inspection fees into escrow before INSPECTOR assignment. Losing party covers full cost; 50/50 ruling splits the fee.

---

## Three-Tier Value Roadmap

| Tier | Value | Who Benefits | When |
|---|---|---|---|
| 1 — EUDR Compliance | Immutable contract audit trail = deforestation-free proof for EU audits. On-demand PDF/CSV export instead of manual aggregation. | Coffee, rubber, timber exporters to EU — legal deadline 30/12/2026 | Phase 1 |
| 2 — Financial Risk Control | Escrow + Milestone: cost of breaking contract exceeds short-term gain. Tiered Dispute + INSPECTOR: resolution in days not years. Reputation: social capital with real monetary value. | Both Buyer and Seller — especially HTX without legal resources | Phase 2 |
| 3 — Credit Infrastructure | Accumulated audit trail = verifiable transaction history. Agribank/BIDV can assess creditworthiness from platform data instead of requiring collateral. 57% of SMEs currently excluded from formal financing. | HTX + banks (as data oracle, not just lender) | Post Phase 2 |

> *Companies access the platform because of EUDR requirements, stay because of reduced capital and dispute risk, and cannot leave because their transaction history on the platform is a real asset.*

---

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) + [Docker Compose](https://docs.docker.com/compose/install/) — no local Java or Maven needed

### 1. Clone and configure

```bash
git clone https://github.com/RedAvocado22/AgriContract.git
cd AgriContract
cp .env.example .env
```

Edit `.env` to set passwords. Defaults work out of the box for local development.

### 2. Start all services

```bash
docker compose up --build
```

First build downloads Maven dependencies and compiles all services (~5–10 minutes). Subsequent builds with unchanged `pom.xml` take ~30 seconds per service.

### 3. Set up Keycloak

Once Keycloak is healthy at http://localhost:8180:

1. Log in with `admin` / `admin` (or your `.env` values)
2. Go to **Realm settings** → **Import realm**
3. Upload `infra/keycloak/agricontract-realm.json`

This creates the `agricontract` realm with pre-configured clients and roles (`BUYER`, `SELLER`, `ADMIN`).

### 4. Access the services

| URL | Service |
|---|---|
| http://localhost:8080 | API Gateway |
| http://localhost:8180 | Keycloak admin console |
| http://localhost:15672 | RabbitMQ management (guest/guest) |
| http://localhost:8025 | MailHog — outgoing emails |
| http://localhost:8081/swagger-ui.html | user-service API docs |
| http://localhost:8082/swagger-ui.html | product-service API docs |
| http://localhost:8083/swagger-ui.html | contract-service API docs |
| http://localhost:8084/swagger-ui.html | escrow-service API docs |

---

## Legal Framework

| Law | Relevant Provision | Application |
|---|---|---|
| Law on Electronic Transactions 2023 (No. 20/2023/QH15) | Art. 34: Electronic contracts cannot be denied legal validity; Art. 14.2: Evidentiary value based on reliability of creation and storage method | Contracts signed on AgriContract are legally equivalent to paper contracts. Audit trail is admissible as evidence and valid for EUDR audit |
| Decree 98/2018/ND-CP (agricultural linkage) | Art. 4: Linkage contracts must be in writing; Art. 15: Parties may choose dispute resolution method | AgriContract provides electronic documents satisfying Art. 4. Admin arbitration is voluntary mediation under Art. 15 |
| Law on Commercial Arbitration 2010 | Art. 5: Arbitration agreement before or after dispute is valid; arbitral award is final | Admin arbitration in Terms of Service is valid mediation. If escalation to formal arbitration is needed, VIAC can use platform audit trail as evidence |
| Civil Code 2015 + Commercial Law 2005 | Civil Code Art. 328: Escrow (ký quỹ) is a lawful obligation security; Commercial Law Art. 300–302: Penalty and compensation clauses | Escrow lock is lawful. Penalty auto-executes per signed ContractTerms — platform enforces an agreement, does not issue a ruling |

> **Note on payment licensing (Decree 52/2024, Art. 8.7):** Platform holds no real money in Phase 1 — mock balance only, no payment service license required. In Phase 2, Agribank/BIDV holds funds; the platform only sends instructions. This structure eliminates the licensing requirement entirely.

---

## Tech Stack

- **Java 21** / Spring Boot 3.3
- **Spring Cloud Gateway** — API gateway with JWT validation
- **Spring Security** — Keycloak RS256 JWT
- **Spring Data JPA** + Flyway — per-service MySQL schema migration
- **Spring AMQP** — RabbitMQ messaging (Choreography Saga + Transactional Outbox)
- **OpenFeign** — synchronous inter-service REST calls
- **Keycloak 24** — identity provider, RBAC
- **Docker** + Docker Compose — local orchestration
- **Phase 2:** Kubernetes, Debezium CDC, MinIO, Redis, Zipkin, ELK, Prometheus/Grafana
