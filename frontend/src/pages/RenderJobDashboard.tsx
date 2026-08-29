import { useEffect, useState } from 'react'
import { useRenderJobs, useRenderJob, useRenderWorkspaceScope } from '../api/render-jobs'
import { JobList } from '../components/render-jobs/JobList'
import { JobDetail } from '../components/render-jobs/JobDetail'

export function RenderJobDashboard() {
  const { data: scope, isLoading: scopeLoading, error: scopeError } = useRenderWorkspaceScope()
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null)
  const tenantId = scope?.tenantId ?? null

  useEffect(() => {
    if (!selectedProjectId && scope?.recentProjects[0]) {
      setSelectedProjectId(scope.recentProjects[0].id)
    }
  }, [scope, selectedProjectId])

  const { data: jobs, isLoading: jobsLoading, error: jobsError } = useRenderJobs(tenantId, selectedProjectId)
  const { data: selectedJob, error: selectedJobError } = useRenderJob(tenantId, selectedProjectId, selectedJobId)

  const selectProject = (projectId: string) => {
    setSelectedProjectId(projectId || null)
    setSelectedJobId(null)
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 p-6">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold">Render Jobs</h1>
            <p className="text-gray-400 text-sm mt-1">
              View render job history, status, and artifacts.
            </p>
          </div>
          {scope?.recentProjects.length ? (
            <label className="flex items-center gap-2 text-sm text-gray-400">
              Project
              <select
                aria-label="Project"
                value={selectedProjectId ?? ''}
                onChange={event => selectProject(event.target.value)}
                className="rounded border border-gray-700 bg-gray-900 px-3 py-2 text-gray-100"
              >
                {scope.recentProjects.map(project => (
                  <option key={project.id} value={project.id}>{project.name}</option>
                ))}
              </select>
            </label>
          ) : null}
        </div>

        {scopeError && (
          <div className="rounded-lg border border-red-800 bg-red-950 p-4 mb-4">
            <p className="text-sm text-red-300">Failed to load the authenticated render scope: {scopeError.message}</p>
          </div>
        )}

        {!scopeLoading && !scopeError && (!tenantId || scope?.recentProjects.length === 0) && (
          <div className="rounded-lg border border-amber-800 bg-amber-950 p-4 mb-4">
            <p className="text-sm text-amber-200">
              Render history needs an authenticated workspace with at least one project.
            </p>
          </div>
        )}

        {jobsError && (
          <div className="rounded-lg border border-red-800 bg-red-950 p-4 mb-4">
            <p className="text-sm text-red-300">Failed to load render jobs: {jobsError.message}</p>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left: Job List */}
          <div>
            {jobsLoading ? (
              <div className="rounded-lg border border-gray-800 bg-gray-900 p-8 text-center">
                <div className="text-gray-400">Loading jobs...</div>
              </div>
            ) : (
              <JobList
                jobs={jobs ?? []}
                selectedJobId={selectedJobId}
                onSelect={setSelectedJobId}
              />
            )}
          </div>

          {/* Center: Job Detail */}
          <div>
            {selectedJobError ? (
              <div className="rounded-lg border border-red-800 bg-red-950 p-4 text-sm text-red-300">
                Failed to load render job: {selectedJobError.message}
              </div>
            ) : selectedJob ? (
              <JobDetail job={selectedJob} />
            ) : (
              <div className="rounded-lg border border-gray-800 bg-gray-900 p-8 text-center">
                <div className="text-gray-500 text-sm">
                  {selectedJobId ? 'Loading job...' : 'Select a job to view details'}
                </div>
              </div>
            )}
          </div>

          {/* Right: Artifacts */}
          <div>
            {selectedJobId ? (
              <div className="rounded-lg border border-amber-800 bg-amber-950 p-4">
                <h3 className="text-sm font-semibold text-amber-100 mb-2">Artifacts unavailable</h3>
                <p className="text-sm text-amber-200">
                  A tenant/project-scoped redacted artifact summary is required before artifacts can be listed.
                </p>
              </div>
            ) : (
              <div className="rounded-lg border border-gray-800 bg-gray-900 p-8 text-center">
                <div className="text-gray-500 text-sm">Select a job to view artifacts</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
