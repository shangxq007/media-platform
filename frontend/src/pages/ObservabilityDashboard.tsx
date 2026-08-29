export function ObservabilityDashboard() {
  return (
    <div className="min-h-screen bg-gray-950 p-6 text-gray-100">
      <div className="mx-auto max-w-2xl">
        <h1 className="text-2xl font-bold">Render observability</h1>
        <div className="mt-6 rounded-lg border border-amber-800 bg-amber-950 p-5">
          <h2 className="font-semibold text-amber-200">Application projection required</h2>
          <p className="mt-2 text-sm text-amber-100">
            The backend does not currently expose the typed metrics, attempt, provider identity, runtime availability,
            and failure projections needed by this operator surface. No status, fallback, or provider decision is
            reconstructed from unscoped render endpoints.
          </p>
          <a href="/render-jobs" className="mt-4 inline-block text-sm font-medium text-blue-300 hover:text-blue-200">
            View scoped render jobs
          </a>
        </div>
      </div>
    </div>
  )
}
