/// <reference lib="es2022.intl" />
import type { SubtitleCue } from './subtitle-cues'

export type FontRun = NonNullable<SubtitleCue['fontRuns']>[number]
export type Range = Pick<FontRun, 'start' | 'end'>
const segmenter = new Intl.Segmenter('und', { granularity: 'grapheme' })

export const textareaText = (text: string) => text.replace(/\r\n?/g, '\n')

/** A textarea indexes normalized UTF-16. Operations index the authored Unicode scalars. */
function graphemeBoundaries(text: string) {
  const boundaries = [{ dom: 0, utf16: 0, scalars: 0 }]
  for (const { segment, index } of segmenter.segment(text)) {
    const previous = boundaries[boundaries.length - 1]
    boundaries.push({
      dom: previous.dom + textareaText(segment).length,
      utf16: index + segment.length,
      scalars: previous.scalars + Array.from(segment).length,
    })
  }
  return boundaries
}

export function subtitleSelection(text: string, from: number, to: number): Range | null {
  if (from === to) return null
  const boundaries = graphemeBoundaries(text)
  const start = boundaries.find(point => point.dom === from)
  const end = boundaries.find(point => point.dom === to)
  return start && end ? { start: start.scalars, end: end.scalars } : null
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

export type TextInputSpan = Range & { inputType: string }

/** The unchanged graphemes common to all minimal single-span replacements. */
function provenChangeSpan(before: string, after: string): Range {
  if (before === after) return { start: Array.from(before).length, end: Array.from(before).length }
  const old = Array.from(segmenter.segment(before), item => item.segment)
  const next = Array.from(segmenter.segment(after), item => item.segment)
  const shared = Math.min(old.length, next.length)
  let prefix = 0, suffix = 0
  while (prefix < shared && old[prefix] === next[prefix]) prefix++
  while (suffix < shared && old[old.length - suffix - 1] === next[next.length - suffix - 1]) suffix++
  const retained = Math.min(shared, prefix + suffix)
  const left = Array.from(old.slice(0, retained - suffix).join('')).length
  const right = Array.from(before).length - Array.from(old.slice(old.length - (retained - prefix)).join('')).length
  return { start: left, end: right }
}

export function anchoredSubtitleRanges(before: string, after: string): (range: Range) => boolean {
  const { start, end } = provenChangeSpan(before, after)
  return range => range.end <= start || range.start >= end
}

/** Determine whether the proposed grapheme edit has a uniquely inferable full style. */
export function ambiguousCanonicalSubtitleStyle(cue: SubtitleCue, after: string): boolean {
  if (cue.text === after) return false
  if (cue.hasPinnedTextRanges || cue.canonicalStyleRuns === null) return true
  if (!cue.canonicalStyleRuns) return false
  const boundaries = graphemeBoundaries(cue.text)
  const scalar = boundaries[boundaries.length - 1].scalars
  const complete = new Set(boundaries.map(item => item.scalars))
  let cursor = 0
  for (const run of cue.canonicalStyleRuns) {
    if (run.start !== cursor || run.end <= cursor || !complete.has(run.end)) return true
    cursor = run.end
  }
  if (cursor !== scalar) return true
  const { start, end } = provenChangeSpan(cue.text, after)
  const inline = (position: number) => cue.fontRuns?.some(run => run.start <= position && position < run.end)
  const owners = cue.canonicalStyleRuns.filter(run => start < end
    ? run.start < end && start < run.end
    : (run.start <= start && start < run.end && !inline(start))
      || (run.start < start && start <= run.end && !inline(start - 1)))
  return new Set(owners.map(run => run.styleIdentity)).size > 1
}

function editSpan(before: string, after: string, caret: number, span: TextInputSpan | null): Range | null {
  if (!span) return null
  let { start, end } = span
  const removed = before.length - after.length
  if (start === end && removed > 0) {
    if (span.inputType.endsWith('Backward') && caret === start - removed) start = caret
    if (span.inputType.endsWith('Forward') && caret === start) end += removed
  }
  const newEnd = end - removed
  return start <= newEnd && before.slice(0, start) === after.slice(0, start)
    && before.slice(end) === after.slice(newEnd) ? { start, end } : null
}

/** Use the native edit span to distinguish typing over a letter from replacing an entire word. */
export function editRangeFonts<T extends Range = Range>(
  authored: string, after: string, runs: FontRun[], caret = after.length, input: TextInputSpan | null = null, fade?: T,
): { text: string; fontRuns: FontRun[]; selectedTextFade?: T } {
  const before = textareaText(authored)
  const span = editSpan(before, after, caret, input)
  if (before === after && (!span || span.start === span.end)) return { text: authored, fontRuns: runs, ...(fade && { selectedTextFade: fade }) }

  const oldPoints = graphemeBoundaries(authored), newPoints = graphemeBoundaries(after)
  const oldLast = oldPoints.length - 1, newLast = newPoints.length - 1
  let prefix = 0, suffix = 0
  const prefixLimit = span?.start ?? before.length
  // Without beforeinput, anchor the unchanged suffix at the post-edit caret where possible.
  // With beforeinput, identical text inside a replacement is still newly authored.
  const suffixLimit = span ? before.length - span.end : after.length - caret
  const advanceSuffix = (limit: number) => {
    while (suffix < oldLast - prefix && suffix < newLast - prefix) {
      const oldStart = oldPoints[oldLast - suffix - 1].dom, newStart = newPoints[newLast - suffix - 1].dom
      if (after.length - newStart > limit || before.slice(oldStart, oldPoints[oldLast - suffix].dom)
        !== after.slice(newStart, newPoints[newLast - suffix].dom)) break
      suffix++
    }
  }
  advanceSuffix(suffixLimit)
  while (prefix < oldLast - suffix && prefix < newLast - suffix
    && oldPoints[prefix + 1].dom <= prefixLimit
    && before.slice(oldPoints[prefix].dom, oldPoints[prefix + 1].dom)
      === after.slice(newPoints[prefix].dom, newPoints[prefix + 1].dom)) prefix++
  if (!span) advanceSuffix(after.length)

  const left = oldPoints[prefix], right = oldPoints[oldLast - suffix]
  const inserted = after.slice(newPoints[prefix].dom, newPoints[newLast - suffix].dom)
  const prefixText = authored.slice(0, left.utf16)
  const suffixText = authored.slice(right.utf16)
  // Complete a retained bare CR before a separate LF so the textarea still sees two breaks.
  const continuedBreak = prefixText.endsWith('\r') && (inserted || suffixText).startsWith('\n') ? '\n' : ''
  const text = prefixText + continuedBreak + inserted + suffixText
  const delta = Array.from(inserted).length + continuedBreak.length - (right.scalars - left.scalars)
  function remap<R extends Range>(range: R): R[] {
    const parts: R[] = []
    if (range.start < left.scalars) {
      const end = range.end < left.scalars ? range.end : left.scalars + continuedBreak.length
      parts.push({ ...range, end })
    }
    if (range.end > right.scalars) {
      parts.push({ ...range, start: Math.max(right.scalars, range.start) + delta, end: range.end + delta })
    }
    return parts
  }
  const [first, second] = fade ? remap(fade) : []
  // Newly authored text cannot inherit the effect. Two separated survivors cannot
  // be represented as one contiguous range, so clear the effect in that case.
  const selectedTextFade = first && (!second || first.end === second.start)
    ? { ...first, end: second?.end ?? first.end } : undefined
  return { text, fontRuns: runs.flatMap(run => remap(run)), ...(selectedTextFade && { selectedTextFade }) }
}
