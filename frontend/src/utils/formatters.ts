export const formatMoney = (amount: number, currency = 'VND') =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: currency === 'VND' ? 0 : 2,
  }).format(amount)

export const formatDate = (value: string | undefined) => {
  if (!value) {
    return 'Chưa đặt'
  }

  return new Intl.DateTimeFormat('vi-VN', {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
  }).format(new Date(value))
}

export const formatPercent = (value: number) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'percent',
    maximumFractionDigits: 1,
  }).format(value)
