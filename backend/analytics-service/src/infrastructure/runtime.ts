import type { AmqpChannelLike, Logger, ProcessedEventStore, TransactionRunner } from '@agricontract/eventing';
import type { IngestedEventStore, ProjectionHandler } from '../application/port.js';
import { IngestEvent } from '../application/usecase/ingestEvent.js';
import { startIngestion } from './messaging/consumers.js';
import { DlqMonitor, type QueueProbe } from './messaging/dlqMonitor.js';
import { declareTopology, type TopologyChannel } from './messaging/topology.js';

export interface RuntimeDeps<Tx> {
  /** Channel with prefetch already set: declares the topology and consumes. */
  consumeChannel: AmqpChannelLike & TopologyChannel;
  /** Separate channel for DLQ depth checks. */
  monitorChannel: QueueProbe;
  transactions: TransactionRunner<Tx>;
  processedEvents: ProcessedEventStore<Tx>;
  store: IngestedEventStore<Tx>;
  projections: readonly ProjectionHandler<Tx>[];
  logger: Logger;
  dlqMonitorIntervalMs: number;
  maxAttempts?: number;
  sleep?: (ms: number) => Promise<void>;
}

export interface AnalyticsRuntime {
  stopConsuming(): Promise<void>;
  awaitIdle(timeoutMs: number): Promise<boolean>;
  stopMonitor(): void;
  inFlightCount(): number;
}

/**
 * Production wiring of the messaging side, shared by main.ts and the tests so what is tested is what runs:
 * topology -> ingest use case -> one consumer per subscribed event type -> DLQ monitor.
 */
export async function startAnalytics<Tx>(deps: RuntimeDeps<Tx>): Promise<AnalyticsRuntime> {
  await declareTopology(deps.consumeChannel);
  const ingest = new IngestEvent<Tx>(deps.store, deps.projections);
  const ingestion = await startIngestion<Tx>({
    channel: deps.consumeChannel,
    transactions: deps.transactions,
    processedEvents: deps.processedEvents,
    ingest,
    logger: deps.logger,
    ...(deps.maxAttempts !== undefined ? { maxAttempts: deps.maxAttempts } : {}),
    ...(deps.sleep ? { sleep: deps.sleep } : {}),
  });
  const monitor = new DlqMonitor(deps.monitorChannel, { intervalMs: deps.dlqMonitorIntervalMs, logger: deps.logger });
  monitor.start();
  return {
    stopConsuming: () => ingestion.stopConsuming(),
    awaitIdle: (timeoutMs) => ingestion.inFlight.idle(timeoutMs),
    stopMonitor: () => monitor.stop(),
    inFlightCount: () => ingestion.inFlight.count,
  };
}
