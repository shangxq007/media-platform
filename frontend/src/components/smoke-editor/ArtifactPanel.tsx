interface Props {
  artifacts: Array<{ id: string; storageUri: string; format?: string }>
}

export function ArtifactPanel({ artifacts }: Props) {
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
      <div className="space-y-2">
        {artifacts.map(artifact => (
          <div key={artifact.id} className="rounded border border-gray-700 bg-gray-800 p-3">
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">ID</span>
              <span className="font-mono text-gray-200">{artifact.id}</span>
            </div>
            <div className="flex justify-between text-sm mt-1">
              <span className="text-gray-500">URI</span>
              <span className="font-mono text-gray-200 break-all text-xs">{artifact.storageUri}</span>
            </div>
            {artifact.format && (
              <div className="flex justify-between text-sm mt-1">
                <span className="text-gray-500">Format</span>
                <span className="text-gray-200">{artifact.format}</span>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
