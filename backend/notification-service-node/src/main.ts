import { loadConfig } from './config.js';
import { buildApp } from './infrastructure/http/app.js';
import { connectRabbitMq, openChannel } from './infrastructure/messaging/connection.js';
import { createPool, pingDatabase } from './infrastructure/persistence/database.js';

async function main(): Promise<void> {
  const config = loadConfig();
  const pool = createPool(config.db);
  const connection = connectRabbitMq(config.rabbitmq);
  const channel = openChannel(connection);

  const app = buildApp({
    database: () => pingDatabase(pool),
    rabbitmq: () => connection.isConnected(),
  });

  const shutdown = async (): Promise<void> => {
    app.log.info('shutdown started');
    await app.close();
    await channel.close();
    await connection.close();
    await pool.end();
    app.log.info('shutdown complete');
  };

  let stopping: Promise<void> | undefined;
  process.on('SIGTERM', () => {
    stopping ??= shutdown().then(
      () => process.exit(0),
      (error: unknown) => {
        app.log.error({ err: error }, 'shutdown failed');
        process.exit(1);
      },
    );
  });

  await app.listen({ port: config.serverPort, host: '0.0.0.0' });
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
