import { useAssetPreviewUrl, type AssetSummary } from '../../hooks/useAssets'

interface Props {
  asset: AssetSummary | null
  projectId: string
}

export function AssetPreview({ asset, projectId }: Props) {
  const { data: previewUrl } = useAssetPreviewUrl(projectId, asset?.id ?? null)

  if (!asset) {
    return (
      <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
        <h3 className="text-sm font-semibold text-gray-400 mb-2">Asset Preview</h3>
        <p className="text-sm text-gray-500">Select an asset to preview.</p>
      </div>
    )
  }

  // Use preview URL from API, fall back to storageKey display
  const displayUrl = previewUrl || null

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <h3 className="text-sm font-semibold text-gray-400 mb-2">Asset Preview</h3>

      <div className="rounded overflow-hidden bg-gray-950 mb-3">
        {asset.mediaType === 'VIDEO' && displayUrl && (
          <video
            controls
            src={displayUrl}
            className="w-full max-h-48 object-contain"
            preload="metadata"
          >
            Your browser does not support video playback.
          </video>
        )}

        {asset.mediaType === 'IMAGE' && displayUrl && (
          <img
            src={displayUrl}
            alt={asset.name}
            className="w-full max-h-48 object-contain"
          />
        )}

        {asset.mediaType === 'AUDIO' && displayUrl && (
          <audio controls src={displayUrl} className="w-full">
            Your browser does not support audio playback.
          </audio>
        )}

        {(!displayUrl || asset.mediaType === 'UNKNOWN' || asset.mediaType === 'SUBTITLE') && (
          <div className="flex items-center justify-center h-24 text-gray-500 text-sm">
            {displayUrl ? 'Preview not available for this type' : 'Loading preview...'}
          </div>
        )}
      </div>

      <div className="space-y-1 text-xs">
        <div className="flex justify-between">
          <span className="text-gray-500">Name</span>
          <span className="text-gray-200">{asset.name}</span>
        </div>
        <div className="flex justify-between">
          <span className="text-gray-500">Type</span>
          <span className="text-gray-200">{asset.mediaType}</span>
        </div>
        {asset.width && asset.height && (
          <div className="flex justify-between">
            <span className="text-gray-500">Resolution</span>
            <span className="text-gray-200">{asset.width}×{asset.height}</span>
          </div>
        )}
        {asset.durationMs && (
          <div className="flex justify-between">
            <span className="text-gray-500">Duration</span>
            <span className="text-gray-200">{(asset.durationMs / 1000).toFixed(1)}s</span>
          </div>
        )}
        {asset.sizeBytes && (
          <div className="flex justify-between">
            <span className="text-gray-500">Size</span>
            <span className="text-gray-200">{formatBytes(asset.sizeBytes)}</span>
          </div>
        )}
      </div>
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}
