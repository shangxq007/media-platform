export const COMPOSITION_ASPECT_FORMAT = 'composition-aspect-rational-v1'
const MAX_COMPONENT = 2_147_483_647

export interface CompositionAspectRatio {
  readonly format: typeof COMPOSITION_ASPECT_FORMAT
  readonly numerator: number
  readonly denominator: number
}

export function exactCompositionAspect(numerator: number, denominator: number): CompositionAspectRatio {
  if (![numerator, denominator].every(value => Number.isInteger(value) && value >= 1 && value <= MAX_COMPONENT)) {
    throw new Error('Composition aspect components must be integers from 1 to 2147483647.')
  }
  let left = numerator
  let right = denominator
  while (right !== 0) {
    const remainder = left % right
    left = right
    right = remainder
  }
  return { format: COMPOSITION_ASPECT_FORMAT, numerator: numerator / left, denominator: denominator / left }
}

export const DEFAULT_COMPOSITION_ASPECT = exactCompositionAspect(4, 3)

export function decodeCompositionAspect(value: unknown): CompositionAspectRatio {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Unsupported composition aspect ratio format.')
  }
  const fields = value as Record<string, unknown>
  if (Object.keys(fields).length !== 3 || fields.format !== COMPOSITION_ASPECT_FORMAT
      || typeof fields.numerator !== 'number' || typeof fields.denominator !== 'number') {
    throw new Error('Unsupported composition aspect ratio format.')
  }
  const ratio = exactCompositionAspect(fields.numerator, fields.denominator)
  if (ratio.numerator !== fields.numerator || ratio.denominator !== fields.denominator) {
    throw new Error('Stored composition aspect ratio must be reduced.')
  }
  return ratio
}

export function aspectLabel(aspect: CompositionAspectRatio): string {
  return `${aspect.numerator}:${aspect.denominator}`
}
