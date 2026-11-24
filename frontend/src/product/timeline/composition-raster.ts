import { exactCompositionAspect, type CompositionAspectRatio } from '../../contracts/app/composition-aspect'

export const COMPOSITION_PRESETS = [
  [16, 9, '16:9'], [9, 16, '9:16'], [1, 1, '1:1'], [4, 3, '4:3'],
  [3, 4, '3:4'], [4, 5, '4:5'], [21, 9, '21:9'], [239, 100, '2.39:1'],
].map(([width, height, label]) => ({ ratio: exactCompositionAspect(Number(width), Number(height)), label: String(label) }))

export function customCompositionAspect(width: string, height: string): CompositionAspectRatio {
  if (![width, height].every(value => /^[0-9]{1,10}$/.test(value))) {
    throw new Error('Enter a positive integer width and height for the composition ratio.')
  }
  return exactCompositionAspect(Number(width), Number(height))
}

interface Raster { readonly width: number; readonly height: number }
const OUTPUT_LIMITS = { minimum: 16, maximum: 1920, pixels: 2_073_600, even: true }

function suggestRaster(aspect: CompositionAspectRatio,
  limits: { minimum: number; maximum: number; pixels: number; even: boolean }): Raster | null {
  const { numerator, denominator } = aspect
  const alignment = limits.even && (numerator % 2 || denominator % 2) ? 2 : 1
  const minimumScale = Math.ceil(limits.minimum / Math.min(numerator, denominator) / alignment) * alignment
  const preferredScale = Math.floor(320 / Math.max(numerator, denominator) / alignment) * alignment
  const scale = Math.max(minimumScale, preferredScale)
  const width = numerator * scale
  const height = denominator * scale
  return width <= limits.maximum && height <= limits.maximum && width * height <= limits.pixels
    ? { width, height } : null
}

export function previewRaster(aspect: CompositionAspectRatio): Raster | null {
  return suggestRaster(aspect, { minimum: 1, maximum: 1280, pixels: 1280 * 1280, even: false })
}

export function suggestedOutputRaster(aspect: CompositionAspectRatio): Raster | null {
  return suggestRaster(aspect, OUTPUT_LIMITS)
}

export function outputRasterError(aspect: CompositionAspectRatio, width: number, height: number): string {
  if (!suggestedOutputRaster(aspect)) return 'No supported output raster exactly matches the authored composition aspect ratio.'
  if (![width, height].every(value => Number.isInteger(value) && value >= OUTPUT_LIMITS.minimum
      && value <= OUTPUT_LIMITS.maximum && value % 2 === 0) || width * height > OUTPUT_LIMITS.pixels) {
    return 'Output requires even dimensions from 16 to 1920 and at most 2073600 pixels.'
  }
  return width * aspect.denominator === height * aspect.numerator
    ? '' : 'Output raster must match the authored composition aspect ratio.'
}
