import type { PublicationFilters, PublicationPost, PublicationWindow } from './types'

const pad = (value: number) => String(value).padStart(2, '0')
const daysInMonth = (year: number, month: number) => [31, year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1] ?? 0

export function monthDays(month: string): string[] {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return []
  const [year, monthNumber] = month.split('-').map(Number)
  if (year < 1) return []
  return Array.from({ length: daysInMonth(year, monthNumber) }, (_, index) => `${month}-${pad(index + 1)}`)
}

export function shiftMonth(month: string, delta: number): string {
  if (!monthDays(month).length || !Number.isInteger(delta)) return month
  const [year, monthNumber] = month.split('-').map(Number)
  const absolute = year * 12 + monthNumber - 1 + delta
  if (absolute < 12 || absolute >= 120000) return month
  return `${String(Math.floor(absolute / 12)).padStart(4, '0')}-${pad(absolute % 12 + 1)}`
}

/** The backend range is an absolute UTC half-open month; display timezone remains presentation-only. */
export function monthWindow(month: string): PublicationWindow | null {
  if (!monthDays(month).length) return null
  const next = shiftMonth(month, 1)
  return next === month ? null : { start: `${month}-01T00:00:00.000Z`, end: `${next}-01T00:00:00.000Z` }
}

/** Dates without an explicit offset are deliberately never parsed. */
export function absoluteInstant(value: unknown): number | null {
  if (typeof value !== 'string') return null
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(Z|[+-](\d{2}):(\d{2}))$/.exec(value)
  if (!match) return null
  const [, year, month, day, hour, minute, second, zone, offsetHour, offsetMinute] = match
  if (+year < 1 || +month < 1 || +month > 12 || +day < 1 || +day > daysInMonth(+year, +month) || +hour > 23 || +minute > 59 || +second > 59
    || (zone !== 'Z' && (+offsetHour > 23 || +offsetMinute > 59 || zone === '-00:00'))) return null
  const instant = Date.parse(value)
  return Number.isFinite(instant) ? instant : null
}

export function calendarDay(value: unknown, zone: string): string | null {
  const instant = absoluteInstant(value)
  if (instant === null) return null
  try {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone: zone, calendar: 'gregory', numberingSystem: 'latn', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(instant)
    const part = (name: string) => parts.find(candidate => candidate.type === name)?.value
    return `${part('year')?.padStart(4, '0')}-${part('month')}-${part('day')}`
  } catch {
    return null
  }
}

export function filterPosts(posts: readonly PublicationPost[], filters: PublicationFilters): PublicationPost[] {
  const query = filters.query.trim().toLowerCase()
  return posts.filter(post => (!query || post.id.toLowerCase().includes(query) || (post.contentAvailability === 'AVAILABLE' && post.contentText?.toLowerCase().includes(query)))
    && (!filters.content || post.contentAvailability === filters.content) && (!filters.artifact || post.artifactRelationState === filters.artifact))
    .sort((left, right) => {
      const leftInstant = absoluteInstant(left.scheduledAt)
      const rightInstant = absoluteInstant(right.scheduledAt)
      if (leftInstant === null && rightInstant !== null) return 1
      if (rightInstant === null && leftInstant !== null) return -1
      const timeDifference = leftInstant !== null && rightInstant !== null ? (leftInstant - rightInstant) * (filters.order === 'asc' ? 1 : -1) : 0
      return timeDifference || (left.id < right.id ? -1 : left.id > right.id ? 1 : 0)
    })
}
