import React from 'react'

// Internal dev diagnostics hub - read-only, no mutation
export function DevDiagnosticsHubPage() {
  return (
    <div style={{ padding: '20px', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ color: '#58a6ff', marginBottom: '24px' }}>🔧 Dev Diagnostics Hub</h1>
      
      <div style={{ 
        background: '#161b22', 
        border: '1px solid #30363d', 
        borderRadius: '8px', 
        padding: '16px', 
        marginBottom: '16px' 
      }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Architecture Guard</h2>
        <p style={{ color: '#8b949e' }}>
          <strong>CI Status:</strong> <span style={{ color: '#3fb950' }}>✅ Active</span>
        </p>
        <p style={{ color: '#8b949e' }}>
          <strong>Local Command:</strong> <code>bash scripts/check-architecture-drift.sh</code>
        </p>
        <p style={{ color: '#8b949e' }}>
          <strong>Workflow:</strong> <code>.github/workflows/architecture-drift.yml</code>
        </p>
      </div>

      <div style={{ 
        background: '#161b22', 
        border: '1px solid #30363d', 
        borderRadius: '8px', 
        padding: '16px', 
        marginBottom: '16px' 
      }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Storage Delivery Profile</h2>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Registry:</strong> <span style={{ color: '#3fb950' }}>✅ Implemented</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Read-only:</strong> <span style={{ color: '#3fb950' }}>✅ Yes</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Canonical Profiles:</strong> <span style={{ color: '#58a6ff' }}>8</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Default Profile:</strong> <code>preview-r2-signed-url</code>
            </p>
          </div>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Runtime Switching:</strong> <span style={{ color: '#f85149' }}>❌ NOT_IMPLEMENTED</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Provider Selection:</strong> <span style={{ color: '#f85149' }}>❌ NO</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Remote Calls:</strong> <span style={{ color: '#3fb950' }}>✅ NO</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>R2 Path:</strong> <span style={{ color: '#3fb950' }}>✅ UNCHANGED</span>
            </p>
          </div>
        </div>
        <p style={{ color: '#8b949e', marginTop: '12px' }}>
          <em>Note: Backend /dev/storage-delivery-profiles endpoint not yet wired. Data shown is static architecture status.</em>
        </p>
      </div>

      <div style={{ 
        background: '#161b22', 
        border: '1px solid #30363d', 
        borderRadius: '8px', 
        padding: '16px', 
        marginBottom: '16px' 
      }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Ingest Preflight Policy</h2>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Evaluator:</strong> <span style={{ color: '#3fb950' }}>✅ Report-only</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Fail-open:</strong> <span style={{ color: '#3fb950' }}>✅ Required</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Enforce Mode:</strong> <span style={{ color: '#f85149' }}>❌ NOT_ENABLED</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Upload Rejection:</strong> <span style={{ color: '#f85149' }}>❌ NOT_IMPLEMENTED</span>
            </p>
          </div>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Persistence:</strong> <span style={{ color: '#f85149' }}>❌ NOT_IMPLEMENTED</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Public Response Changed:</strong> <span style={{ color: '#3fb950' }}>✅ NO</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>Raw Metadata Exposure:</strong> <span style={{ color: '#3fb950' }}>✅ NO</span>
            </p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}>
              <strong>OCR/Full Text:</strong> <span style={{ color: '#f85149' }}>❌ DISABLED</span>
            </p>
          </div>
        </div>

        <h3 style={{ color: '#58a6ff', marginTop: '16px', marginBottom: '8px' }}>Decision Semantics</h3>
        <table style={{ width: '100%', borderCollapse: 'collapse', color: '#e6edf3' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #30363d' }}>
              <th style={{ textAlign: 'left', padding: '8px', color: '#bc8cff' }}>Decision</th>
              <th style={{ textAlign: 'left', padding: '8px', color: '#bc8cff' }}>Behavior</th>
            </tr>
          </thead>
          <tbody>
            <tr style={{ borderBottom: '1px solid #21262d' }}>
              <td style={{ padding: '8px' }}><code>ACCEPT</code></td>
              <td style={{ padding: '8px' }}>Non-blocking</td>
            </tr>
            <tr style={{ borderBottom: '1px solid #21262d' }}>
              <td style={{ padding: '8px' }}><code>ACCEPT_WITH_WARNINGS</code></td>
              <td style={{ padding: '8px' }}>Non-blocking</td>
            </tr>
            <tr style={{ borderBottom: '1px solid #21262d' }}>
              <td style={{ padding: '8px' }}><code>REJECT_CANDIDATE</code></td>
              <td style={{ padding: '8px' }}>Diagnostic only, non-blocking</td>
            </tr>
            <tr style={{ borderBottom: '1px solid #21262d' }}>
              <td style={{ padding: '8px' }}><code>ERROR_FAIL_OPEN</code></td>
              <td style={{ padding: '8px' }}>Fail-open, upload continues</td>
            </tr>
            <tr>
              <td style={{ padding: '8px' }}><code>REJECT</code></td>
              <td style={{ padding: '8px' }}>Not emitted by report-only evaluator</td>
            </tr>
          </tbody>
        </table>

        <p style={{ color: '#8b949e', marginTop: '12px' }}>
          <em>Note: Backend /dev/ingest/preflight-policy endpoint not yet wired. Data shown is static architecture status.</em>
        </p>
      </div>

      <div style={{ 
        background: '#161b22', 
        border: '1px solid #30363d', 
        borderRadius: '8px', 
        padding: '16px' 
      }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Architecture References</h2>
        <ul style={{ color: '#8b949e', margin: 0, paddingLeft: '20px' }}>
          <li><a href="https://r2.scribe.cc.cd/architecture/maps/latest/" style={{ color: '#58a6ff' }} target="_blank" rel="noopener">LikeC4 Architecture Views</a></li>
          <li><a href="https://github.com/shangxq007/media-platform/blob/main/docs/architecture/current/architecture-assertions.md" style={{ color: '#58a6ff' }} target="_blank" rel="noopener">Architecture Assertions</a></li>
          <li><a href="https://github.com/shangxq007/media-platform/blob/main/docs/architecture/current/architecture-drift-guard.md" style={{ color: '#58a6ff' }} target="_blank" rel="noopener">Architecture Drift Guard</a></li>
        </ul>
      </div>
    </div>
  )
}
