import { useState } from 'react'
import { useAssets, assetToSummary, type AssetSummary } from '../../hooks/useAssets'

interface Props {
  projectId: string
  selectedAssetId: string | null
  onSelect: (asset: AssetSummary) => void
}

export function AssetPicker({ projectId, selectedAssetId, onSelect }: Props) {
  const [manualUri, setManualUri] = useState('')
  const [showManual, setShowManual] = useState(false)

  const { data: assets, isLoading, error } = useAssets(projectId)

  const summaries = (assets ?? []).map(assetToSummary)

  const handleManualSubmit = () => {
    if (manualUri.trim()) {
      onSelect({
        id: 'manual-' + Date.now(),
        name: manualUri.split('/').pop() || 'Manual Asset',
        mediaType: 'UNKNOWN',
        storageKey: manualUri.trim(),
      })
    }
  }

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-300">Asset Browser</h3>
        <button
          type="button"
          onClick={() => setShowManual(!showManual)}
          className="text-xs text-blue-400 hover:text-blue-300"
        >
          {showManual ? 'Browse' : 'Enter URI manually'}
        </button>
      </div>

      {showManual ? (
        <div className="flex gap-2">
          <input
            type="text"
            value={manualUri}
            onChange={e => setManualUri(e.target.value)}
            placeholder="storage://path/to/asset.mp4"
            className="flex-1 rounded border border-gray-700 bg-gray-800 px-3 py-2 text-sm"
            onKeyDown={e => e.key === 'Enter' && handleManualSubmit()}
          />
          <button
            type="button"
            onClick={handleManualSubmit}
            className="rounded bg-blue-600 px-3 py-2 text-sm hover:bg-blue-500"
          >
            Use
          </button>
        </div>
      ) : (
        <>
          {isLoading && (
            <div className="text-sm text-gray-500 py-4 text-center">Loading assets...</div>
          )}

          {error && (
            <div className="text-sm text-red-400 py-4 text-center">
              Failed to load assets: {error.message}
            </div>
          )}

          {!isLoading && !error && summaries.length === 0 && (
            <div className="text-sm text-gray-500 py-4 text-center">
              No assets found for project {projectId}
            </div>
          )}

          {!isLoading && !error && summaries.length > 0 && (
            <div className="space-y-1 max-h-48 overflow-y-auto">
              {summaries.map(asset => (
                <button
                  key={asset.id}
                  type="button"
                  onClick={() => onSelect(asset)}
                  className={`w-full text-left rounded px-3 py-2 text-sm transition-colors ${
                    selectedAssetId === asset.id
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{asset.name}</span>
                    <span className="text-xs text-gray-500">{asset.mediaType}</span>
                  </div>
                  {asset.sizeBytes && (
                    <div className="text-xs text-gray-500 mt-0.5">
                      {formatBytes(asset.sizeBytes)}
                      {asset.width && asset.height && ` · ${asset.width}×${asset.height}`}
                      {asset.durationMs && ` · ${(asset.durationMs / 1000).toFixed(1)}s`}
                    </div>
                  )}
                </button>
              ))}
            </div>
          )}
        </>
      )}

      {selectedAssetId && !showManual && (
        <div className="mt-2 text-xs text-gray-500">
          Selected: <span className="text-gray-300">{summaries.find(a => a.id === selectedAssetId)?.name ?? selectedAssetId}</span>
        </div>
      )}
    </div>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB'
}
