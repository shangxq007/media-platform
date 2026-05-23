/**
 * Stable hash for timeline JSON comparison (non-cryptographic).
 */
export function hashTimelineJson(json: string): string {
  let h = 5381
  for (let i = 0; i < json.length; i++) {
    h = ((h << 5) + h) ^ json.charCodeAt(i)
  }
  return (h >>> 0).toString(16).padStart(8, '0')
}

export function parseInternalRevision(internalTimelineJson: string | undefined): number {
  if (!internalTimelineJson) {
    return 0
  }
  try {
    const root = JSON.parse(internalTimelineJson) as { revision?: number }
    return typeof root.revision === 'number' ? root.revision : 0
  } catch {
    return 0
  }
}
