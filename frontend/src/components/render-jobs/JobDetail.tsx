import type { RenderJobSummary } from '../../contracts/app/render-job'

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
        Read-only platform job record. This status does not describe provider outcomes or Artifact availability.
      </p>
    </div>
  )
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="grid min-w-0 gap-1 sm:grid-cols-[5rem_minmax(0,1fr)] sm:gap-3">
      <span className="text-gray-500">{label}</span>
      <span className={`min-w-0 break-all text-gray-200 sm:text-right ${mono ? 'font-mono text-xs' : ''}`}>{value || '—'}</span>
    </div>
  )
}
