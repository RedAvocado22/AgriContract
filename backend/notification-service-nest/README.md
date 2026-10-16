# notification-service (NestJS)

NestJS port of the Spring Boot `notification-service`. Plan and status:
`docs/planning/notification-service-nestjs-migration.md`.

**Status: M1 (infrastructure).** The service validates its environment, connects to MySQL and RabbitMQ, declares
the same RabbitMQ topology as the Java service and exposes `GET /actuator/health`. It does **not** consume any
message or send any email yet (M2).

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

## Run locally

Requires Node >= 22.12 and a built `@agricontract/eventing`.

```bash
# 1. Infrastructure from the backend compose (run in backend/).
#    compose needs these two secrets set to any value, even for these services.
GATEWAY_INTERNAL_SECRET=local-dev SERVICE_INTERNAL_SECRET=local-dev \
  docker compose up -d rabbitmq notification-db mailhog
docker compose stop notification-service   # if the Java service is running

# 2. Shared eventing package, then this service.
cd shared/node-eventing && npm ci && npm run build
cd ../../notification-service-nest && npm ci
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

## Why Jest has its own tsconfig

`@agricontract/eventing` is ESM-only. At runtime the CommonJS app loads it through Node's `require(esm)`
(Node >= 22.12). Jest uses its own module loader, does not support that on Node 22, and treats every `.js` file in a
`"type": "module"` package as ESM before transforming it. So in Jest only:

- `moduleNameMapper` points `@agricontract/eventing` at the package's TypeScript source, and strips the `.js`
  suffix from its relative imports;
- `tsconfig.spec.json` compiles specs and that source to CommonJS (`module: commonjs`).

Tests therefore run against `shared/node-eventing/src`, the app against its built `dist`.
`src/infrastructure/eventing.smoke.spec.ts` fails if this setup breaks.
