import grammar from '../../../contracts/composition/version-range-v1.json'

type Version = [number, number, number]
type Bound = { version: Version; inclusive: boolean }
const compare = (a: Version, b: Version) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2]
function version(value: string): Version | undefined {
  const m = new RegExp(grammar.versionPattern).exec(value)
  if (!m) return undefined
  const v: Version = [Number(m[1]), Number(m[2]), Number(m[3] ?? 0)]
  return v.every(n => Number.isSafeInteger(n) && n <= grammar.maximumComponent) ? v : undefined
}
/** Interpreter of the same canonical interval description consumed by the backend. */
export function versionCompatibility(range: string, actual: string): string {
  const expression = range.trim()
  if (!expression) return 'MALFORMED_VERSION_RANGE'
  let lower: Bound | undefined, upper: Bound | undefined
  const exact = version(expression)
  const wildcard = new RegExp(grammar.wildcardPattern).exec(expression)
  if (exact) {
    lower = upper = { version: exact, inclusive: true }
  } else if (wildcard) {
    const start = version(`${wildcard[1]}.${wildcard[2] ?? 0}.0`)
    if (!start) return 'MALFORMED_VERSION_RANGE'
    lower = { version: start, inclusive: true }
    upper = { version: wildcard[2] === undefined ? [start[0] + 1, 0, 0] : [start[0], start[1] + 1, 0], inclusive: false }
  } else {
    const tokens = expression.split(/\s+/)
    if (tokens.length > 2) return 'MALFORMED_VERSION_RANGE'
    for (const token of tokens) {
      const bound = new RegExp(grammar.boundPattern).exec(token)
      if (!bound) return 'MALFORMED_VERSION_RANGE'
      const v = version(bound[2])
      if (!v) return 'MALFORMED_VERSION_RANGE'
      if (bound[1].startsWith('>')) {
        if (lower) return 'MALFORMED_VERSION_RANGE'
        lower = { version: v, inclusive: bound[1] === '>=' }
      } else {
        if (upper) return 'MALFORMED_VERSION_RANGE'
        upper = { version: v, inclusive: bound[1] === '<=' }
      }
    }
  }
  if (lower && upper) {
    const c = compare(lower.version, upper.version)
    if (c > 0 || (c === 0 && (!lower.inclusive || !upper.inclusive))) return 'INVALID_VERSION_RANGE'
  }
  const candidate = version(actual)
  if (!candidate) return 'MALFORMED_VERSION'
  if (lower) {
    const c = compare(candidate, lower.version)
    if (c < 0 || (c === 0 && !lower.inclusive)) return 'INCOMPATIBLE_VERSION_RANGE'
  }
  if (upper) {
    const c = compare(candidate, upper.version)
    if (c > 0 || (c === 0 && !upper.inclusive)) return 'INCOMPATIBLE_VERSION_RANGE'
  }
  return 'OK'
}
