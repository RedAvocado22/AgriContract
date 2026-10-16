import { Module } from '@nestjs/common';
import { M0SpikeConsumer } from './infrastructure/messaging/m0-spike.consumer';

@Module({
  providers: [M0SpikeConsumer],
})
export class AppModule {}
