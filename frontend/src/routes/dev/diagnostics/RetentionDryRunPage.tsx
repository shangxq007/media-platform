import React from 'react'

export function RetentionDryRunPage() {
  return (
    <div style={{ padding: '20px' }}>
      <h2 style={{ color: '#58a6ff' }}>Retention Dry-run</h2>
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
        <p style={{ color: '#8b949e' }}><strong>Status:</strong> READY_WITH_LIMITS</p>
        <p style={{ color: '#8b949e' }}><strong>Contract:</strong> dev.retentionDryRun</p>
        <p style={{ color: '#8b949e' }}><strong>API Client:</strong> dev.retentionDryRun</p>
        <p style={{ color: '#8b949e' }}><strong>Query Key:</strong> dev.retentionDryRun</p>
        <p style={{ color: '#8b949e' }}><strong>Cache:</strong> manual refresh preferred, no auto-poll</p>
        <p style={{ color: '#f85149', marginTop: '12px' }}>
          <strong>Retention dry-run is DEV_ONLY and no-mutation.</strong> Cleanup runtime and scheduler are not implemented.
        </p>
      </div>
    </div>
  )
}
