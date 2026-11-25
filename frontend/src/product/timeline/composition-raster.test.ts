import { describe, expect, it } from 'vitest'
import { aspectLabel, exactCompositionAspect } from '../../contracts/app/composition-aspect'
import { COMPOSITION_PRESETS, customCompositionAspect, outputRasterError,
  previewRaster, suggestedOutputRaster } from './composition-raster'

describe('composition presets and raster admission', () => {
  it('resolves conveniences and integer input to one exact contract', () => {
    expect(COMPOSITION_PRESETS.map(item => [item.label, aspectLabel(item.ratio)])).toEqual([
      ['16:9', '16:9'], ['9:16', '9:16'], ['1:1', '1:1'], ['4:3', '4:3'], ['3:4', '3:4'],
      ['4:5', '4:5'], ['21:9', '7:3'], ['2.39:1', '239:100'],
    ])
    expect(customCompositionAspect('3840', '2160')).toEqual(exactCompositionAspect(16, 9))
    expect(customCompositionAspect('13', '11')).toEqual(exactCompositionAspect(13, 11))
    for (const invalid of ['0', '-1', '2.39', '2147483648', '2e2', '1:2']) {
      expect(() => customCompositionAspect(invalid, '1')).toThrow()
    }
  })

  it('separates exact geometry from preview and aligned output feasibility', () => {
    const profiles = [{ minWidth: 16, minHeight: 16, maxWidth: 1920, maxHeight: 1920, maxPixels: 2073600, alignment: 2 }]
    const fixed = [{ minWidth: 320, minHeight: 240, maxWidth: 320, maxHeight: 240, maxPixels: 76800, alignment: 2 }]
    expect(suggestedOutputRaster(exactCompositionAspect(4, 3), fixed)).toEqual({ width: 320, height: 240 })
    expect(suggestedOutputRaster(exactCompositionAspect(16, 9), fixed)).toBeNull()
    const custom = exactCompositionAspect(13, 11)
    expect(suggestedOutputRaster(custom, profiles)).toEqual({ width: 312, height: 264 })
    expect(outputRasterError(custom, 624, 528, profiles)).toBe('')
    expect(outputRasterError(custom, 624, 530, profiles)).toContain('must match')
    const noOutput = exactCompositionAspect(1000, 777)
    expect(previewRaster(noOutput)).toEqual({ width: 1000, height: 777 })
    expect(suggestedOutputRaster(noOutput, profiles)).toBeNull()
    expect(outputRasterError(noOutput, 1000, 778, profiles)).toContain('No supported output raster')
    expect(previewRaster(exactCompositionAspect(2_147_483_647, 1))).toBeNull()
  })
})
