/** Money is carried as decimal strings with exactly two decimals ("60000000.00"); never as floating point. */

function toCents(amount: string): bigint {
  const match = /^(\d+)\.(\d{2})$/.exec(amount);
  if (!match) throw new Error('Not a money amount with two decimals');
  return BigInt(match[1]! + match[2]!);
}

function fromCents(cents: bigint): string {
  const negative = cents < 0n;
  const digits = (negative ? -cents : cents).toString().padStart(3, '0');
  return `${negative ? '-' : ''}${digits.slice(0, -2)}.${digits.slice(-2)}`;
}

/** a - b, exact. The result may be negative (the escrow paid out more than it had locked). */
export function subtractMoney(a: string, b: string): string {
  return fromCents(toCents(a) - toCents(b));
}
