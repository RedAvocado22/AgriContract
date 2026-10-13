import { createServer, type Server } from 'node:http';

/**
 * GET /health: 200 UP once ingestion has started, 503 STARTING while MySQL or RabbitMQ is still
 * being waited for. It listens before any dependency is contacted, so it never waits on them.
 */
export function createHealthServer(isReady: () => boolean): Server {
  return createServer((req, res) => {
    const path = (req.url ?? '/').split('?')[0];
    if (path !== '/health') {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'NOT_FOUND' }));
      return;
    }
    const ready = isReady();
    res.writeHead(ready ? 200 : 503, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: ready ? 'UP' : 'STARTING' }));
  });
}
