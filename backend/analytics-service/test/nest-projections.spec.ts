/**
 * The projections are registered through Nest providers (leader decision 19/10/2026): a real Nest
 * application context builds the handler list from the repository providers.
 */
import 'reflect-metadata';
import assert from 'node:assert/strict';
import { NestFactory } from '@nestjs/core';
import type { ProjectionHandler } from '../src/application/port';
import { ProjectionsModule } from '../src/infrastructure/nest/projections.module';
import { PROJECTIONS } from '../src/infrastructure/nest/tokens';
import { deepEqual } from './deepEqual';

describe('ProjectionsModule (Nest DI)', () => {
  it('provides the four #133 projections', async () => {
    const context = await NestFactory.createApplicationContext(ProjectionsModule, { logger: false });
    try {
      const projections = context.get<ProjectionHandler<unknown>[]>(PROJECTIONS);
      deepEqual(projections.map((p) => p.eventType).sort(), [
        'contract.signed',
        'milestone.cancelled_with_penalty',
        'milestone.force_majeure_resolved',
        'milestone.settled',
      ]);
    } finally {
      await context.close();
    }
  });
});
