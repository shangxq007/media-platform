interface CaptionEditorProps {
  captions: Array<{ id: string; text: string; startTime: number; endTime: number }>
  onChange: (captions: Array<{ id: string; text: string; startTime: number; endTime: number }>) => void
}

export function CaptionEditor({ captions, onChange }: CaptionEditorProps) {
  const addCaption = () => {
    onChange([...captions, {
      id: `cap-${Date.now()}`,
      text: 'New caption',
      startTime: 0,
      endTime: 3,
    }])
  }

  return (
    <div className="p-3 border-b border-gray-800">
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-sm font-medium">Captions</h3>
        <button
          onClick={addCaption}
          className="text-xs px-2 py-1 bg-blue-600 hover:bg-blue-500 rounded"
        >
          Add
        </button>
      </div>
      <div className="space-y-2 max-h-48 overflow-y-auto">
        {captions.map((cap) => (
          <div key={cap.id} className="text-xs bg-gray-800 rounded p-2">
            <div className="text-gray-400">
              {cap.startTime.toFixed(1)}s - {cap.endTime.toFixed(1)}s
            </div>
            <div className="mt-1">{cap.text}</div>
          </div>
        ))}
        {captions.length === 0 && (
          <div className="text-xs text-gray-500 italic">No captions yet</div>
        )}
      </div>
    </div>
  )
}
