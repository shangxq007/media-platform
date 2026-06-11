import { useQuery } from '@tanstack/react-query'

export function RenderJobsPage() {
  const { data: jobs, isLoading } = useQuery({
    queryKey: ['render-jobs'],
    queryFn: async () => {
      const res = await fetch('/api/v1/render-jobs')
      if (!res.ok) throw new Error('Failed to fetch jobs')
      return res.json()
    },
  })

  return (
    <div className="p-6">
      <h1 className="text-xl font-bold mb-4">Render Jobs</h1>
      {isLoading ? (
        <div className="text-gray-400">Loading...</div>
      ) : (
        <div className="space-y-2">
          {(jobs ?? []).map((job: { id: string; status: string; jobType: string }) => (
            <div key={job.id} className="bg-gray-800 rounded p-3 flex justify-between items-center">
              <div>
                <div className="text-sm font-mono">{job.id}</div>
                <div className="text-xs text-gray-400">{job.jobType}</div>
              </div>
              <span className="text-xs px-2 py-1 bg-gray-700 rounded">{job.status}</span>
            </div>
          ))}
          {(jobs ?? []).length === 0 && (
            <div className="text-gray-500 text-sm">No render jobs yet</div>
          )}
        </div>
      )}
    </div>
  )
}
