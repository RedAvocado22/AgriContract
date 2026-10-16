import { Logger } from '@nestjs/common';
import {
  consumeEvents,
  handleDelivery,
  NonRetryableError,
  type AmqpChannelLike,
} from '@agricontract/eventing';
import { M0SpikeMessageConsumer } from './m0-spike.consumer';

/** Records ack/nack like an amqplib channel that stays open. */
function recordingChannel(): AmqpChannelLike & { acks: number; nacks: number } {
  const channel = {
    acks: 0,
    nacks: 0,
    consume: () => Promise.resolve({ consumerTag: 'ctag' }),
    ack: () => void (channel.acks += 1),
    nack: () => void (channel.nacks += 1),
  };
  return channel;
}

describe('@agricontract/eventing under Jest (M0 spike)', () => {
  it('loads the ESM package at runtime, not only its types', () => {
    expect(typeof consumeEvents).toBe('function');
    expect(new NonRetryableError('x')).toBeInstanceOf(Error);
  });

  it('acks a flat (non-envelope) message through handleDelivery', async () => {
    const channel = recordingChannel();
    const consumer = new M0SpikeMessageConsumer(new Logger('test'));
    jest.spyOn(Logger.prototype, 'log').mockImplementation(() => undefined);

    const result = await handleDelivery(
      { content: Buffer.from('{"eventId":"evt-1"}'), fields: {} },
      { channel, queue: 'q', consumer },
    );

    expect(result).toBe('ACK_PROCESSED');
    expect(channel.acks).toBe(1);
    expect(channel.nacks).toBe(0);
  });
});
