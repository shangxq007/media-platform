import React, { useState } from 'react'

type AccessActionKind = 'preview' | 'open' | 'download'
type AccessActionState = 'idle' | 'requesting' | 'available' | 'expired' | 'unavailable' | 'error'

interface ArtifactAccessActionProps {
  artifactId: string
  contentType?: string
  onAccessRequest: (artifactId: string) => Promise<{ accessUrl: string; expiresAt?: string } | null>
}

function getActionKind(contentType?: string): AccessActionKind {
  if (!contentType) return 'download'
  if (contentType.startsWith('video/') || contentType.startsWith('image/') || contentType.startsWith('audio/')) {
    return 'preview'
  }
  return 'download'
}

function getActionLabel(kind: AccessActionKind): string {
  switch (kind) {
    case 'preview': return 'Preview'
    case 'open': return 'Open'
    case 'download': return 'Download'
  }
}

export function ArtifactAccessAction({ artifactId, contentType, onAccessRequest }: ArtifactAccessActionProps) {
  const [state, setState] = useState<AccessActionState>('idle')
  const kind = getActionKind(contentType)

  const handleAction = async () => {
    setState('requesting')
    
    try {
      const access = await onAccessRequest(artifactId)
      
      if (!access) {
        setState('unavailable')
        return
      }

      setState('available')

      // Execute action immediately
      switch (kind) {
        case 'preview':
        case 'open':
          window.open(access.accessUrl, '_blank')
          break
        case 'download': {
          const a = document.createElement('a')
          a.href = access.accessUrl
          a.download = ''
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
          break
        }
      }

      // Reset after action
      setTimeout(() => setState('idle'), 1000)
    } catch (error) {
      setState('error')
      setTimeout(() => setState('idle'), 3000)
    }
  }

  if (state === 'requesting') {
    return (
      <button disabled style={{ 
        background: '#21262d', 
        color: '#8b949e', 
        border: '1px solid #30363d', 
        padding: '6px 12px', 
        borderRadius: '6px',
        cursor: 'not-allowed'
      }}>
        Loading...
      </button>
    )
  }

  if (state === 'error') {
    return (
      <span style={{ color: '#f85149', fontSize: '14px' }}>Access failed</span>
    )
  }

  if (state === 'unavailable') {
    return (
      <span style={{ color: '#8b949e', fontSize: '14px' }}>Unavailable</span>
    )
  }

  return (
    <button 
      onClick={handleAction}
      style={{ 
        background: '#238636', 
        color: '#fff', 
        border: '1px solid #30363d', 
        padding: '6px 12px', 
        borderRadius: '6px',
        cursor: 'pointer',
        fontSize: '14px'
      }}
    >
      {getActionLabel(kind)}
    </button>
  )
}
