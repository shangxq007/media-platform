export function SmokeEditorPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 p-6">
      <div className="mx-auto max-w-2xl">
        <h1 className="text-2xl font-bold">Create render</h1>
        <div className="mt-6 rounded-lg border border-amber-800 bg-amber-950 p-5">
          <h2 className="font-semibold text-amber-200">Canonical authoring projection required</h2>
          <p className="mt-2 text-sm text-amber-100">
            Render submission is unavailable because the application API does not yet expose a typed Timeline
            authoring contract for immutable media bindings, exact media time, and temporal mapping.
          </p>
          <p className="mt-2 text-sm text-amber-100">
            Existing render attempts remain available in Render Jobs. Raw storage locations are never accepted here.
          </p>
          <a href="/render-jobs" className="mt-4 inline-block text-sm font-medium text-blue-300 hover:text-blue-200">
            View render jobs
          </a>
        </div>
      </div>
    </div>
  )
}
