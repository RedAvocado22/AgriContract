import { Module } from '@nestjs/common';
import { connect } from 'amqplib';
import { AMQP_CONNECT, AmqpService } from './amqp.provider';

@Module({
  providers: [{ provide: AMQP_CONNECT, useValue: connect }, AmqpService],
  exports: [AmqpService],
})
export class MessagingModule {}
