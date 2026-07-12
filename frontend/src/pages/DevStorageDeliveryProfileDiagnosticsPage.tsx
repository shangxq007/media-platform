import React, { useState, useEffect } from 'react'

interface StorageDeliveryProfileDiagnosticsResponse {
  diagnosticsMode: string
  runtimeSwitchingImplemented: boolean
  artifactAccessUsesRegistry: boolean
  providerSelectionUsesRegistry: boolean
  remoteCallsPerformed: boolean
  defaultProfileId: string
  profileCount: number
  profileIds: string[]
  enabledProfileIds: string[]
  runtimeSelectableProfileIds: string[]
  profiles: StorageDeliveryProfileDiagnosticsItem[]
  validation: StorageDeliveryProfileValidationDiagnostics
  generatedAt: string
}

interface StorageDeliveryProfileDiagnosticsItem {
  profileId: string
  status: string
  accessMode: string
  backendType: string
  providerType: string
  enabled: boolean
  runtimeSelectable: boolean
  userFacingAllowed: boolean
  capabilities: Record<string, boolean>
  securityPolicy: Record<string, boolean>
  validationStatus: string
}

interface StorageDeliveryProfileValidationDiagnostics {
  valid: boolean
  errorCount: number
  warningCount: number
  errorCodes: string[]
  warningCodes: string[]
}

export function DevStorageDeliveryProfileDiagnosticsPage() {
  const [data, setData] = useState<StorageDeliveryProfileDiagnosticsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/dev/storage-delivery-profiles')
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then(setData)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <div style={{ padding: '20px', color: '#8b949e' }}>Loading...</div>
  if (error) return <div style={{ padding: '20px', color: '#f85149' }}>Error: {error}</div>
  if (!data) return <div style={{ padding: '20px', color: '#8b949e' }}>No data</div>

  return (
    <div style={{ padding: '20px', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ color: '#58a6ff', marginBottom: '24px' }}>📦 Storage Delivery Profile Diagnostics</h1>
      
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Safety Summary</h2>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>READ_ONLY</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>INTERNAL_ONLY</span>
          <span style={{ background: '#f85149', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NO_RUNTIME_SWITCHING</span>
          <span style={{ background: '#f85149', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NO_PROVIDER_SELECTION</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>NO_REMOTE_CALLS</span>
          <span style={{ background: '#238636', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '12px' }}>R2_ACTIVE_PREVIEW_PATH</span>
        </div>
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Registry Summary</h2>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Default Profile:</strong> <code>{data.defaultProfileId}</code></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Profile Count:</strong> {data.profileCount}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Enabled:</strong> {data.enabledProfileIds.length}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Runtime Selectable:</strong> {data.runtimeSelectableProfileIds.length}</p>
          </div>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Runtime Switching:</strong> <span style={{ color: '#f85149' }}>❌ NOT_IMPLEMENTED</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Provider Selection:</strong> <span style={{ color: '#f85149' }}>❌ NO</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Remote Calls:</strong> <span style={{ color: '#3fb950' }}>✅ NO</span></p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Artifact Access:</strong> <span style={{ color: '#f85149' }}>❌ NOT_USED</span></p>
          </div>
        </div>
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Profiles</h2>
        <table style={{ width: '100%', borderCollapse: 'collapse', color: '#e6edf3' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #30363d' }}>
              <th style={{ textAlign: 'left', padding: '8px', color: '#bc8cff' }}>Profile ID</th>
              <th style={{ textAlign: 'left', padding: '8px', color: '#bc8cff' }}>Status</th>
              <th style={{ textAlign: 'left', padding: '8px', color: '#bc8cff' }}>Access Mode</th>
              <th style={{ textAlign: 'center', padding: '8px', color: '#bc8cff' }}>Enabled</th>
              <th style={{ textAlign: 'center', padding: '8px', color: '#bc8cff' }}>Runtime Selectable</th>
            </tr>
          </thead>
          <tbody>
            {data.profiles.map(profile => (
              <tr key={profile.profileId} style={{ borderBottom: '1px solid #21262d' }}>
                <td style={{ padding: '8px' }}><code>{profile.profileId}</code></td>
                <td style={{ padding: '8px' }}>{profile.status}</td>
                <td style={{ padding: '8px' }}>{profile.accessMode}</td>
                <td style={{ padding: '8px', textAlign: 'center' }}>{profile.enabled ? '✅' : '❌'}</td>
                <td style={{ padding: '8px', textAlign: 'center' }}>{profile.runtimeSelectable ? '✅' : '❌'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>Validation</h2>
        <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Status:</strong> {data.validation.valid ? '✅ VALID' : '❌ INVALID'}</p>
        <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Errors:</strong> {data.validation.errorCount}</p>
        <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Warnings:</strong> {data.validation.warningCount}</p>
        {data.validation.errorCodes.length > 0 && (
          <div style={{ marginTop: '8px' }}>
            <strong style={{ color: '#f85149' }}>Error Codes:</strong>
            <ul style={{ color: '#8b949e', margin: '4px 0', paddingLeft: '20px' }}>
              {data.validation.errorCodes.map(code => <li key={code}>{code}</li>)}
            </ul>
          </div>
        )}
      </div>

      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
        <h2 style={{ color: '#bc8cff', marginTop: 0 }}>References</h2>
        <ul style={{ color: '#8b949e', margin: 0, paddingLeft: '20px' }}>
          <li><a href="/dev/diagnostics" style={{ color: '#58a6ff' }}>← Back to Diagnostics Hub</a></li>
          <li><a href="https://github.com/shangxq007/media-platform/blob/main/docs/storage/storage-delivery-profile-readonly-diagnostics.md" style={{ color: '#58a6ff' }} target="_blank" rel="noopener">Storage Diagnostics Docs</a></li>
          <li><a href="https://github.com/shangxq007/media-platform/blob/main/docs/architecture/current/architecture-assertions.md" style={{ color: '#58a6ff' }} target="_blank" rel="noopener">Architecture Assertions</a></li>
        </ul>
      </div>
    </div>
  )
}
