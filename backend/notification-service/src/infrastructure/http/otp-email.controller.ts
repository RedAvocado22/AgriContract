import {
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  UseFilters,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { parseOtpEmailRequest } from '../../application/otp/otp-email-request';
import { SendOtpEmailUseCase } from '../../application/otp/send-otp-email.use-case';
import { ErrorEnvelopeFilter } from './error-envelope.filter';
import { InternalSecretGuard } from './internal-secret.guard';
import { readJsonBody } from './json-body';

/**
 * POST /internal/v1/notifications/otp-email (golden-flow-api.yaml sendOtpEmail,
 * design §2.2): contract-service sends the signing OTP synchronously.
 *
 * - 200, empty body: the mail server accepted the email, now or for an
 *   earlier call with the same requestId;
 * - 400 VALIDATION_ERROR, 401 UNAUTHORIZED;
 * - 502 OTP_EMAIL_PROVIDER_FAILED: not accepted, nothing is retried later;
 * - 503 OTP_EMAIL_IN_PROGRESS / NOTIFICATION_LOG_UNAVAILABLE: nothing sent
 *   by this call.
 *
 * Not routed by the API gateway (/internal/** is never routed).
 */
@Controller('internal/v1/notifications')
@UseGuards(InternalSecretGuard)
@UseFilters(ErrorEnvelopeFilter)
export class OtpEmailController {
  constructor(private readonly sendOtpEmail: SendOtpEmailUseCase) {}

  @Post('otp-email')
  @HttpCode(HttpStatus.OK)
  async send(@Req() req: Request): Promise<void> {
    const request = parseOtpEmailRequest(await readJsonBody(req));
    await this.sendOtpEmail.execute(request);
  }
}
