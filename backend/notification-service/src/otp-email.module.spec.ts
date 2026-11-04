import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { SendOtpEmailUseCase } from './application/otp/send-otp-email.use-case';
import { OTP_EMAIL_PORT } from './application/ports/email.port';
import { validate } from './config/env.validation';
import {
  CREATE_MAIL_TRANSPORT,
  NodemailerEmailAdapter,
} from './infrastructure/email/nodemailer-email.adapter';
import { OtpEmailController } from './infrastructure/http/otp-email.controller';
import { MYSQL_POOL } from './infrastructure/persistence/mysql.provider';
import { OtpEmailModule } from './otp-email.module';

describe('OtpEmailModule wiring', () => {
  it('builds the controller and use case with an OTP mail transport of short timeouts', async () => {
    const create = jest.fn(() => ({ sendMail: jest.fn() }));
    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          ignoreEnvFile: true,
          validate: () => validate({}),
        }),
        OtpEmailModule,
      ],
    })
      .overrideProvider(MYSQL_POOL)
      .useValue({})
      .overrideProvider(CREATE_MAIL_TRANSPORT)
      .useValue(create)
      .compile();

    expect(moduleRef.get(OtpEmailController)).toBeInstanceOf(
      OtpEmailController,
    );
    expect(moduleRef.get(SendOtpEmailUseCase)).toBeInstanceOf(
      SendOtpEmailUseCase,
    );
    expect(moduleRef.get(OTP_EMAIL_PORT)).toBeInstanceOf(
      NodemailerEmailAdapter,
    );
    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({ socketTimeout: 2_500 }),
    );
  });
});
