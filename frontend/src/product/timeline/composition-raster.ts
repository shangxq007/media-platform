import type { RasterProfile } from '../../api/formal-render'
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
function suggestRaster(aspect: CompositionAspectRatio, limits: RasterProfile): Raster | null {
  const { numerator, denominator } = aspect
  // A reduced aspect has coprime axes. Both dimensions align iff the scale aligns.
  const a = limits.alignment
  const min = Math.ceil(Math.max(limits.minWidth / numerator, limits.minHeight / denominator) / a) * a
  const max = Math.floor(Math.min(limits.maxWidth / numerator, limits.maxHeight / denominator,
    Math.sqrt(limits.maxPixels / numerator / denominator)) / a) * a
  if (min > max) return null
  const preferred = Math.floor(320 / Math.max(numerator, denominator) / a) * a
  const scale = Math.max(min, Math.min(max, preferred))
  return { width: numerator * scale, height: denominator * scale }
}

export function previewRaster(aspect: CompositionAspectRatio): Raster | null {
  return suggestRaster(aspect, { minWidth: 1, minHeight: 1, maxWidth: 1280, maxHeight: 1280,
    maxPixels: 1280 * 1280, alignment: 1 })
}

export function suggestedOutputRaster(aspect: CompositionAspectRatio, profiles: RasterProfile[]): Raster | null {
  for (const profile of profiles) {
    const raster = suggestRaster(aspect, profile)
    if (raster) return raster
  }
  return null
}

export function outputRasterError(aspect: CompositionAspectRatio, width: number, height: number,
  profiles: RasterProfile[]): string {
  if (!suggestedOutputRaster(aspect, profiles)) return 'No supported output raster exactly matches the authored composition aspect ratio.'
  if (!Number.isInteger(width) || !Number.isInteger(height) || !profiles.some(p => width >= p.minWidth
      && height >= p.minHeight && width <= p.maxWidth && height <= p.maxHeight
      && width % p.alignment === 0 && height % p.alignment === 0 && width * height <= p.maxPixels)) {
    return 'The requested output dimensions are outside the supported raster limits.'
  }
  return width * aspect.denominator === height * aspect.numerator
    ? '' : 'Output raster must match the authored composition aspect ratio.'
}
