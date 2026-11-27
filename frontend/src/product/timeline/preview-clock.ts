import { rational, ZERO, type Rational } from './trim'

/** Use the native control's actual decimal serialization rather than flooring a Number. */
export function decimalCursor(value: string): Rational | null {
  const match = /^(\d+)(?:\.(\d+))?(?:e([+-]?\d+))?$/i.exec(value)
  if (!match || value.length > 64 || Math.abs(Number(match[3] ?? 0)) > 324) return null
  const fraction = match[2] ?? ''
  const exponent = Number(match[3] ?? 0) - fraction.length
  const digits = BigInt(match[1] + fraction)
  return exponent < 0 ? rational(digits, 10n ** BigInt(-exponent))
    : { numerator: digits * 10n ** BigInt(exponent), denominator: 1n }
}

const binary = new DataView(new ArrayBuffer(8))

/** A continuous hardware clock produces doubles, not frame identities. Preserve its exact side of a cut. */
export function clockCursor(seconds: number): Rational {
  if (seconds <= 0) return ZERO
  binary.setFloat64(0, seconds)
  const bits = binary.getBigUint64(0)
  const encodedExponent = Number(bits >> 52n) & 0x7ff
  const fraction = bits & ((1n << 52n) - 1n)
  const significand = encodedExponent ? (1n << 52n) + fraction : fraction
  const exponent = (encodedExponent || 1) - 1075
  return exponent < 0 ? rational(significand, 1n << BigInt(-exponent))
    : { numerator: significand << BigInt(exponent), denominator: 1n }
}
