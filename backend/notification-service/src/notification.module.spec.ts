import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { validate } from './config/env.validation';
import { NotificationSender } from './application/notification-sender';
import { NOTIFICATION_SETTINGS } from './application/notification-settings';
import { NotifyContractDisputedUseCase } from './application/use-cases';
import { MYSQL_POOL } from './infrastructure/persistence/mysql.provider';
import { AMQP_CONNECT } from './infrastructure/messaging/amqp.provider';
import {
  LEGACY_ROUTES,
  type LegacyRoutes,
} from './infrastructure/messaging/legacy-routes';
import { queueNames } from './infrastructure/messaging/topology';
import { NotificationModule } from './notification.module';

describe('NotificationModule wiring', () => {
  it('builds the plain application classes from tokens and routes all 9 queues', async () => {
    // compile() resolves the providers without running lifecycle hooks, so no broker or DB is touched.
    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          ignoreEnvFile: true,
          validate: () => validate({ ADMIN_EMAIL: 'ops@example.test' }),
        }),
        NotificationModule,
      ],
    })
      .overrideProvider(MYSQL_POOL)
      .useValue({})
      .overrideProvider(AMQP_CONNECT)
      .useValue(() => Promise.reject(new Error('not used')))
      .compile();

    expect(moduleRef.get(NotificationSender)).toBeInstanceOf(
      NotificationSender,
    );
    expect(moduleRef.get(NotifyContractDisputedUseCase)).toBeInstanceOf(
      NotifyContractDisputedUseCase,
    );
    expect(moduleRef.get(NOTIFICATION_SETTINGS)).toEqual({
      adminEmail: 'ops@example.test',
    });
    const routes = moduleRef.get<LegacyRoutes>(LEGACY_ROUTES);
    expect(
      Object.keys(routes)
        .map((key) => `notification-svc.${key}`)
        .sort(),
    ).toEqual(queueNames().sort());
  });
});
