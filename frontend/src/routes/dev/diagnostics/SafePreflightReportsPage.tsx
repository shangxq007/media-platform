import React from 'react'

export function SafePreflightReportsPage() {
  return (
    <div style={{ padding: '20px' }}>
      <h2 style={{ color: '#58a6ff' }}>Safe Preflight Reports</h2>
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
        <p style={{ color: '#8b949e' }}><strong>Status:</strong> READY_WITH_LIMITS</p>
        <p style={{ color: '#8b949e' }}><strong>Contract:</strong> dev.safePreflightReports</p>
        <p style={{ color: '#8b949e' }}><strong>API Client:</strong> dev.safePreflightReports</p>
        <p style={{ color: '#8b949e' }}><strong>Query Key:</strong> dev.safePreflightReports</p>
        <p style={{ color: '#8b949e' }}><strong>Cache:</strong> short staleTime, no auto-poll</p>
        <p style={{ color: '#f85149', marginTop: '12px' }}>
          <strong>Safe preflight persistence is DEV_ONLY and PAUSED.</strong> This route is for future internal diagnostics only.
        </p>
      </div>
    </div>
  )
}
