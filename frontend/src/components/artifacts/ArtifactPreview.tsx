interface Artifact {
  id: string
  storageUri: string
  format?: string
}

interface Props {
  artifacts: Artifact[]
}

export function ArtifactPreview({ artifacts }: Props) {
  if (artifacts.length === 0) {
    return (
      <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
        <h3 className="text-sm font-semibold text-gray-400">Artifacts</h3>
        <p className="text-sm text-gray-500 mt-2">No artifacts yet.</p>
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <h3 className="text-sm font-semibold text-gray-400 mb-3">Artifacts</h3>
      <div className="space-y-3">
        {artifacts.map(artifact => (
          <ArtifactCard key={artifact.id} artifact={artifact} />
        ))}
      </div>
    </div>
  )
}

function ArtifactCard({ artifact }: { artifact: Artifact }) {
  const mediaType = guessMediaType(artifact.format)
  const previewUrl = artifact.storageUri

  return (
    <div className="rounded border border-gray-700 bg-gray-800 p-3">
      <div className="rounded overflow-hidden bg-gray-950 mb-2">
        {mediaType === 'video' && (
          <video
            controls
            src={previewUrl}
            className="w-full max-h-48 object-contain"
            preload="metadata"
          >
            Your browser does not support video playback.
          </video>
        )}

        {mediaType === 'image' && (
          <img
            src={previewUrl}
            alt={artifact.id}
            className="w-full max-h-48 object-contain"
          />
        )}

        {mediaType === 'audio' && (
          <audio controls src={previewUrl} className="w-full">
            Your browser does not support audio playback.
          </audio>
        )}

        {mediaType === 'unknown' && (
          <div className="flex items-center justify-center h-16 text-gray-500 text-sm">
            No preview available
          </div>
        )}
      </div>

      <div className="space-y-1 text-xs">
        <div className="flex justify-between">
          <span className="text-gray-500">ID</span>
          <span className="font-mono text-gray-200">{artifact.id}</span>
        </div>
        {artifact.format && (
          <div className="flex justify-between">
            <span className="text-gray-500">Format</span>
            <span className="text-gray-200">{artifact.format}</span>
          </div>
        )}
        <div className="flex justify-between">
          <span className="text-gray-500">URI</span>
          <a
            href={previewUrl}
            target="_blank"
            rel="noreferrer noopener"
            className="text-blue-400 hover:text-blue-300 font-mono text-xs truncate max-w-48"
          >
            Open ↗
          </a>
        </div>
      </div>
    </div>
  )
}

function guessMediaType(format?: string): 'video' | 'image' | 'audio' | 'unknown' {
  if (!format) return 'unknown'
  const f = format.toLowerCase()
  if (['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv'].includes(f)) return 'video'
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp'].includes(f)) return 'image'
  if (['mp3', 'wav', 'ogg', 'aac', 'flac', 'm4a'].includes(f)) return 'audio'
  return 'unknown'
}
