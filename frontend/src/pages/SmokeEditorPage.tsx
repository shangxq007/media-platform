import { useState } from 'react'
import { SmokeEditorForm } from '../components/smoke-editor/SmokeEditorForm'
import { RenderJobStatusPanel } from '../components/smoke-editor/RenderJobStatusPanel'
import { ArtifactPanel } from '../components/smoke-editor/ArtifactPanel'
import type { SmokeTimelineInput, SmokeJobStatus } from '../api/smoke-editor'
import { SmokeEditorAPI } from '../api/smoke-editor'

export function SmokeEditorPage() {
  const [jobId, setJobId] = useState<string | null>(null)
  const [jobStatus, setJobStatus] = useState<SmokeJobStatus | null>(null)
  const [artifacts, setArtifacts] = useState<Array<{ id: string; storageUri: string; format?: string }>>([])
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [polling, setPolling] = useState(false)

  const handleSubmit = async (input: SmokeTimelineInput) => {
    setError(null)
    setJobId(null)
    setJobStatus(null)
    setArtifacts([])
    setSubmitting(true)

    try {
      const result = await SmokeEditorAPI.submitRenderJob(input)
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
      <div className="max-w-4xl mx-auto">
        <h1 className="text-2xl font-bold mb-6">Smoke Editor</h1>
        <p className="text-gray-400 mb-8">
          Minimal render flow: create timeline → add asset/clip → optional subtitle/effect → submit render job → view status.
        </p>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div>
            <SmokeEditorForm onSubmit={handleSubmit} submitting={submitting} />
          </div>
          <div className="space-y-4">
            <RenderJobStatusPanel jobId={jobId} status={jobStatus} polling={polling} />
            <ArtifactPanel artifacts={artifacts} />
            {error && (
              <div className="rounded-lg border border-red-800 bg-red-950 p-4">
                <h3 className="text-sm font-semibold text-red-400 mb-1">Error</h3>
                <p className="text-sm text-red-300">{error}</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
