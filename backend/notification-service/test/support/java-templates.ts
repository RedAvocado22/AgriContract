/**
 * Reads test/fixtures/java-templates.json: the email templates of the Java
 * ProcessNotificationUseCaseImpl, frozen from the Java service at tag
 * notification-java-final, so tests can compare the Nest output against them
 * character by character (plan §8, "Subject/body lệch chữ").
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export type TemplatePart = { text: string } | { field: string };

export interface JavaTemplate {
  subject: string;
  body: TemplatePart[];
  /** In send order: a command field name, or "adminEmail" for the configured admin. */
  recipients: string[];
}

export type JavaTemplates = Record<string, JavaTemplate>;

export const FIXTURE = resolve(__dirname, '../fixtures/java-templates.json');

export function readFixture(): JavaTemplates {
  return JSON.parse(readFileSync(FIXTURE, 'utf8')) as JavaTemplates;
}
