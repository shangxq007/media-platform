interface PlaybackControlsProps {
  isPlaying: boolean
  currentTime: number
  duration: number
  onPlay: () => void
  onSeek: (time: number) => void
}

export function PlaybackControls({ isPlaying, currentTime, duration, onPlay, onSeek }: PlaybackControlsProps) {
  return (
    <div className="flex items-center gap-4 px-4 py-2 bg-gray-900 border-t border-gray-800">
      <button
        onClick={onPlay}
        className="w-8 h-8 flex items-center justify-center bg-blue-600 hover:bg-blue-500 rounded-full text-sm"
      >
        {isPlaying ? '⏸' : '▶'}
      </button>
      <span className="text-xs text-gray-400 font-mono">
        {currentTime.toFixed(1)}s / {duration.toFixed(1)}s
      </span>
      <input
        type="range"
        min={0}
        max={duration}
        step={0.1}
        value={currentTime}
        onChange={(e) => onSeek(parseFloat(e.target.value))}
        className="flex-1 h-1 bg-gray-700 rounded-lg appearance-none cursor-pointer"
      />
    </div>
  )
}
