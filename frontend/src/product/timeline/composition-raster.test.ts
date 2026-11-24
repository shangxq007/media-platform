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
    const custom = exactCompositionAspect(13, 11)
    expect(suggestedOutputRaster(custom)).toEqual({ width: 312, height: 264 })
    expect(outputRasterError(custom, 624, 528)).toBe('')
    expect(outputRasterError(custom, 624, 530)).toContain('must match')
    const noOutput = exactCompositionAspect(1000, 777)
    expect(previewRaster(noOutput)).toEqual({ width: 1000, height: 777 })
    expect(suggestedOutputRaster(noOutput)).toBeNull()
    expect(outputRasterError(noOutput, 1000, 778)).toContain('No supported output raster')
    expect(previewRaster(exactCompositionAspect(2_147_483_647, 1))).toBeNull()
  })
})
