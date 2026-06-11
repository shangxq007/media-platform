import { useState } from 'react'
import { CaptionEditor } from '../editor/captions/CaptionEditor'
import { TemplateSelector } from '../editor/templates/TemplateSelector'
import { Inspector } from '../editor/inspector/Inspector'
import { RemotionPreview } from '../remotion/player/RemotionPreview'
import { PlaybackControls } from '../editor/playback/PlaybackControls'

export function EditorPage() {
  const [captions, setCaptions] = useState<Array<{
    id: string
    text: string
    startTime: number
    endTime: number
  }>>([])
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null)
  const [isPlaying, setIsPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)

  return (
    <div className="flex flex-col h-screen">
      <header className="flex items-center justify-between px-4 py-2 bg-gray-900 border-b border-gray-800">
        <h1 className="text-lg font-semibold">Video Editor</h1>
        <nav className="flex gap-4">
          <a href="/render-jobs" className="text-sm text-blue-400 hover:text-blue-300">Render Jobs</a>
          <a href="/capabilities" className="text-sm text-blue-400 hover:text-blue-300">Capabilities</a>
        </nav>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <aside className="w-64 bg-gray-900 border-r border-gray-800 overflow-y-auto">
          <TemplateSelector
            selected={selectedTemplate}
            onSelect={setSelectedTemplate}
          />
          <CaptionEditor
            captions={captions}
            onChange={setCaptions}
          />
        </aside>

        <main className="flex-1 flex flex-col overflow-hidden">
          <div className="flex-1 flex items-center justify-center bg-gray-950">
            <RemotionPreview
              captions={captions}
              templateId={selectedTemplate}
              isPlaying={isPlaying}
              currentTime={currentTime}
            />
          </div>
          <PlaybackControls
            isPlaying={isPlaying}
            currentTime={currentTime}
            duration={30}
            onPlay={() => setIsPlaying(!isPlaying)}
            onSeek={setCurrentTime}
          />
        </main>

        <aside className="w-72 bg-gray-900 border-l border-gray-800 overflow-y-auto">
          <Inspector
            captions={captions}
            selectedTemplate={selectedTemplate}
          />
        </aside>
      </div>
    </div>
  )
}
