const FRACTION: Record<string, number> = { JPY: 0, KRW: 0, KWD: 3, BHD: 3, OMR: 3, TND: 3 }

export function fractionDigits(currency: string): number {
  return FRACTION[currency] ?? 2
}

export function formatMinor(minor: number, currency: string): string {
  const digits = fractionDigits(currency)
  const scale = 10 ** digits
  const negative = minor < 0
  const abs = Math.abs(minor)
  const whole = Math.trunc(abs / scale)
  const fraction = digits === 0 ? '' : `.${String(abs % scale).padStart(digits, '0')}`
  const formatted = new Intl.NumberFormat('en-GB', {
    style: 'currency',
    currency,
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(Number(`${negative ? '-' : ''}${whole}${fraction}`))
  return formatted
}

export function eurosToMinor(text: string): number | null {
  const value = text.trim()
  if (!/^\d+(\.\d{1,2})?$/.test(value)) return null
  const [whole, fraction = ''] = value.split('.')
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}
