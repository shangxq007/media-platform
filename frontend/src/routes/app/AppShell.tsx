import React from 'react'

export function AppShell() {
  return (
    <div style={{ padding: '20px', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ color: '#58a6ff', marginBottom: '24px' }}>Media Platform</h1>
      
      <div style={{ 
        background: '#161b22', 
        border: '1px solid #30363d', 
        borderRadius: '8px', 
        padding: '16px', 
        marginBottom: '24px' 
      }}>
        <p style={{ color: '#8b949e', margin: 0 }}>
          <strong>App workspace.</strong> This is the normal user product surface.
        </p>
      </div>
    </div>
  )
}
