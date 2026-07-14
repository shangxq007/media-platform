import type { RenderJobSummary } from '../../api/render-jobs'
import { useCancelRenderJob } from '../../api/render-jobs'

interface Props {
  job: RenderJobSummary
}

export function JobDetail({ job }: Props) {
  const cancelMutation = useCancelRenderJob()

  const canCancel = job.status === 'QUEUED' || job.status === 'PROCESSING'

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <h3 className="text-sm font-semibold text-gray-300 mb-3">Job Detail</h3>

      <div className="space-y-2 text-sm">
        <DetailRow label="Job ID" value={job.id} mono />
        <DetailRow label="Status" value={job.status} />
        <DetailRow label="Profile" value={job.profile} />
        <DetailRow label="Project" value={job.projectId} mono />
        <DetailRow label="Snapshot" value={job.timelineSnapshotId} mono />
      </div>

      {/* Actions */}
      <div className="flex gap-2 mt-4">
        {canCancel && (
          <button
            onClick={() => cancelMutation.mutate(job.id)}
            disabled={cancelMutation.isPending}
            className="flex-1 rounded bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-500 disabled:opacity-50"
          >
            {cancelMutation.isPending ? 'Cancelling...' : 'Cancel'}
          </button>
        )}
      </div>

      {cancelMutation.isError && (
        <p className="text-xs text-red-400 mt-2">Cancel failed: {cancelMutation.error?.message}</p>
      )}
    </div>
  )
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex justify-between">
      <span className="text-gray-500">{label}</span>
      <span className={`text-gray-200 ${mono ? 'font-mono text-xs' : ''}`}>{value || '—'}</span>
    </div>
  )
}
