export function CapabilitiesPage() {
  return (
    <div className="p-6">
      <h1 className="text-xl font-bold mb-4">Capabilities</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {[
          { name: 'Render Job', status: 'stable', mode: 'async' },
          { name: 'Media Processing', status: 'stable', mode: 'async' },
          { name: 'Subtitle Render', status: 'stable', mode: 'async' },
          { name: 'Font API', status: 'beta', mode: 'sync' },
          { name: 'Artifact API', status: 'stable', mode: 'sync' },
        ].map((cap) => (
          <div key={cap.name} className="bg-gray-800 rounded p-4">
            <div className="font-medium">{cap.name}</div>
            <div className="flex gap-2 mt-2">
              <span className="text-xs px-2 py-0.5 bg-green-900 text-green-300 rounded">{cap.status}</span>
              <span className="text-xs px-2 py-0.5 bg-blue-900 text-blue-300 rounded">{cap.mode}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
