// =============================================================================
// TrackView Component
// =============================================================================
// Renders a single track row in the timeline.
// Contains clip blocks and track header with controls.
// Supports drop zones for drag and drop.
// =============================================================================

import { useDroppable } from '@dnd-kit/core'
import type { TimelineTrack, TrackLayout } from '../model/timeline'
import { TRACK_TYPE_COLORS } from '../model/timeline'
import { useTimelineStore } from '../store/timelineStore'
import { ClipBlock } from './ClipBlock'

interface TrackViewProps {
  track: TimelineTrack
  layout: TrackLayout
  pixelsPerSecond: number
  onTrimStart?: (clipId: string, side: 'start' | 'end', clientX: number) => void
}

export function TrackView({ track, layout, pixelsPerSecond, onTrimStart }: TrackViewProps) {
  const { selectedClipId, setSelectedClip, selectedTrackId, setSelectedTrack } = useTimelineStore()
  const clips = useTimelineStore((state) => state.getTrackClips(track.id))
  const isSelected = selectedTrackId === track.id
  const trackColor = TRACK_TYPE_COLORS[track.type]

  const { setNodeRef, isOver } = useDroppable({
    id: track.id,
    data: { track },
  })

  return (
    <div
      className={`flex border-b border-gray-800 ${isSelected ? 'bg-gray-800/50' : ''}`}
      onClick={() => setSelectedTrack(track.id)}
    >
      {/* Track Header */}
      <div
        className="w-40 flex-shrink-0 flex items-center gap-2 px-3 py-2 border-r border-gray-800 cursor-pointer hover:bg-gray-800/50"
        onClick={(e) => {
          e.stopPropagation()
          setSelectedTrack(track.id)
        }}
      >
        <div
          className="w-3 h-3 rounded-full flex-shrink-0"
          style={{ backgroundColor: trackColor }}
        />
        <div className="min-w-0">
          <div className="text-xs font-medium text-gray-200 truncate">
            {track.name}
          </div>
          <div className="text-[10px] text-gray-500 uppercase">
            {track.type}
          </div>
        </div>
        <div className="ml-auto flex items-center gap-1">
          {track.muted && (
            <span className="text-[10px] text-red-400">M</span>
          )}
          {track.locked && (
            <span className="text-[10px] text-yellow-400">L</span>
          )}
        </div>
      </div>

      {/* Track Body (clips) - Drop zone */}
      <div
        ref={setNodeRef}
        className="relative flex-1 transition-colors"
        style={{
          height: `${track.height}px`,
          minHeight: `${track.height}px`,
          backgroundColor: isOver ? 'rgba(59, 130, 246, 0.1)' : undefined,
        }}
      >
        {clips.map((clip) => (
          <ClipBlock
            key={clip.id}
            clip={clip}
            pixelsPerSecond={pixelsPerSecond}
            isSelected={selectedClipId === clip.id}
            onSelect={setSelectedClip}
            onTrimStart={onTrimStart}
          />
        ))}

        {/* Drop indicator */}
        {isOver && (
          <div className="absolute inset-0 border-2 border-blue-500/50 rounded pointer-events-none" />
        )}
      </div>
    </div>
  )
}
