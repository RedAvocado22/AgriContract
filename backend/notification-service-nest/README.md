# notification-service (NestJS)

NestJS port of the Spring Boot `notification-service`. Plan and status:
`docs/planning/notification-service-nestjs-migration.md`. Currently at **M0 (spike)**: the only consumer is
`notification-svc.m0-spike`, which logs and acks.

## Develop

Requires Node >= 22.12 and a built `@agricontract/eventing`:

```bash
cd ../shared/node-eventing && npm ci && npm run build
cd ../../notification-service-nest && npm ci
npm run start        # RabbitMQ from env RABBITMQ_* (default guest/guest@localhost:5672), HTTP on SERVER_PORT (8085)
npm test
```

## Why Jest has its own tsconfig

`@agricontract/eventing` is ESM-only. At runtime the CommonJS app loads it through Node's `require(esm)`
(Node >= 22.12). Jest uses its own module loader, does not support that on Node 22, and treats every `.js` file in a
`"type": "module"` package as ESM before transforming it. So in Jest only:

- `moduleNameMapper` points `@agricontract/eventing` at the package's TypeScript source, and strips the `.js`
  suffix from its relative imports;
- `tsconfig.spec.json` compiles specs and that source to CommonJS (`module: commonjs`).

Tests therefore run against `shared/node-eventing/src`, the app against its built `dist`.
