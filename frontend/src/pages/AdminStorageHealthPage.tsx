import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import api from '../api/index'

const PROJECT_ID = 'prj_6802ca7a12c24aafa31cf77fa63890be'

interface OrphanReport {
  generatedAt: string
  reportOnly: boolean
  destructive: boolean
  limit: number
  issueCount: number
  issues: Array<{
    issueType: string
    severity: string
    entityType: string
    entityId: string
    status?: string
    message: string
    recommendedAction: string
    safeToAutoDelete: boolean
  }>
}

function useOrphanReport() {
  return useQuery({
    queryKey: ['storage-orphan-report', PROJECT_ID],
    queryFn: () => api.get(`/render/projects/${PROJECT_ID}/storage/orphan-report?limit=100`).then(r => r.data as OrphanReport),
  })
}

function SeverityBadge({ severity }: { severity: string }) {
  const colors: Record<string, string> = {
    'HIGH': 'bg-red-900 text-red-200',
    'MEDIUM': 'bg-yellow-900 text-yellow-200',
    'LOW': 'bg-gray-800 text-gray-300',
  }
  return (
    <span className={`inline-block rounded px-2 py-1 text-xs ${colors[severity] || 'bg-gray-800 text-gray-300'}`}>
      {severity}
    </span>
  )
}

function IssueTypeBadge({ issueType }: { issueType: string }) {
  return (
    <span className="inline-block rounded bg-gray-800 px-2 py-1 text-xs text-gray-300">
      {issueType.replace(/_/g, ' ')}
    </span>
  )
}

export default function AdminStorageHealthPage() {
  const { data: report, isLoading, error } = useOrphanReport()
  const [severityFilter, setSeverityFilter] = useState('ALL')

  const issues = report?.issues || []
  const filteredIssues = severityFilter === 'ALL'
    ? issues
    : issues.filter(i => i.severity === severityFilter)

  const highCount = issues.filter(i => i.severity === 'HIGH').length
  const mediumCount = issues.filter(i => i.severity === 'MEDIUM').length
  const lowCount = issues.filter(i => i.severity === 'LOW').length

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <div className="border-b border-gray-800 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold">Storage Health</h1>
            <p className="text-sm text-gray-400">Inspect StorageRuntime reference consistency</p>
          </div>
          <div className="flex gap-2">
            <span className="rounded bg-blue-900 px-2 py-1 text-xs text-blue-200">Admin Console</span>
            <span className="rounded bg-green-900 px-2 py-1 text-xs text-green-200">Report-only</span>
          </div>
        </div>
      </div>

      <div className="p-6">
        {/* Report-only Guarantee Banner */}
        <div className="mb-6 rounded-lg border border-green-800 bg-green-950 p-4">
          <div className="flex items-center gap-2">
            <span className="text-green-400">🔒</span>
            <div>
              <div className="font-semibold text-green-200">Report-only Guarantee</div>
              <div className="text-sm text-green-300">
                This page never deletes or mutates Product, Artifact, StorageReference, RAW_MEDIA, files, or remote objects.
              </div>
            </div>
          </div>
        </div>

        {/* Summary Cards */}
        <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
          {isLoading ? (
            <div className="col-span-4 text-center text-gray-400">Loading report...</div>
          ) : error ? (
            <div className="col-span-4 text-center text-red-400">Failed to load report</div>
          ) : (
            <>
              <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
                <div className="text-sm text-gray-400">Total Issues</div>
                <div className="text-2xl font-bold">{report?.issueCount || 0}</div>
              </div>
              <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
                <div className="text-sm text-gray-400">High Severity</div>
                <div className="text-2xl font-bold text-red-400">{highCount}</div>
              </div>
              <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
                <div className="text-sm text-gray-400">Medium Severity</div>
                <div className="text-2xl font-bold text-yellow-400">{mediumCount}</div>
              </div>
              <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
                <div className="text-sm text-gray-400">Low Severity</div>
                <div className="text-2xl font-bold text-gray-400">{lowCount}</div>
              </div>
            </>
          )}
        </div>

        {/* Filters */}
        <div className="mb-4 flex gap-2">
          {['ALL', 'HIGH', 'MEDIUM', 'LOW'].map(severity => (
            <button
              key={severity}
              onClick={() => setSeverityFilter(severity)}
              className={`rounded px-3 py-1 text-sm ${
                severityFilter === severity
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
              }`}
            >
              {severity}
            </button>
          ))}
        </div>

        {/* Issue Table */}
        {isLoading ? (
          <div className="text-center text-gray-400">Loading issues...</div>
        ) : filteredIssues.length === 0 ? (
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-6 text-center">
            <div className="mb-2 text-lg font-semibold">No storage consistency issues found</div>
            <p className="text-sm text-gray-400">
              All StorageRuntime references appear consistent.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-800">
                  <th className="p-2 text-left">Severity</th>
                  <th className="p-2 text-left">Issue Type</th>
                  <th className="p-2 text-left">Entity</th>
                  <th className="p-2 text-left">Message</th>
                  <th className="p-2 text-left">Action</th>
                </tr>
              </thead>
              <tbody>
                {filteredIssues.map((issue, i) => (
                  <tr key={i} className="border-b border-gray-800">
                    <td className="p-2"><SeverityBadge severity={issue.severity} /></td>
                    <td className="p-2"><IssueTypeBadge issueType={issue.issueType} /></td>
                    <td className="p-2 font-mono text-xs">{issue.entityType}: {issue.entityId?.slice(0, 16)}...</td>
                    <td className="p-2">{issue.message}</td>
                    <td className="p-2 text-xs text-gray-400">{issue.recommendedAction}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Output Cleanup Safety Summary */}
        <div className="mt-6 rounded-lg border border-gray-800 bg-gray-900 p-4">
          <h2 className="mb-2 font-semibold">Output Cleanup Safety</h2>
          <div className="grid grid-cols-2 gap-2 text-sm text-gray-400">
            <div>✓ Temp-root scoped only</div>
            <div>✓ Dry-run capable</div>
            <div>✓ Bounded cleanup</div>
            <div>✓ Never delete completed Product/Artifact</div>
            <div>✓ Never delete RAW_MEDIA</div>
            <div>✓ Never delete active EXECUTING outputs</div>
            <div>✓ Never follow symlinks</div>
            <div>✓ Never delete outside temp-root</div>
          </div>
        </div>

        {/* Links */}
        <div className="mt-4 flex gap-4 text-sm">
          <Link to="/admin/render-jobs" className="text-blue-400 hover:underline">Admin Render Jobs</Link>
          <Link to="/dev/timeline-git" className="text-blue-400 hover:underline">Dev Console</Link>
        </div>
      </div>
    </div>
  )
}
