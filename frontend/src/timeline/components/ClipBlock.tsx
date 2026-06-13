// =============================================================================
// ClipBlock Component
// =============================================================================
// Renders a single clip block on the timeline.
// Handles selection state, hover highlighting, drag, and trim.
// =============================================================================

import { useState, useCallback, useRef } from 'react'
import { useDraggable } from '@dnd-kit/core'
import type { TimelineClip } from '../model/timeline'
import { CLIP_TYPE_COLORS } from '../model/timeline'

interface ClipBlockProps {
  clip: TimelineClip
  pixelsPerSecond: number
  isSelected: boolean
  onSelect: (clipId: string) => void
  onTrimStart?: (clipId: string, side: 'start' | 'end', clientX: number) => void
}

export function ClipBlock({
  clip,
  pixelsPerSecond,
  isSelected,
  onSelect,
  onTrimStart,
}: ClipBlockProps) {
  const [isHovered, setIsHovered] = useState(false)
  const clipRef = useRef<HTMLDivElement>(null)

  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: clip.id,
    data: { clip },
  })

  const width = clip.timing.duration * pixelsPerSecond
  const left = clip.timing.start * pixelsPerSecond
  const color = CLIP_TYPE_COLORS[clip.type]

  // Apply drag transform
  const dragStyle = transform
    ? { transform: `translate3d(${transform.x}px, 0, 0)` }
    : undefined

  const handleTrimStart = useCallback(
    (side: 'start' | 'end', e: React.MouseEvent) => {
      e.stopPropagation()
      e.preventDefault()
      onTrimStart?.(clip.id, side, e.clientX)
    },
    [clip.id, onTrimStart]
  )

  return (
    <div
      ref={(node) => {
        setNodeRef(node)
        if (node) clipRef.current = node
      }}
      className="absolute top-1 bottom-1 rounded cursor-grab active:cursor-grabbing transition-all"
      style={{
        left: `${left}px`,
        width: `${Math.max(width, 4)}px`,
        backgroundColor: isSelected ? color : isHovered ? `${color}cc` : `${color}99`,
        border: isSelected
          ? '2px solid white'
          : isHovered
          ? `2px solid ${color}`
          : '1px solid rgba(255,255,255,0.1)',
        boxShadow: isSelected
          ? `0 0 8px ${color}80`
          : isDragging
          ? `0 4px 12px rgba(0,0,0,0.5)`
          : 'none',
        opacity: isDragging ? 0.8 : 1,
        zIndex: isDragging ? 50 : isSelected ? 10 : 1,
        ...dragStyle,
      }}
      onClick={(e) => {
        e.stopPropagation()
        onSelect(clip.id)
      }}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onSelect(clip.id)
        }
      }}
      aria-label={`Clip: ${clip.name}`}
      aria-selected={isSelected}
      {...attributes}
      {...listeners}
    >
      {/* Left trim handle */}
      {(isSelected || isHovered) && width > 20 && (
        <div
          className="absolute left-0 top-0 bottom-0 w-2 cursor-ew-resize hover:bg-white/30 rounded-l transition-colors z-10"
          onMouseDown={(e) => handleTrimStart('start', e)}
          role="slider"
          aria-label={`Trim start of ${clip.name}`}
          tabIndex={-1}
        />
      )}

      {/* Clip content */}
      <div className="px-2 py-0.5 pointer-events-none">
        {width > 40 && (
          <div className="text-xs font-medium text-white truncate leading-tight">
            {clip.name}
          </div>
        )}
        {width > 80 && (
          <div className="text-[10px] text-white/60 truncate">
            {formatTime(clip.timing.start)} — {formatTime(clip.timing.end)}
          </div>
        )}
      </div>

      {/* Right trim handle */}
      {(isSelected || isHovered) && width > 20 && (
        <div
          className="absolute right-0 top-0 bottom-0 w-2 cursor-ew-resize hover:bg-white/30 rounded-r transition-colors z-10"
          onMouseDown={(e) => handleTrimStart('end', e)}
          role="slider"
          aria-label={`Trim end of ${clip.name}`}
          tabIndex={-1}
        />
      )}
    </div>
  )
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  const ms = Math.floor((seconds % 1) * 10)
  return `${m}:${s.toString().padStart(2, '0')}.${ms}`
}
