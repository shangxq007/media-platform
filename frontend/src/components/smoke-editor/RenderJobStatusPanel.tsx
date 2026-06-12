import type { SmokeJobStatus } from '../../api/smoke-editor'

interface Props {
  jobId: string | null
  status: SmokeJobStatus | null
  polling: boolean
}

export function RenderJobStatusPanel({ jobId, status, polling }: Props) {
  if (!jobId) {
    return (
      <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
        <h3 className="text-sm font-semibold text-gray-400">Render Job Status</h3>
        <p className="text-sm text-gray-500 mt-2">No job submitted yet.</p>
      </div>
    )
  }

  const statusColor = status?.status === 'COMPLETED'
    ? 'text-green-400'
    : status?.status === 'FAILED'
      ? 'text-red-400'
      : 'text-yellow-400'

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <h3 className="text-sm font-semibold text-gray-400 mb-3">Render Job Status</h3>
      <div className="space-y-2">
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Job ID</span>
          <span className="font-mono text-gray-200">{jobId}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-gray-500">Status</span>
          <span className={`font-semibold ${statusColor}`}>
            {status?.status ?? 'UNKNOWN'}
            {polling && ' (polling...)'}
          </span>
        </div>
        {status?.createdAt && (
          <div className="flex justify-between text-sm">
            <span className="text-gray-500">Created</span>
            <span className="text-gray-200">{status.createdAt}</span>
          </div>
        )}
        {status?.errorMessage && (
          <div className="mt-2 rounded border border-red-800 bg-red-950 p-2">
            <p className="text-xs text-red-300">{status.errorMessage}</p>
          </div>
        )}
      </div>
    </div>
  )
}
