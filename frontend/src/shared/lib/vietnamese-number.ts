const DIGIT_WORDS = [
  "không",
  "một",
  "hai",
  "ba",
  "bốn",
  "năm",
  "sáu",
  "bảy",
  "tám",
  "chín",
]

/**
 * Đơn vị theo nhóm 3 chữ số, mở rộng vô hạn: nghìn/triệu/tỷ rồi lặp lại kèm
 * hậu tố "tỷ" (nghìn tỷ, triệu tỷ, tỷ tỷ...) — không giới hạn cứng ở "tỷ" như
 * bản cũ, tránh rớt mất đơn vị khi số vượt 999 tỷ (case thật gặp: 5.000 tỷ
 * từng bị đọc thành "Năm đồng" vì GROUP_UNITS[4] không tồn tại).
 */
function scaleWord(scaleIndex: number): string {
  const tier = Math.floor(scaleIndex / 3)
  const posInTier = scaleIndex % 3
  const posWord = posInTier === 0 ? "" : posInTier === 1 ? "nghìn" : "triệu"
  const tySuffix = tier > 0 ? Array(tier).fill("tỷ").join(" ") : ""
  return [posWord, tySuffix].filter(Boolean).join(" ")
}

function readThreeDigits(group: number, isMostSignificantGroup: boolean): string {
  const hundred = Math.floor(group / 100)
  const ten = Math.floor((group % 100) / 10)
  const unit = group % 10
  const parts: string[] = []

  if (hundred > 0) {
    parts.push(DIGIT_WORDS[hundred], "trăm")
  } else if (!isMostSignificantGroup && (ten > 0 || unit > 0)) {
    parts.push("không trăm")
  }

  if (ten > 1) {
    parts.push(DIGIT_WORDS[ten], "mươi")
    if (unit === 1) parts.push("mốt")
    else if (unit === 5) parts.push("lăm")
    else if (unit > 0) parts.push(DIGIT_WORDS[unit])
  } else if (ten === 1) {
    parts.push("mười")
    if (unit === 5) parts.push("lăm")
    else if (unit > 0) parts.push(DIGIT_WORDS[unit])
  } else if (unit > 0) {
    if (hundred > 0 || !isMostSignificantGroup) parts.push("linh")
    parts.push(DIGIT_WORDS[unit])
  }

  return parts.join(" ")
}

/** Đọc số tiền bằng chữ tiếng Việt, dùng cho hint dưới MoneyInput. */
function numberToVietnameseWords(value: number): string {
  if (!Number.isFinite(value) || value === 0) return "Không đồng"

  const negative = value < 0
  let remaining = Math.floor(Math.abs(value))

  const groups: number[] = []
  while (remaining > 0) {
    groups.unshift(remaining % 1000)
    remaining = Math.floor(remaining / 1000)
  }

  const words: string[] = []
  groups.forEach((group, index) => {
    if (group === 0) return
    const scaleIndex = groups.length - index - 1
    const unit = scaleWord(scaleIndex)
    const groupWords = readThreeDigits(group, index === 0)
    words.push(unit ? `${groupWords} ${unit}` : groupWords)
  })

  const sentence = words.join(" ").trim()
  const capitalized = sentence.charAt(0).toUpperCase() + sentence.slice(1)
  return `${negative ? "Âm " : ""}${capitalized} đồng`
}

export { numberToVietnameseWords }
