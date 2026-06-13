import { useState } from 'react'
import { SmokeEditorForm } from '../components/smoke-editor/SmokeEditorForm'
import { RenderJobStatusPanel } from '../components/smoke-editor/RenderJobStatusPanel'
import { AssetPicker } from '../components/assets/AssetPicker'
import { AssetPreview } from '../components/assets/AssetPreview'
import { ArtifactPreview } from '../components/artifacts/ArtifactPreview'
import { TimelineCanvas } from '../timeline/canvas/TimelineCanvas'
import { useTimelineInit } from '../timeline/hooks/useTimelineInit'
import type { AssetSummary } from '../hooks/useAssets'
import type { SmokeTimelineInput, SmokeJobStatus } from '../api/smoke-editor'
import { SmokeEditorAPI } from '../api/smoke-editor'

export function SmokeEditorPage() {
  const [projectId] = useState('proj-1')
  const [selectedAsset, setSelectedAsset] = useState<AssetSummary | null>(null)
  const [assetUri, setAssetUri] = useState('storage://videos/source.mp4')
  const [jobId, setJobId] = useState<string | null>(null)
  const [jobStatus, setJobStatus] = useState<SmokeJobStatus | null>(null)
  const [artifacts, setArtifacts] = useState<Array<{ id: string; storageUri: string; format?: string }>>([])
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [polling, setPolling] = useState(false)

  // Initialize timeline with demo data
  useTimelineInit()

  const handleAssetSelect = (asset: AssetSummary) => {
    setSelectedAsset(asset)
    setAssetUri(asset.storageKey)
  }

  const handleSubmit = async (input: SmokeTimelineInput) => {
    setError(null)
    setJobId(null)
    setJobStatus(null)
    setArtifacts([])
    setSubmitting(true)

    try {
      const result = await SmokeEditorAPI.submitRenderJob({
        ...input,
        assetUri,
      })
      setJobId(result.jobId)
      setJobStatus({ jobId: result.jobId, status: result.status })
      startPolling(result.jobId)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Submit failed')
    } finally {
      setSubmitting(false)
    }
  }

  const startPolling = (id: string) => {
    setPolling(true)
    const interval = setInterval(async () => {
      try {
        const status = await SmokeEditorAPI.getJobStatus(id)
        setJobStatus(status)

        if (status.status === 'COMPLETED' || status.status === 'FAILED') {
          clearInterval(interval)
          setPolling(false)

          if (status.status === 'COMPLETED') {
            try {
              const arts = await SmokeEditorAPI.getArtifacts(id)
              setArtifacts(arts)
            } catch {
              // artifact fetch failure is non-fatal
            }
          }
        }
      } catch {
        // poll failure is non-fatal, keep trying
      }
    }, 2000)

    // Auto-stop after 5 minutes
    setTimeout(() => {
      clearInterval(interval)
      setPolling(false)
    }, 300000)
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 p-6">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold">Smoke Editor</h1>
            <p className="text-gray-400 text-sm mt-1">
              Minimal render flow: browse assets → configure timeline → optional subtitle/effect → submit → view result.
            </p>
          </div>
          <a
            href="/render-jobs"
            className="rounded bg-gray-700 px-4 py-2 text-sm font-medium text-gray-200 hover:bg-gray-600"
          >
            View All Jobs
          </a>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left: Asset Picker + Preview */}
          <div className="space-y-4">
            <AssetPicker
              projectId={projectId}
              selectedAssetId={selectedAsset?.id ?? null}
              onSelect={handleAssetSelect}
            />
            <AssetPreview asset={selectedAsset} projectId={projectId} />
          </div>

          {/* Center: Editor Form */}
          <div>
            <SmokeEditorForm
              onSubmit={handleSubmit}
              submitting={submitting}
              assetUri={assetUri}
              onAssetUriChange={setAssetUri}
            />
          </div>

          {/* Right: Status + Artifacts */}
          <div className="space-y-4">
            <RenderJobStatusPanel jobId={jobId} status={jobStatus} polling={polling} />
            <ArtifactPreview artifacts={artifacts} />
            {error && (
              <div className="rounded-lg border border-red-800 bg-red-950 p-4">
                <h3 className="text-sm font-semibold text-red-400 mb-1">Error</h3>
                <p className="text-sm text-red-300">{error}</p>
              </div>
            )}
          </div>
        </div>

        {/* Timeline Canvas */}
        <div className="mt-6">
          <TimelineCanvas height={320} />
        </div>
      </div>
    </div>
  )
}
