import React from 'react'
import { devDiagnosticsRoutes } from '../diagnostics.route-map'

export function DevDiagnosticsShell() {
  return (
    <div style={{ padding: '20px', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ color: '#58a6ff', marginBottom: '16px' }}>🔧 DEV Diagnostics</h1>
      
      <div style={{ 
        background: '#161b22', 
        border: '1px solid #30363d', 
        borderRadius: '8px', 
        padding: '16px', 
        marginBottom: '24px' 
      }}>
        <p style={{ color: '#8b949e', margin: 0 }}>
          <strong>DEV diagnostics are internal-only surfaces.</strong> They are not part of normal /app product workflows. 
          Safe preflight persistence remains DEV_ONLY and paused.
        </p>
      </div>

      <h2 style={{ color: '#bc8cff', marginBottom: '16px' }}>Routes</h2>
      
      <div style={{ display: 'grid', gap: '12px' }}>
        {devDiagnosticsRoutes.map(route => (
          <div key={route.id} style={{ 
            background: '#161b22', 
            border: '1px solid #30363d', 
            borderRadius: '8px', 
            padding: '16px' 
          }}>
            <h3 style={{ color: '#58a6ff', margin: '0 0 8px 0' }}>{route.title}</h3>
            <p style={{ color: '#8b949e', margin: '0 0 8px 0', fontSize: '14px' }}>{route.description}</p>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <span style={{ background: '#238636', color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' }}>DEV_ONLY</span>
              <span style={{ background: '#1f6feb', color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' }}>{route.status}</span>
              <span style={{ background: '#6e7681', color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' }}>No Mutation</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
