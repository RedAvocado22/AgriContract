/** Format decimal text without converting financial values to floating point. */
export function formatDecimalVnd(value: string) {
  if (!/^-?\d+(\.\d+)?$/.test(value)) return "Chưa xác định"
  const [whole, fraction] = value.replace(/^-/, "").split(".")
  const grouped = whole
    .replace(/^0+(?=\d)/, "")
    .replace(/\B(?=(\d{3})+(?!\d))/g, ".")
  return grouped + (fraction && /[1-9]/.test(fraction) ? `,${fraction}` : "")
}
