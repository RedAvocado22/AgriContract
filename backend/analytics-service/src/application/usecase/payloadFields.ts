import type { IngestEventCommand } from '../port';

/** Reads a string field of a payload that the messaging adapter has already validated against the contract. */
export function stringField(command: IngestEventCommand, name: string): string {
  const value = command.payload[name];
  if (typeof value !== 'string') throw new Error(`${command.eventType}: payload.${name} is not a string`);
  return value;
}

/** Reads a field the contract declares as string-or-null. */
export function nullableStringField(command: IngestEventCommand, name: string): string | null {
  const value = command.payload[name];
  if (value === null) return null;
  if (typeof value !== 'string') throw new Error(`${command.eventType}: payload.${name} is not a string or null`);
  return value;
}
