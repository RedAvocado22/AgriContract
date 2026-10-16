import net from 'node:net';
import { afterEach, describe, expect, it } from 'vitest';
import { OTP_TIME_BUDGET } from '../../src/application/otpTimeBudget.js';
import { EmailDeliveryError } from '../../src/application/ports.js';
import {
  OTP_SMTP_TIMEOUTS,
  SmtpEmailSender,
  type SmtpTimeouts,
} from '../../src/infrastructure/email/smtpEmailSender.js';

const MESSAGE = {
  to: 'signer@test.local',
  subject: 'Subject',
  text: 'code 482913',
  html: '<p>code 482913</p>',
};

// socketTimeout is long so the total deadline, not inactivity, ends the stalled cases.
const FAST: SmtpTimeouts = {
  connectionTimeout: 300,
  greetingTimeout: 300,
  socketTimeout: 5000,
  totalTimeout: 800,
  dataResponseReserve: 300,
};

type Stage = 'greeting' | 'EHLO' | 'MAIL' | 'RCPT' | 'DATA' | 'dot' | 'QUIT';

interface Script {
  /** Withhold the reply to this stage until release(). */
  hold?: Stage;
  /** Drop the connection instead of replying to this stage. */
  close?: Stage;
  replies?: Partial<Record<Stage, string>>;
}

const DEFAULT_REPLIES: Record<Stage, string> = {
  greeting: '220 fake ESMTP',
  EHLO: '250 fake',
  MAIL: '250 Ok',
  RCPT: '250 Ok',
  DATA: '354 End data with <CR><LF>.<CR><LF>',
  dot: '250 Ok: queued as fake-queue-id@fake',
  QUIT: '221 Bye',
};

/** Just enough SMTP for SMTPConnection, with replies the test can hold or drop. */
class ScriptedSmtp {
  port = 0;
  connections = 0;
  /** Bytes received after replying 354, i.e. message content. */
  bodyBytes = 0;
  /** Messages received complete, terminating "." included. */
  readonly messages: string[] = [];
  readonly commands: string[] = [];
  private readonly sockets = new Set<net.Socket>();
  private readonly reachedStages = new Map<Stage, () => void>();
  private readonly reachedPromises = new Map<Stage, Promise<void>>();
  private releaseHeld!: () => void;
  private readonly held = new Promise<void>((resolve) => {
    this.releaseHeld = resolve;
  });
  private readonly server: net.Server;

  constructor(private readonly script: Script = {}) {
    for (const stage of Object.keys(DEFAULT_REPLIES) as Stage[]) {
      this.reachedPromises.set(
        stage,
        new Promise<void>((resolve) => {
          this.reachedStages.set(stage, resolve);
        }),
      );
    }
    this.server = net.createServer((socket) => {
      this.handle(socket);
    });
  }

  async listen(): Promise<this> {
    await new Promise<void>((resolve) => this.server.listen(0, '127.0.0.1', resolve));
    this.port = (this.server.address() as net.AddressInfo).port;
    return this;
  }

  /** Resolves when the server is about to reply to stage. */
  reached(stage: Stage): Promise<void> {
    return this.reachedPromises.get(stage) ?? Promise.resolve();
  }

  release(): void {
    this.releaseHeld();
  }

  /** Resolves once every client connection is closed. */
  async allClosed(): Promise<void> {
    await Promise.all(
      [...this.sockets].map(
        (socket) =>
          new Promise<void>((resolve) => {
            if (socket.destroyed) resolve();
            else
              socket.once('close', () => {
                resolve();
              });
          }),
      ),
    );
  }

  async stop(): Promise<void> {
    this.release();
    for (const socket of this.sockets) socket.destroy();
    await new Promise<void>((resolve) => {
      this.server.close(() => {
        resolve();
      });
    });
  }

  private handle(socket: net.Socket): void {
    this.connections += 1;
    this.sockets.add(socket);
    socket.on('close', () => this.sockets.delete(socket));
    socket.on('error', () => undefined);

    let chain = Promise.resolve();
    const respond = (stage: Stage): void => {
      chain = chain.then(async () => {
        this.reachedStages.get(stage)?.();
        if (this.script.close === stage) {
          socket.destroy();
          return;
        }
        if (this.script.hold === stage) {
          await this.held;
        }
        if (!socket.destroyed && socket.writable) {
          socket.write(`${this.script.replies?.[stage] ?? DEFAULT_REPLIES[stage]}\r\n`);
        }
      });
    };

    let inData = false;
    let data = '';
    let buffer = '';
    respond('greeting');
    socket.on('data', (chunk: Buffer) => {
      if (inData) this.bodyBytes += chunk.length;
      buffer += chunk.toString('utf8');
      let index: number;
      while ((index = buffer.indexOf('\r\n')) >= 0) {
        const line = buffer.slice(0, index);
        buffer = buffer.slice(index + 2);
        if (inData) {
          if (line === '.') {
            inData = false;
            this.messages.push(data);
            data = '';
            respond('dot');
          } else {
            data += `${line}\n`;
          }
          continue;
        }
        const command = line.slice(0, 4).toUpperCase();
        this.commands.push(command);
        if (command === 'EHLO' || command === 'HELO') respond('EHLO');
        else if (command === 'MAIL') respond('MAIL');
        else if (command === 'RCPT') respond('RCPT');
        else if (command === 'DATA') {
          respond('DATA');
          // Content only starts after our 354; counting from here keeps bodyBytes honest.
          chain = chain.then(() => {
            inData = true;
          });
        } else if (command === 'QUIT') {
          respond('QUIT');
          chain = chain.then(() => {
            socket.end();
          });
        } else socket.write('502 Not implemented\r\n');
      }
    });
  }
}

async function closedPort(): Promise<number> {
  const server = net.createServer();
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as net.AddressInfo;
  await new Promise<void>((resolve) => {
    server.close(() => {
      resolve();
    });
  });
  return port;
}

function waitUntil(epochMs: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, Math.max(0, epochMs - Date.now())));
}

function sender(port: number, timeouts: SmtpTimeouts = FAST): SmtpEmailSender {
  return new SmtpEmailSender({ host: '127.0.0.1', port, from: 'noreply@test.local' }, timeouts);
}

function inFuture(ms: number): { deadlineAt: number } {
  return { deadlineAt: Date.now() + ms };
}

async function failure(promise: Promise<unknown>): Promise<EmailDeliveryError> {
  const error: unknown = await promise.then(
    () => undefined,
    (rejected: unknown) => rejected,
  );
  expect(error).toBeInstanceOf(EmailDeliveryError);
  return error as EmailDeliveryError;
}

describe('SmtpEmailSender', () => {
  let server: ScriptedSmtp | undefined;

  async function start(script?: Script): Promise<ScriptedSmtp> {
    server = await new ScriptedSmtp(script).listen();
    return server;
  }

  afterEach(async () => {
    await server?.stop();
    server = undefined;
  });

  it('keeps its defaults inside the OTP time budget', () => {
    expect(OTP_SMTP_TIMEOUTS.totalTimeout).toBe(OTP_TIME_BUDGET.smtpBudgetMs);
    expect(OTP_SMTP_TIMEOUTS.connectionTimeout).toBeLessThan(OTP_SMTP_TIMEOUTS.totalTimeout);
    expect(OTP_SMTP_TIMEOUTS.dataResponseReserve).toBeLessThan(OTP_SMTP_TIMEOUTS.totalTimeout);
  });

  it('returns the provider queue id and closes the connection after QUIT', async () => {
    const smtp = await start();

    const result = await sender(smtp.port).send(MESSAGE, inFuture(5000));
    await smtp.allClosed();

    expect(result.providerMessageId).toBe('fake-queue-id@fake');
    expect(smtp.messages).toHaveLength(1);
    expect(smtp.messages[0]).toContain('To: signer@test.local');
    expect(smtp.commands).toContain('QUIT');
  });

  describe('before the message body is written: NOT_SENT', () => {
    it('fails with a connection error when nothing listens', async () => {
      const port = await closedPort();

      const error = await failure(sender(port).send(MESSAGE, inFuture(5000)));

      // Nodemailer reports a refused connection as ESOCKET or ECONNECTION depending on platform.
      expect(error.reason).toMatch(/^SMTP_E(SOCKET|CONNECTION)$/);
      expect(error.certainty).toBe('NOT_SENT');
    });

    it('does not connect when the deadline leaves less than the DATA reply reserve', async () => {
      const smtp = await start();

      const error = await failure(
        sender(smtp.port).send(MESSAGE, inFuture(FAST.dataResponseReserve - 1)),
      );

      expect(error.reason).toBe('SMTP_DEADLINE_BEFORE_CONNECT');
      expect(smtp.connections).toBe(0);
    });

    it('handles a server that closes silently before the greeting', async () => {
      const smtp = await start({ close: 'greeting' });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error).toMatchObject({ reason: 'SMTP_ECONNECTION', certainty: 'NOT_SENT' });
    });

    it('times out a greeting that never comes', async () => {
      const smtp = await start({ hold: 'greeting' });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error).toMatchObject({ reason: 'SMTP_ETIMEDOUT', certainty: 'NOT_SENT' });
    });

    it('does not start the envelope when a late greeting leaves too little time', async () => {
      const slowGreeting: SmtpTimeouts = {
        ...FAST,
        connectionTimeout: 2000,
        greetingTimeout: 2000,
      };
      const smtp = await start({ hold: 'greeting' });
      const startedAt = Date.now();

      const sending = failure(sender(smtp.port, slowGreeting).send(MESSAGE, inFuture(5000)));
      await smtp.reached('greeting');
      await waitUntil(startedAt + FAST.totalTimeout - FAST.dataResponseReserve + 100);
      smtp.release();
      const error = await sending;
      await smtp.allClosed();

      expect(error).toMatchObject({
        reason: 'SMTP_DEADLINE_BEFORE_ENVELOPE',
        certainty: 'NOT_SENT',
      });
      expect(smtp.commands).not.toContain('MAIL');
    });

    it('stops at the deadline while waiting for the RCPT reply, before DATA', async () => {
      const smtp = await start({ hold: 'RCPT' });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error).toMatchObject({ reason: 'SMTP_DEADLINE_EXCEEDED', certainty: 'NOT_SENT' });
      expect(smtp.commands).not.toContain('DATA');
      expect(smtp.bodyBytes).toBe(0);
    });

    it('stops at the deadline while waiting for 354, without writing the body', async () => {
      const smtp = await start({ hold: 'DATA' });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error).toMatchObject({ reason: 'SMTP_DEADLINE_EXCEEDED', certainty: 'NOT_SENT' });
      expect(smtp.commands).toContain('DATA');
      expect(smtp.bodyBytes).toBe(0);
      expect(smtp.messages).toHaveLength(0);
    });

    it('withholds the body when 354 arrives with less than the reply reserve left', async () => {
      const smtp = await start({ hold: 'DATA' });
      const startedAt = Date.now();

      const sending = failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.reached('DATA');
      // Inside the deadline but past the point where the reply reserve still fits.
      await waitUntil(startedAt + FAST.totalTimeout - FAST.dataResponseReserve + 100);
      smtp.release();
      const error = await sending;
      await smtp.allClosed();

      expect(error).toMatchObject({
        reason: 'SMTP_DEADLINE_BEFORE_DATA_BODY',
        certainty: 'NOT_SENT',
      });
      expect(smtp.bodyBytes).toBe(0);
      expect(smtp.messages).toHaveLength(0);
    });

    it('honours a caller deadline earlier than totalTimeout', async () => {
      const smtp = await start({ hold: 'RCPT' });
      const startedAt = Date.now();

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(500)));

      expect(error.reason).toBe('SMTP_DEADLINE_EXCEEDED');
      expect(Date.now() - startedAt).toBeLessThan(FAST.totalTimeout);
    });

    it('reports a rejected recipient by code and response code only', async () => {
      const smtp = await start({ replies: { RCPT: '550 No such user signer@test.local' } });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error).toMatchObject({ reason: 'SMTP_EENVELOPE_550', certainty: 'NOT_SENT' });
      expect(smtp.bodyBytes).toBe(0);
    });
  });

  describe('after the message body is written', () => {
    it('is NOT_SENT when the server rejects the message', async () => {
      const smtp = await start({ replies: { dot: '554 5.7.1 Message rejected' } });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error).toMatchObject({ reason: 'SMTP_EMESSAGE_554', certainty: 'NOT_SENT' });
    });

    it('is UNKNOWN when the deadline passes before the reply to the final dot', async () => {
      const smtp = await start({ hold: 'dot' });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error).toMatchObject({
        reason: 'SMTP_DEADLINE_EXCEEDED_AFTER_DATA',
        certainty: 'UNKNOWN',
      });
      // The server holds the complete message: it may well deliver it.
      expect(smtp.messages).toHaveLength(1);
    });

    it('is UNKNOWN when the server drops the connection instead of replying', async () => {
      const smtp = await start({ close: 'dot' });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      await smtp.allClosed();

      expect(error.reason).toMatch(/_AFTER_DATA$/);
      expect(error.certainty).toBe('UNKNOWN');
      expect(smtp.messages).toHaveLength(1);
    });

    it('ignores a late 250 that arrives after the deadline, without errors', async () => {
      const smtp = await start({ hold: 'dot' });

      const error = await failure(sender(smtp.port).send(MESSAGE, inFuture(5000)));
      // The server now accepts: the send already reported UNKNOWN, which is the honest answer.
      smtp.release();
      await smtp.allClosed();

      expect(error.certainty).toBe('UNKNOWN');
      expect(smtp.messages).toHaveLength(1);
    });
  });
});
