import { expect, it } from 'vitest'
import cases from '../../../contracts/composition/version-range-cases.json'
import { versionCompatibility } from './compositionVersionRange'
it.each(cases)('canonical range $range against $actual: $expected', ({ range, actual, expected }) => {
  expect(versionCompatibility(range, actual)).toBe(expected)
})
