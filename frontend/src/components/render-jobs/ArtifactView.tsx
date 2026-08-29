import type { RenderJobArtifactSummary } from '../../api/render-jobs'
import { ArtifactAccessAction } from '../../routes/app/renders/ArtifactAccessAction'

interface Props {
  artifacts: RenderJobArtifactSummary[]
  jobId: string
  onAccessRequest: (artifactId: string) => Promise<{ accessUrl: string; expiresAt?: string } | null>
}

export function ArtifactView({ artifacts, jobId, onAccessRequest }: Props) {
  if (artifacts.length === 0) {
    return (
      <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
        <h3 className="text-sm font-semibold text-gray-300 mb-2">Artifacts</h3>
        <p className="text-sm text-gray-500">No artifacts for job {jobId}.</p>
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <h3 className="text-sm font-semibold text-gray-300 mb-3">Artifacts</h3>
      <div className="space-y-3">
        {artifacts.map(artifact => (
          <ArtifactCard key={artifact.artifactId} artifact={artifact} onAccessRequest={onAccessRequest} />
        ))}
      </div>
    </div>
  )
}

function ArtifactCard({
  artifact,
  onAccessRequest,
}: {
  artifact: RenderJobArtifactSummary
  onAccessRequest: Props['onAccessRequest']
}) {
  return (
    <div className="rounded border border-gray-700 bg-gray-800 p-3">
      <div className="space-y-1 text-xs">
        <div className="flex justify-between">
          <span className="text-gray-500">ID</span>
          <span className="font-mono text-gray-200">{artifact.artifactId}</span>
        </div>
        {artifact.format && (
          <div className="flex justify-between">
            <span className="text-gray-500">Format</span>
            <span className="text-gray-200">{artifact.format}</span>
          </div>
        )}
        <div className="flex items-center justify-between pt-2">
          <span className="text-gray-500">Access</span>
          <ArtifactAccessAction
            artifactId={artifact.artifactId}
            contentType={contentTypeForFormat(artifact.format)}
            onAccessRequest={onAccessRequest}
          />
        </div>
      </div>
    </div>
  )
}

function contentTypeForFormat(format: string | null): string | undefined {
  if (!format) return undefined
  const f = format.toLowerCase()
  if (['mp4', 'webm', 'ogg', 'mov', 'mkv'].includes(f)) return `video/${f}`
  if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(f)) return `image/${f === 'jpg' ? 'jpeg' : f}`
  if (['mp3', 'wav', 'aac', 'flac', 'm4a'].includes(f)) return `audio/${f}`
  return undefined
}
