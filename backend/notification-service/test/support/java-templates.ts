/**
 * Extracts the email templates from the Java ProcessNotificationUseCaseImpl
 * so tests can compare the Nest output against the Java source, character by
 * character (plan §8, "Subject/body lệch chữ").
 *
 * Each `handleX(XCommand command)` method is expected to declare
 * `String subject = <expr>;` and `String body = <expr>;`, where <expr> joins
 * string literals and `command.field()` calls with `+`, and then call
 * `sendNotification(command.eventId(), <recipient>, subject, body)` once per
 * recipient. Anything else throws, so a change in Java is noticed, not guessed.
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

export const JAVA_SOURCE = resolve(
  __dirname,
  '../../../notification-service/src/main/java/com/agricontract/notification/application/usecase/ProcessNotificationUseCaseImpl.java',
);

export const FIXTURE = resolve(__dirname, '../fixtures/java-templates.json');

export function readFixture(): JavaTemplates {
  return JSON.parse(readFileSync(FIXTURE, 'utf8')) as JavaTemplates;
}

export function extractJavaTemplates(source: string): JavaTemplates {
  const templates: JavaTemplates = {};
  const header = /public void (handle\w+)\(\w+ command\)\s*\{/g;
  for (let m = header.exec(source); m; m = header.exec(source)) {
    const name = m[1];
    const block = methodBody(source, header.lastIndex - 1);
    const subject = parseExpression(block, 'subject');
    if (subject.some((p) => 'field' in p)) {
      throw new Error(`${name}: subject is expected to be a plain literal`);
    }
    templates[name] = {
      subject: subject.map((p) => ('text' in p ? p.text : '')).join(''),
      body: parseExpression(block, 'body'),
      recipients: parseRecipients(block, name),
    };
  }
  if (Object.keys(templates).length === 0) {
    throw new Error('No handleX(command) method found in the Java source');
  }
  return templates;
}

/** Returns the text between the brace at `open` and its matching closing brace. */
function methodBody(source: string, open: number): string {
  let depth = 0;
  for (let i = open; i < source.length; i += 1) {
    const ch = source[i];
    if (ch === '"') {
      i = readLiteral(source, i).end - 1;
    } else if (ch === '{') {
      depth += 1;
    } else if (ch === '}') {
      depth -= 1;
      if (depth === 0) return source.slice(open + 1, i);
    }
  }
  throw new Error('Unbalanced braces in the Java source');
}

function parseExpression(block: string, variable: string): TemplatePart[] {
  const start = block.search(new RegExp(`String ${variable}\\s*=`));
  if (start < 0) throw new Error(`No "String ${variable} =" declaration`);
  let i = block.indexOf('=', start) + 1;
  const parts: TemplatePart[] = [];
  let expectOperand = true;
  for (;;) {
    while (/\s/.test(block[i] ?? '')) i += 1;
    const ch = block[i];
    if (ch === undefined) throw new Error(`Unterminated ${variable}`);
    if (expectOperand) {
      if (ch === '"') {
        const literal = readLiteral(block, i);
        parts.push({ text: literal.value });
        i = literal.end;
      } else {
        const call = /^command\.(\w+)\(\)/.exec(block.slice(i));
        if (!call) {
          throw new Error(
            `${variable}: unexpected operand at "${block.slice(i, i + 30)}"`,
          );
        }
        parts.push({ field: call[1] });
        i += call[0].length;
      }
      expectOperand = false;
    } else if (ch === '+') {
      i += 1;
      expectOperand = true;
    } else if (ch === ';') {
      return mergeText(parts);
    } else {
      throw new Error(
        `${variable}: unexpected "${block.slice(i, i + 30)}" after an operand`,
      );
    }
  }
}

function mergeText(parts: TemplatePart[]): TemplatePart[] {
  const merged: TemplatePart[] = [];
  for (const part of parts) {
    const last = merged[merged.length - 1];
    if ('text' in part && last && 'text' in last) last.text += part.text;
    else merged.push({ ...part });
  }
  return merged;
}

function parseRecipients(block: string, name: string): string[] {
  const calls = [...block.matchAll(/sendNotification\(([^;]*)\);/g)];
  if (calls.length === 0) throw new Error(`${name}: no sendNotification call`);
  return calls.map(([, args]) => {
    const m =
      /^\s*command\.eventId\(\)\s*,\s*(adminEmail|command\.(\w+)\(\))\s*,\s*subject\s*,\s*body\s*$/.exec(
        args,
      );
    if (!m) throw new Error(`${name}: unexpected sendNotification(${args})`);
    return m[2] ?? 'adminEmail';
  });
}

const ESCAPES: Record<string, string> = {
  '"': '"',
  "'": "'",
  '\\': '\\',
  n: '\n',
  t: '\t',
  r: '\r',
  b: '\b',
  f: '\f',
  s: ' ',
};

/** Reads the Java string literal starting at the quote at `start`. */
function readLiteral(
  source: string,
  start: number,
): { value: string; end: number } {
  let value = '';
  for (let i = start + 1; i < source.length; i += 1) {
    const ch = source[i];
    if (ch === '"') return { value, end: i + 1 };
    if (ch === '\n') break;
    if (ch !== '\\') {
      value += ch;
      continue;
    }
    const next = source[i + 1];
    if (next === 'u') {
      value += String.fromCharCode(parseInt(source.slice(i + 2, i + 6), 16));
      i += 5;
    } else if (next !== undefined && next in ESCAPES) {
      value += ESCAPES[next];
      i += 1;
    } else {
      throw new Error(`Unsupported escape \\${next} in a Java literal`);
    }
  }
  throw new Error('Unterminated Java string literal');
}
