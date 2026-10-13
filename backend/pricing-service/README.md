# pricing-service (Node)

Advisory coffee reference prices for AgriContract Phase 2 (`docs/phase_2/design/pricing-service-phase2-design.md`).
This increment implements the **Admin/Operator manual fallback** (design §3.2, issue #117).

Pricing is **advisory only** (design §4.1). contract-service and escrow-service never call it; a test in
`test/contract.test.ts` fails if either service gains a pricing client, URL or route.

## API

`POST /api/v1/prices/manual` (frozen as `recordManualPrice` in `contracts/events/supplementary-services-api.yaml`)

| Item | Rule |
|---|---|
| Auth | Through the API gateway only (`X-Gateway-Secret`, `X-User-Id`, `X-User-Role`), same as the Java services. Roles `ADMIN` or `OPERATOR` (data-governance §5.4). Others get `403 PRICE_MANUAL_FORBIDDEN`. |
| `Idempotency-Key` | Required. Same key + same body returns the original `201` (header `Idempotent-Replayed: true`). Same key + different body returns `409 IDEMPOTENCY_KEY_STATE_CONFLICT`. Keys are scoped per user. |
| Body | `commodity` (`COFFEE` only), `itemName`, `province`, `price` (VND/kg, string with 2 decimals and no leading zeros, e.g. `"120500.00"` or `"0.50"`; `"00100.00"` is rejected because MySQL would return it as `"100.00"` and a replay would differ), `capturedAt` (date the price applies, `YYYY-MM-DD`, not in the future, Vietnam time), `referenceSource`, `reason`. Unknown fields are rejected. |
| Storage | One new row in `price_history` with `source = ADMIN_MANUAL`, `captured_at`, `ingested_at` (UTC), `reference_source`, `reason`, `recorded_by`. Append-only: older prices are never updated or deleted. |
| Cache | After the MySQL insert, Redis key `price:coffee:{province}:{itemSlug}` is refreshed (no TTL) unless it already holds a price for a later date. A replay refreshes it again, so a cache write that failed on the first attempt is repaired by the retry. A Redis failure does not fail the request. |
| Redis down | Redis is optional: startup never waits for it (background connect + reconnect), commands fail at once while it is disconnected (no offline queue), and each cache update gives up after `REDIS_COMMAND_TIMEOUT_MS`. The request still returns `201` from MySQL. |
| Errors | Shared error envelope (`timestamp, status, error, code, message, path, traceId`). |

Example:

```json
POST /api/v1/prices/manual
Idempotency-Key: 6f1c2f0e-2b51-4a7c-9a55-0e1f7f6f3a10

{
  "commodity": "COFFEE",
  "itemName": "Cà phê Robusta nhân xô",
  "province": "Đắk Lắk",
  "price": "120500.00",
  "capturedAt": "2026-10-10",
  "referenceSource": "Bản tin giá Sở NN&MT Đắk Lắk 10/10/2026",
  "reason": "VNSAT không cập nhật quá 14 ngày"
}
```

`GET /health` returns `{"status":"UP"}`.

Not in this increment: public reads `GET /api/v1/prices/{commodity}/{province}[/items]` and the VNSAT scraping job.
The API gateway route to this service is not added here (gateway is owned separately).

## Layout

Same canonical layout as the Java services (`domain` / `application` / `infrastructure`), checked by `test/architecture.test.ts`:

```
src/
  domain/                 PriceQuote, manual price validation, PriceHistoryRepository port (no framework code)
  application/
    port.ts               PriceCache, Logger, Actor
    errors.ts             IdempotencyKeyRequired, ValidationFailed, IdempotencyConflict (no HTTP status)
    usecase/recordManualPrice.ts
  infrastructure/
    config.ts
    persistence/          MySQL repository, migration runner
    cache/                Redis cache adapter
    http/                 server, gateway auth, error envelope + application error -> HTTP mapping
  main.ts                 composition root
```

Dependency rule: `domain` imports nothing else, `application` imports only `domain`, and HTTP statuses exist only in `infrastructure/http`.

## Run

```bash
npm install
npm run build
npm run migrate      # applies migrations/V*.sql once each (table schema_migrations)
npm start            # port 8091
```

Environment (same names as the Java services): `GATEWAY_INTERNAL_SECRET` (required), `SERVER_PORT` (8091),
`DB_HOST`, `DB_PORT`, `DB_NAME` (`pricing_db`), `DB_USERNAME`, `DB_PASSWORD`, `REDIS_URL` (optional; cache disabled if unset),
`REDIS_CONNECT_TIMEOUT_MS` (2000), `REDIS_COMMAND_TIMEOUT_MS` (500, deadline for one cache update).

## Test

```bash
npm run typecheck
npm test
```

Unit tests, HTTP tests through a real in-process server, contract tests against the repo's frozen YAML
contracts, and a boundary test proving the contract flow has no synchronous dependency on pricing.
`test/redis.test.ts` drives the real node-redis client against a stopped and a frozen Redis (no Redis
install needed). `test/replay.test.ts` checks that a replay read back from MySQL equals the first response;
to also run it against a real MySQL (the database must exist, migrations are applied by the test):

```bash
PRICING_TEST_MYSQL_URL=mysql://root:root@localhost:3306/pricing_test npm test
```
