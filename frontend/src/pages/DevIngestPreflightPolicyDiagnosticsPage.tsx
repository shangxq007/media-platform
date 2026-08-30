import React from 'react'
import { useQuery } from '@tanstack/react-query'
import { devDiagnosticsClient } from '../api/dev'

export function DevIngestPreflightPolicyDiagnosticsPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['dev', 'ingest-preflight-policy-diagnostics'],
    queryFn: async () => {
      const result = await devDiagnosticsClient.getIngestPreflightPolicy()
      if (!result.success) throw new Error(result.error.message)
      return result.data
    },
  })

  if (isLoading) return <div style={{ padding: '20px', color: '#8b949e' }}>Loading...</div>
  if (error) return <div style={{ padding: '20px', color: '#f85149' }}>Error: {error.message}</div>
  if (!data) return <div style={{ padding: '20px', color: '#8b949e' }}>No data</div>

  return (
    <div style={{ padding: '20px', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ color: '#58a6ff', marginBottom: '24px' }}>🔍 Ingest Preflight Policy Diagnostics</h1>
      
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Safety Summary</h2>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>READ_ONLY</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>INTERNAL_ONLY</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>REPORT_ONLY</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>FAIL_OPEN</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NEVER_REJECTS</span>
          <span style={{ background: '#f85149', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NO_ENFORCE_MODE</span>
          <span style={{ background: '#f85149', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NO_UPLOAD_REJECTION</span>
          <span style={{ background: '#f85149', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NO_PERSISTENCE</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NO_RAW_METADATA</span>
        </div>
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Runtime Boundary</h2>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Report-only Evaluator:</strong> <span style={{ color: '#3fb950' }}>✅ Implemented</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Hook Integration:</strong> <span style={{ color: '#3fb950' }}>✅ Implemented</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Config Binding:</strong> <span style={{ color: '#3fb950' }}>✅ Implemented</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Report-only Mode:</strong> <span style={{ color: '#3fb950' }}>✅ true</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Fail-open Required:</strong> <span style={{ color: '#3fb950' }}>✅ true</span></p>
          </div>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Enforce Mode:</strong> <span style={{ color: '#f85149' }}>❌ NOT_ENABLED</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Upload Rejection:</strong> <span style={{ color: '#f85149' }}>❌ NOT_IMPLEMENTED</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Runtime Policy Gate:</strong> <span style={{ color: '#f85149' }}>❌ NOT_IMPLEMENTED</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Persistence:</strong> <span style={{ color: '#f85149' }}>❌ NOT_IMPLEMENTED</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Public Response Changed:</strong> <span style={{ color: '#3fb950' }}>✅ NO</span></p>
          </div>
        </div>
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Config Diagnostics</h2>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Mode:</strong> <code>{data.config.mode}</code></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Profile:</strong> <code>{data.config.profile}</code></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Fail-open:</strong> {data.config.failOpen ? '✅ true' : '❌ false'}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Max Findings:</strong> {data.config.maxFindings}</p>
          </div>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Validation:</strong> {data.config.validationStatus === 'VALID' ? '✅ VALID' : '❌ INVALID'}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Errors:</strong> {data.config.validationErrorCount}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Warnings:</strong> {data.config.validationWarningCount}</p>
          </div>
        </div>
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Decision Semantics</h2>
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
              <td style={{ padding: '8px' }}>{data.decisionSemantics.accept}</td>
            </tr>
            <tr style={{ borderBottom: '1px solid #21262d' }}>
              <td style={{ padding: '8px' }}><code>ACCEPT_WITH_WARNINGS</code></td>
              <td style={{ padding: '8px' }}>{data.decisionSemantics.acceptWithWarnings}</td>
            </tr>
            <tr style={{ borderBottom: '1px solid #21262d' }}>
              <td style={{ padding: '8px' }}><code>REJECT_CANDIDATE</code></td>
              <td style={{ padding: '8px' }}>{data.decisionSemantics.rejectCandidate}</td>
            </tr>
            <tr style={{ borderBottom: '1px solid #21262d' }}>
              <td style={{ padding: '8px' }}><code>ERROR_FAIL_OPEN</code></td>
              <td style={{ padding: '8px' }}>{data.decisionSemantics.errorFailOpen}</td>
            </tr>
            <tr>
              <td style={{ padding: '8px' }}><code>REJECT</code></td>
              <td style={{ padding: '8px' }}>{data.decisionSemantics.reject}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>References</h2>
        <ul style={{ color: '#8b949e', margin: 0, paddingLeft: '20px' }}>
          <li><a href="/dev/diagnostics" style={{ color: '#58a6ff' }}>← Back to Diagnostics Hub</a></li>
          <li><a href="https://github.com/shangxq007/media-platform/blob/main/docs/ingest/preflight-policy-evaluator-readonly-diagnostics.md" style={{ color: '#58a6ff' }} target="_blank" rel="noopener">Ingest Policy Diagnostics Docs</a></li>
          <li><a href="https://github.com/shangxq007/media-platform/blob/main/docs/architecture/current/architecture-assertions.md" style={{ color: '#58a6ff' }} target="_blank" rel="noopener">Architecture Assertions</a></li>
        </ul>
      </div>
    </div>
  )
}
