import type { SemanticStep } from '../../contracts/semantic-batch.generated'
import { add, compare, parseRational, subtract, toSeconds, type Rational } from './trim'

export type SubtitleCue = Extract<SemanticStep, { definitionId: 'timeline.subtitle.cue.set' }>['parameters']

export type SubtitleDecoration = NonNullable<SubtitleCue['decoration']>
export type SelectedTextFade = NonNullable<SubtitleCue['selectedTextFade']>

const LEGACY_SUBTITLE_FONT = 'noto-sc-color-emoji-v1'

/** Saved snapshots may omit the legacy pin. Whether an ID is installed is decided by the backend. */
export function savedSubtitleFont(value: unknown): string {
  if (value == null) return LEGACY_SUBTITLE_FONT
  if (typeof value === 'string' && /^[a-z0-9-]{1,40}$/.test(value)) return value
  throw new Error('Invalid saved subtitle font identity')
}

export function subtitlePose(cue: SubtitleCue, time: Rational) {
  const start = parseRational(cue.start)!, end = parseRational(cue.end)!
  if (compare(time, start) < 0 || compare(time, end) >= 0) return null
  if (!cue.keys.length) return { x: 0.1, y: 0.55, alpha: 1 }
  const local = subtract(time, start)
  const index = cue.keys.findIndex(k => compare(local, parseRational(k.time)!) <= 0)
  const right = index < 0 ? cue.keys[cue.keys.length - 1] : cue.keys[index]
  const left = index <= 0 ? right : cue.keys[index - 1]
  const t = parseRational(left.time)!
  const u = left === right ? 0 : toSeconds(subtract(local, t)) / toSeconds(subtract(parseRational(right.time)!, t))
  const value = (field: 'xMicros'|'yMicros'|'opacityMicros') => (left[field] + u * (right[field] - left[field])) / 1_000_000
  return { x: value('xMicros'), y: value('yMicros'), alpha: value('opacityMicros') }
}

export function selectedTextAlpha(cue: SubtitleCue, time: Rational) {
  const fade = cue.selectedTextFade
  if (!fade) return 1
  const start = add(parseRational(cue.start)!, parseRational(fade.time)!)
  const length = parseRational(fade.duration)!
  if (compare(time, start) <= 0) return 0
  if (compare(time, add(start, length)) >= 0) return 1
  return toSeconds(subtract(time, start)) / toSeconds(length)
}

export function decorationVisible(cue: SubtitleCue, time: Rational) {
  const decoration = cue.decoration
  if (!decoration?.enabled) return false
  const local = subtract(time, parseRational(cue.start)!)
  return compare(local, parseRational(decoration.start)!) >= 0 && compare(local, parseRational(decoration.end)!) < 0
}
