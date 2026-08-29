export function CapabilitiesPage() {
  return (
    <div className="p-6">
      <h1 className="text-xl font-bold mb-4">Access and runtime availability</h1>
      <div className="max-w-2xl rounded border border-amber-800 bg-amber-950 p-4">
        <h2 className="font-medium text-amber-200">Effective access projection unavailable</h2>
        <p className="mt-2 text-sm text-amber-100">
          This page will show capability existence, runtime availability, entitlement, policy permission, and quota
          as separate backend decisions. The current API does not provide that projection, so no access decision is
          inferred from subscription names, feature labels, or local runtime checks.
        </p>
      </div>
    </div>
  )
}
