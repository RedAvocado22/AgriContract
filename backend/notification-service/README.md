# notification-service (NestJS)

Migrated from the Spring Boot `notification-service`; see `docs/notification-service-nestjs-migration.md`
(done, M5).

**Status: M5 (cutover done).** The service consumes the 9 Phase 1 queues, sends the same emails as the Java service
did and records them in `notification_logs`, with the same retry, dead-lettering and per-recipient deduplication.
Parity with Java was signed off in M4 (`docs/notification-service-parity-m4.md`). The Java service is
removed; tag `notification-java-final` is the rollback point (plan §7).

| Queue (`notification-svc.<key>`) | Recipients, in order |
| --- | --- |
| `contract.signed`, `contract.cancelled` | buyer, seller |
| `contract.delivered` | seller |
| `contract.disputed` | `ADMIN_EMAIL`, seller, buyer |
| `escrow.locked` | seller |
| `escrow.released` | seller, buyer |
| `escrow.penalized` | buyer if `penalizedParty` is `BUYER`, otherwise seller |
| `category.approved`, `category.rejected` | proposer |

- A payload that is not JSON, or misses a required field, goes to the DLQ at once (see
  `src/infrastructure/messaging/legacy-payload.parsers.ts` for the required fields).
- Each email (event + recipient) is claimed atomically in `notification_logs` before sending, so two deliveries
  of the same event never both send it. A delivery that finds a live claim (younger than 120s) is retried
  instead of acked; a `PENDING` claim older than that (sender died mid-send) can be taken over.
- A failed send is retried 3 times (1s, 2s), then dead-lettered; the log row is `FAILED` with `retry_count`.
- Logs carry the event id and a failure code (e.g. `EENVELOPE/550`), never a recipient address or payload text.
- Every 5 minutes a warning is logged for each DLQ that holds messages.
- On shutdown the consumers are cancelled and in-flight messages finish (up to 8s) before the channel and the
  database pool close.

### OTP email endpoint (Phase 2, #114)

`POST /internal/v1/notifications/otp-email` (`golden-flow-api.yaml` `sendOtpEmail`, design §2.2): contract-service
sends the contract-signing OTP synchronously. Service-to-service only: the caller sends `SERVICE_INTERNAL_SECRET` in
`X-Internal-Secret`, and the gateway never routes `/internal/**`.

| Response | When |
| --- | --- |
| `200`, empty body | The mail server accepted the email, now or for an earlier call with the same `requestId` |
| `400 VALIDATION_ERROR` | Body is not JSON or does not match `OtpEmailRequest`; the message names fields, never values |
| `401 UNAUTHORIZED` | Missing or wrong `X-Internal-Secret` |
| `502 OTP_EMAIL_PROVIDER_FAILED` | The mail server did not accept it; nothing is retried later |
| `503 OTP_EMAIL_IN_PROGRESS` | Another call with the same `requestId` is sending it right now |
| `503 NOTIFICATION_LOG_UNAVAILABLE` | `notification_logs` could not be written; nothing was sent |

Errors use the shared envelope (`timestamp, status, error, code, message, path, traceId`).

- **Idempotent by `requestId`** (the caller's `otpId`): each call is claimed in `notification_logs` with
  `event_id = requestId` before sending. A repeated call after `SENT` returns 200 without a second email; after
  `FAILED` it sends again (the caller asked); while another call holds the claim it returns 503 at once.
- **The OTP is never stored or logged.** `body` holds a placeholder; logs carry the `requestId` and a failure code.
  The code is the only line of its own in the email, and the subject does not contain it.
- **Short SMTP timeouts** (1s connect, 1s greeting, 2.5s inactivity) so a failure comes back before the caller's
  read timeout of at least 5s (#167).
- Stricter than the contract, which only says `string`: `requestId` is at most 36 characters of `[A-Za-z0-9._:-]`
  (it is stored in `event_id`, `VARCHAR(36)`, and logged); `recipientUserId` and `contractId` are 1–255 characters.
  `test/contract/otp-email-request.contract.spec.ts` checks the fields and verdicts against the contract file.
- Until the V2 migration (#115) these rows have no `notification_type`; V2 backfills them like any Phase 1 row.

**Known limitations.** The SMTP inactivity timeout restarts whenever the server answers, so a server that answers
slowly can take longer than 5s; and a mail the server accepted just as the caller gave up is still delivered. Both
can make a user see "not sent" for a code that arrives. The caller treats any non-200 as "not sent" and lets the
user request a new code, which supersedes the old one.

Bruno `notification-service/20`–`25` and `npm run test:int` (`otp-email.int-spec.ts`) check it against MailHog.

The table `notification_logs` comes from `db/migration/V1__create_notification_logs.sql`, a byte-for-byte copy of
the Java service's Flyway V1 (same checksum). The app does not run migrations; the compose service
`notification-migrate` (`flyway/flyway:10.10.0`) does, before the app starts.

> **Never run this service and the Java `notification-service` at the same time.** They use the same queues and the
> same database; two consumers on one queue split the messages between them. Stop the Java container first.

## Environment

Same names and defaults as the Java service (`application.yml`), plus `RABBITMQ_PORT` and `MAIL_FROM`. A missing
variable takes its default. An empty value, or a port that is not an integer from 1 to 65535, stops the service at
startup with a list of every invalid variable. `.env` files are not read.

| Variable | Default |
| --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3311` / `notification_db` |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `password` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` |
| `MAIL_FROM` | `noreply@agricontract.com` |
| `ADMIN_EMAIL` | `admin@agricontract.com` |
| `SERVER_PORT` | `8085` |
| `SERVICE_INTERNAL_SECRET` | `changeme-internal` (same default as contract-service; compose requires it in `.env`) |

## Run with compose

From `backend/`. compose needs the two secrets set to any value, even for these services (on PowerShell, set
`$env:GATEWAY_INTERNAL_SECRET` and `$env:SERVICE_INTERNAL_SECRET` first).

```bash
GATEWAY_INTERNAL_SECRET=local-dev SERVICE_INTERNAL_SECRET=local-dev \
  docker compose up -d --build notification-migrate notification-service mailhog
docker compose ps notification-service     # becomes (healthy) once /actuator/health returns 200
```

`notification-migrate` runs Flyway once and exits; `notification-service` starts only if it succeeded, and
`docker compose logs notification-migrate` shows why it did not. Rerunning it is safe: an up-to-date schema is a
no-op.

- **First run: the `notification_db_data` volume must be empty, or come from the Java service** (which recorded V1
  in `flyway_schema_history`). A volume that has `notification_logs` without `flyway_schema_history` makes Flyway
  stop with `Found non-empty schema(s) ... but no schema history table`. Remove that volume
  (`docker compose rm -sf notification-db && docker volume rm backend_notification_db_data`), which deletes its
  data.
- The image is built from `notification-service/Dockerfile` with build context `backend/`, so
  `shared/node-eventing` is built inside the image; nothing needs to be built on the host.
- **Do not run it next to the Java service.** If you start the Java image by hand (for the M4 parity run), stop
  this one first: `docker compose stop notification-service`.

## Run locally

Requires Node >= 22.12 and a built `@agricontract/eventing`. The database schema must already exist (run
`notification-migrate` once, see above).

```bash
# 1. Infrastructure from the backend compose (run in backend/).
#    compose needs these two secrets set to any value, even for these services.
GATEWAY_INTERNAL_SECRET=local-dev SERVICE_INTERNAL_SECRET=local-dev \
  docker compose up -d rabbitmq notification-db mailhog
docker compose stop notification-service   # the compose container would consume the same queues

# 2. Shared eventing package, then this service.
cd shared/node-eventing && npm ci && npm run build
cd ../../notification-service && npm ci
npm run start            # defaults match the ports compose exposes on localhost
```

On Windows PowerShell set the two variables first (`$env:GATEWAY_INTERNAL_SECRET='local-dev'`, same for
`SERVICE_INTERNAL_SECRET`), then run the same commands.

Startup fails if MySQL is unreachable. RabbitMQ may be down: the service starts anyway, retries in the background
(1s, 2s, 4s ... up to 30s) and declares the topology once connected; `/actuator/health` returns 503 meanwhile.

```bash
curl -i http://localhost:8085/actuator/health
# 200 {"status":"UP","components":{"db":{"status":"UP"},"rabbit":{"status":"UP"}}}
```

Unlike Spring Boot's actuator, health does not check SMTP.

## Scripts

| Script | Use |
| --- | --- |
| `npm run build` | Compile to `dist/` |
| `npm run start` / `start:dev` | Run (watch mode for `start:dev`) |
| `npm test` | Unit tests (Jest) |
| `npm run lint` | ESLint + Prettier, fixing what it can |
| `npm run lint:check` | Same without `--fix`, for CI |
| `npm run test:int` | Integration tests against the compose MySQL (`DB_*` env, table `notification_logs` must exist) and, for the OTP endpoint, MailHog (`MAIL_HOST`/`MAIL_PORT`, API at `MAILHOG_API_URL`, default `http://localhost:8025`) |

## Golden email fixtures

Subjects, bodies and recipient order must stay identical to the Java service. `test/fixtures/java-templates.json`
holds the templates of the Java `ProcessNotificationUseCaseImpl`, frozen from the Java service at tag
`notification-java-final`; `src/application/use-cases.spec.ts` checks every use case against it. The Java source
is gone, so the fixture is not regenerated: change it only together with an intended change to an email.

## Code layout

`src/application` is plain TypeScript (no Nest imports): one `Notify<Event>UseCase` per queue with
`execute(command)`, the shared `NotificationSender`, the ports and their errors. `NotificationModule` builds these
classes with factories from the `EMAIL_PORT`, `NOTIFICATION_LOG_REPOSITORY` and `NOTIFICATION_SETTINGS` tokens;
`src/infrastructure` holds the Nest/amqplib/mysql2/nodemailer adapters.

The OTP endpoint follows the same split: `src/application/otp` (request validation, template,
`SendOtpEmailUseCase`), `src/infrastructure/http` (controller, `X-Internal-Secret` guard, error envelope filter,
body reader), wired by `OtpEmailModule` with its own short-timeout `OTP_EMAIL_PORT`. The app runs without Nest's
body parser (`src/app.options.ts`), so a malformed body never ends up in an error message or a log line.

Bruno uses `service_internal_secret: local-dev` from `bruno/environments/local.bru`, matching the compose examples
above; override it with `--env-var service_internal_secret=...` if your `.env` differs.

## Why Jest has its own tsconfig

`@agricontract/eventing` is ESM-only. At runtime the CommonJS app loads it through Node's `require(esm)`
(Node >= 22.12). Jest uses its own module loader, does not support that on Node 22, and treats every `.js` file in a
`"type": "module"` package as ESM before transforming it. So in Jest only:

- `moduleNameMapper` points `@agricontract/eventing` at the package's TypeScript source, and strips the `.js`
  suffix from its relative imports;
- `tsconfig.spec.json` compiles specs and that source to CommonJS (`module: commonjs`).

Tests therefore run against `shared/node-eventing/src`, the app against its built `dist`.
`src/infrastructure/eventing.smoke.spec.ts` fails if this setup breaks.
