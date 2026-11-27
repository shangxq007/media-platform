import { expect, it } from 'vitest'
import { subtitleDecorationId } from './subtitle-decoration'

it('validates versioned decoration identity without enumerating catalog assets', () => {
  expect(['sunflower-v1', 'future-pin-v22', 'a-v1'].every(subtitleDecorationId)).toBe(true)
  for (const invalid of ['sunflower', 'a-v0', 'a-v01', 'A-v1', 'a-v1\n', 'a/b-v1', 'x'.repeat(63) + '-v1', null])
    expect(subtitleDecorationId(invalid)).toBe(false)
})
