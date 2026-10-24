import { NestFactory } from '@nestjs/core';
import type { AddressInfo } from 'node:net';
import { PricingModule, type AppDeps } from '../src/infrastructure/nest/pricing.module';
import { FakeCache, GATEWAY_SECRET, InMemoryPriceHistory, memoryLogger, type LogLine } from './fakes';

/** The service under test: the real controllers, guard and error filter on a Nest app, with in-memory adapters. */
export interface TestApp {
  baseUrl: string;
  repo: InMemoryPriceHistory;
  cache: FakeCache;
  logs: LogLine[];
  close(): Promise<void>;
}

export async function startApp(overrides: Partial<AppDeps> = {}): Promise<TestApp> {
  const repo = new InMemoryPriceHistory();
  const cache = new FakeCache(repo.events);
  const logs: LogLine[] = [];
  let seq = 0;
  const app = await NestFactory.create(
    PricingModule.forDeps({
      repository: repo,
      cache,
      logger: memoryLogger(logs),
      gatewaySecret: GATEWAY_SECRET,
      now: () => new Date('2026-10-12T05:00:00.000Z'), // 12:00 ICT
      newId: () => `00000000-0000-4000-8000-${String(++seq).padStart(12, '0')}`,
      ...overrides,
    }),
    { bodyParser: false, logger: false },
  );
  await app.listen(0, '127.0.0.1');
  const { port } = app.getHttpServer().address() as AddressInfo;
  return {
    baseUrl: `http://127.0.0.1:${port}`,
    repo,
    cache,
    logs,
    close: () => app.close(),
  };
}

