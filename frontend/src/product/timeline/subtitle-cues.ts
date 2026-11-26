import type { SemanticStep } from '../../contracts/semantic-batch.generated'
import { compare, parseRational, subtract, toSeconds, type Rational } from './trim'

export type SubtitleCue = Extract<SemanticStep, { definitionId: 'timeline.subtitle.cue.set' }>['parameters']

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
