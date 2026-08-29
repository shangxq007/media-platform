import type { RenderJobSummary } from '../../api/render-jobs'

interface Props {
  job: RenderJobSummary
}

export function JobDetail({ job }: Props) {
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

      <p className="mt-4 text-xs text-gray-500">
        Job actions will appear when the application projection reports the allowed actions for this attempt.
      </p>
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
