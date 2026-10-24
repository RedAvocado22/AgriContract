import { Module } from '@nestjs/common';
import { PricingModule } from './pricing.module';

@Module({ imports: [PricingModule.forRoot()] })
export class AppModule {}
