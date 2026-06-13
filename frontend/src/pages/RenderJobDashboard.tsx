import { useState } from 'react'
import { useRenderJobs, useRenderJob, useRenderJobArtifacts } from '../api/render-jobs'
import { JobList } from '../components/render-jobs/JobList'
import { JobDetail } from '../components/render-jobs/JobDetail'
import { ArtifactView } from '../components/render-jobs/ArtifactView'

export function RenderJobDashboard() {
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null)

  const { data: jobs, isLoading: jobsLoading, error: jobsError } = useRenderJobs()
  const { data: selectedJob } = useRenderJob(selectedJobId)
  const { data: artifacts } = useRenderJobArtifacts(selectedJobId)

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
          <a
            href="/smoke-editor"
            className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
          >
            New Render Job
          </a>
        </div>

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
            {selectedJob ? (
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
              <ArtifactView artifacts={artifacts ?? []} jobId={selectedJobId} />
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
