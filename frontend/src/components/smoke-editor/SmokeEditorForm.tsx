import { useState } from 'react'
import type { SmokeTimelineInput } from '../../api/smoke-editor'

interface Props {
  onSubmit: (input: SmokeTimelineInput) => void
  submitting: boolean
  assetUri: string
  onAssetUriChange: (uri: string) => void
}

export function SmokeEditorForm({ onSubmit, submitting, assetUri, onAssetUriChange }: Props) {
  const [projectId] = useState('proj-1')
  const [clipStart, setClipStart] = useState(0)
  const [clipEnd, setClipEnd] = useState(10)
  const [subtitleText, setSubtitleText] = useState('')
  const [effectKey, setEffectKey] = useState<string>('')
  const [blurRadius, setBlurRadius] = useState(5)
  const [vignetteIntensity, setVignetteIntensity] = useState(0.5)
  const [profile, setProfile] = useState('default_1080p')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()

    const effectParams: Record<string, unknown> = {}
    if (effectKey === 'video.blur') effectParams.radius = blurRadius
    if (effectKey === 'video.vignette') effectParams.intensity = vignetteIntensity

    onSubmit({
      projectId,
      assetUri,
      clipStart,
      clipEnd,
      subtitleText: subtitleText || undefined,
      effectKey: effectKey || undefined,
      effectParams: effectKey ? effectParams : undefined,
      profile,
    })
  }

  return (
    <form onSubmit={handleSubmit} className="rounded-lg border border-gray-800 bg-gray-900 p-4 space-y-4">
      <h2 className="text-lg font-semibold">Timeline Input</h2>

      <div>
        <label className="block text-sm text-gray-400 mb-1">Asset URI</label>
        <input
          type="text"
          value={assetUri}
          onChange={e => onAssetUriChange(e.target.value)}
          className="w-full rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm font-mono"
          placeholder="storage://videos/source.mp4"
          required
        />
        <p className="text-xs text-gray-600 mt-1">
          Select from Asset Browser or enter manually
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-sm text-gray-400 mb-1">Clip Start (s)</label>
          <input
            type="number"
            value={clipStart}
            onChange={e => setClipStart(Number(e.target.value))}
            className="w-full rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm"
            min={0}
          />
        </div>
        <div>
          <label className="block text-sm text-gray-400 mb-1">Clip End (s)</label>
          <input
            type="number"
            value={clipEnd}
            onChange={e => setClipEnd(Number(e.target.value))}
            className="w-full rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm"
            min={1}
          />
        </div>
      </div>

      <div>
        <label className="block text-sm text-gray-400 mb-1">Subtitle Text (optional)</label>
        <textarea
          value={subtitleText}
          onChange={e => setSubtitleText(e.target.value)}
          className="w-full rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm"
          rows={2}
          placeholder="Hello World"
        />
      </div>

      <div>
        <label className="block text-sm text-gray-400 mb-1">Effect (optional)</label>
        <select
          value={effectKey}
          onChange={e => setEffectKey(e.target.value)}
          className="w-full rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm"
        >
          <option value="">None</option>
          <option value="video.blur">Blur</option>
          <option value="video.vignette">Vignette</option>
        </select>
      </div>

      {effectKey === 'video.blur' && (
        <div>
          <label className="block text-sm text-gray-400 mb-1">Blur Radius: {blurRadius}</label>
          <input
            type="range"
            value={blurRadius}
            onChange={e => setBlurRadius(Number(e.target.value))}
            min={1}
            max={10}
            step={1}
            className="w-full"
          />
        </div>
      )}

      {effectKey === 'video.vignette' && (
        <div>
          <label className="block text-sm text-gray-400 mb-1">Vignette Intensity: {vignetteIntensity.toFixed(2)}</label>
          <input
            type="range"
            value={vignetteIntensity}
            onChange={e => setVignetteIntensity(Number(e.target.value))}
            min={0}
            max={1}
            step={0.05}
            className="w-full"
          />
        </div>
      )}

      <div>
        <label className="block text-sm text-gray-400 mb-1">Render Profile</label>
        <select
          value={profile}
          onChange={e => setProfile(e.target.value)}
          className="w-full rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm"
        >
          <option value="default_1080p">1080p (default)</option>
          <option value="default_720p">720p</option>
          <option value="social_1080p">Social 1080p</option>
        </select>
      </div>

      <button
        type="submit"
        disabled={submitting || !assetUri.trim()}
        className="w-full rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50"
      >
        {submitting ? 'Submitting...' : 'Submit Render Job'}
      </button>
    </form>
  )
}
