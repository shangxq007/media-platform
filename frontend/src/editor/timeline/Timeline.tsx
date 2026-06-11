interface TimelineProps {
  currentTime: number
  duration: number
  captions: Array<{ id: string; text: string; startTime: number; endTime: number }>
}

export function Timeline({ currentTime, duration, captions }: TimelineProps) {
  return (
    <div className="h-24 bg-gray-900 border-t border-gray-800 px-4 py-2">
      <div className="relative h-12 bg-gray-800 rounded">
        <div
          className="absolute top-0 left-0 h-full bg-blue-600/30 rounded"
          style={{ width: `${(currentTime / duration) * 100}%` }}
        />
        {captions.map((cap) => (
          <div
            key={cap.id}
            className="absolute top-1 h-4 bg-yellow-500/50 rounded text-xs px-1 truncate"
            style={{
              left: `${(cap.startTime / duration) * 100}%`,
              width: `${((cap.endTime - cap.startTime) / duration) * 100}%`,
            }}
          >
            {cap.text}
          </div>
        ))}
      </div>
    </div>
  )
}
