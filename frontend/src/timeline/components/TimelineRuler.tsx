// =============================================================================
// TimelineRuler Component
// =============================================================================
// Renders the time ruler at the top of the timeline.
// Shows time markers and playhead position.
// =============================================================================

import { useTimelineStore } from '../store/timelineStore'

interface TimelineRulerProps {
  pixelsPerSecond: number
}

export function TimelineRuler({ pixelsPerSecond }: TimelineRulerProps) {
  const { playheadPosition, setPlayhead, timeline } = useTimelineStore()
  const duration = timeline.duration

  // Calculate marker interval based on zoom
  const markerInterval = pixelsPerSecond >= 100 ? 1 : pixelsPerSecond >= 50 ? 2 : 5
  const markers: number[] = []
  for (let t = 0; t <= duration + markerInterval; t += markerInterval) {
    markers.push(t)
  }

  const rulerWidth = Math.max(duration * pixelsPerSecond, 800)

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const x = e.clientX - rect.left + e.currentTarget.scrollLeft
    const time = x / pixelsPerSecond
    setPlayhead(time)
  }

  return (
    <div
      className="relative h-8 bg-gray-900 border-b border-gray-800 cursor-pointer select-none"
      style={{ width: `${rulerWidth}px` }}
      onClick={handleClick}
    >
      {/* Time markers */}
      {markers.map((t) => (
        <div
          key={t}
          className="absolute top-0 bottom-0 flex flex-col items-center"
          style={{ left: `${t * pixelsPerSecond}px` }}
        >
          <div className="text-[10px] text-gray-500 pt-0.5">
            {formatTime(t)}
          </div>
          <div className="w-px h-full bg-gray-800" />
        </div>
      ))}

      {/* Playhead marker */}
      <div
        className="absolute top-0 bottom-0 w-0.5 bg-red-500 z-10"
        style={{ left: `${playheadPosition * pixelsPerSecond}px` }}
      >
        <div className="absolute -top-0 -left-1.5 w-3 h-3 bg-red-500 rotate-45 transform -translate-y-0.5" />
      </div>
    </div>
  )
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}
