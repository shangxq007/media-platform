/// <reference lib="es2022.intl" />
import type { SubtitleCue } from './subtitle-cues'

export type FontRun = NonNullable<SubtitleCue['fontRuns']>[number]
type Range = Pick<FontRun, 'start' | 'end'>
const segmenter = new Intl.Segmenter('und', { granularity: 'grapheme' })

/** The textarea indexes normalized UTF-16. The wire contract indexes the original Unicode scalars. */
export function subtitleSelection(text: string, from: number, to: number): Range | null {
  if (from === to) return null
  const boundaries = new Map([[0, 0]])
  let dom = 0, scalars = 0
  for (const { segment } of segmenter.segment(text)) {
    dom += segment.replace(/\r\n?/g, '\n').length
    scalars += Array.from(segment).length
    boundaries.set(dom, scalars)
  }
  const start = boundaries.get(from), end = boundaries.get(to)
  return start !== undefined && end !== undefined ? { start, end } : null
}

export function setRangeFont(runs: FontRun[], range: Range, profile: string): FontRun[] {
  const result: FontRun[] = []
  for (const run of runs) {
    if (run.end <= range.start || run.start >= range.end) result.push(run)
    else {
      if (run.start < range.start) result.push({ ...run, end: range.start })
      if (run.end > range.end) result.push({ ...run, start: range.end })
    }
  }
  if (profile) result.push({ ...range, fontProfile: profile })
  result.sort((a, b) => a.start - b.start)
  const merged: FontRun[] = []
  for (const run of result) {
    const last = merged[merged.length - 1]
    if (last && last.end === run.start && last.fontProfile === run.fontProfile) merged[merged.length - 1] = { ...last, end: run.end }
    else merged.push(run)
  }
  return merged
}

/** Preserve the unchanged prefix and suffix. Inserted/replaced graphemes inherit the cue font. */
export function editRangeFonts(before: string, after: string, runs: FontRun[], caret?: number): FontRun[] {
  if (!runs.length || before === after) return runs
  const segments = (s: string) => Array.from(segmenter.segment(s), g => g.segment)
  const a = segments(before), b = segments(after)
  let prefix = 0, suffix = 0
  // Anchor the unchanged suffix at the native post-edit caret. This disambiguates typing
  // another identical character immediately before a styled occurrence of that character.
  if (caret !== undefined) {
    let utf16End = after.length
    while (suffix < a.length && suffix < b.length && a[a.length - 1 - suffix] === b[b.length - 1 - suffix]) {
      const nextStart = utf16End - b[b.length - 1 - suffix].length
      if (nextStart < caret) break
      utf16End = nextStart
      suffix++
    }
  }
  while (prefix < a.length - suffix && prefix < b.length - suffix && a[prefix] === b[prefix]) prefix++
  while (suffix < a.length - prefix && suffix < b.length - prefix && a[a.length - 1 - suffix] === b[b.length - 1 - suffix]) suffix++
  const length = (s: string) => Array.from(s).length
  const left = length(a.slice(0, prefix).join(''))
  const right = length(a.slice(0, a.length - suffix).join(''))
  const delta = length(after) - length(before)
  const result: FontRun[] = []
  for (const run of runs) {
    if (run.start < left) result.push({ ...run, end: Math.min(left, run.end) })
    if (run.end > right) result.push({ ...run, start: Math.max(right, run.start) + delta, end: run.end + delta })
  }
  return result
}
