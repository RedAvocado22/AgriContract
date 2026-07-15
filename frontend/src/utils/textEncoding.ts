const CP1252_BYTES: Record<string, number> = {
  '€': 0x80,
  '‚': 0x82,
  'ƒ': 0x83,
  '„': 0x84,
  '…': 0x85,
  '†': 0x86,
  '‡': 0x87,
  'ˆ': 0x88,
  '‰': 0x89,
  'Š': 0x8a,
  '‹': 0x8b,
  'Œ': 0x8c,
  'Ž': 0x8e,
  '‘': 0x91,
  '’': 0x92,
  '“': 0x93,
  '”': 0x94,
  '•': 0x95,
  '–': 0x96,
  '—': 0x97,
  '˜': 0x98,
  '™': 0x99,
  'š': 0x9a,
  '›': 0x9b,
  'œ': 0x9c,
  'ž': 0x9e,
  'Ÿ': 0x9f,
}

const MOJIBAKE_PATTERN = /(?:Ã|Â|Ä|Æ|á[º»]|â[€†‡‰„œ]|�)/
const decoder = new TextDecoder('utf-8', { fatal: false })

export const repairMojibake = (value: string | null | undefined) => {
  if (!value || !MOJIBAKE_PATTERN.test(value)) {
    return value ?? ''
  }

  const bytes = Uint8Array.from(
    Array.from(value, (char) => CP1252_BYTES[char] ?? char.charCodeAt(0)),
  )
  const decoded = decoder.decode(bytes)

  return decoded.includes('�') ? value : decoded
}
