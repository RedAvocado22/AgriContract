function getPasswordStrength(password: string): number {
  if (!password) return 0
  let score = 0
  if (password.length >= 8) score++
  if (/[A-Z]/.test(password)) score++
  if (/[0-9]/.test(password)) score++
  if (/[^A-Za-z0-9]/.test(password)) score++
  return score
}

const strengthColors = ["#E2E8F0", "#DC2626", "#D97706", "#22C55E", "#15803D"]
const strengthLabels = ["", "Yếu", "Trung bình", "Khá", "Mạnh"]

export { getPasswordStrength, strengthColors, strengthLabels }
