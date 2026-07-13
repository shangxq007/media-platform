import React from 'react'
import { useProductDetail } from '../../../query/app/useProducts'

interface ArtifactMetadata {
  artifactId: string
  label: string
  contentType?: string
  sizeBytes?: number
  createdAt?: string
  availability?: string
}

function RenderStatusBadge({ status }: { status?: string }) {
  if (!status) return <span style={{ color: '#8b949e' }}>Unknown</span>
  
  const colors: Record<string, string> = {
    QUEUED: '#f0883e',
    EXECUTING: '#58a6ff',
    COMPLETED: '#3fb950',
    FAILED: '#f85149',
    CANCELED: '#8b949e',
  }
  
  return (
    <span style={{ 
      background: colors[status] || '#8b949e', 
      color: '#fff', 
      padding: '4px 12px', 
      borderRadius: '4px', 
      fontSize: '14px' 
    }}>
      {status}
    </span>
  )
}

function ArtifactMetadataItem({ artifact }: { artifact: ArtifactMetadata }) {
  return (
    <div style={{ 
      background: '#21262d', 
      border: '1px solid #30363d', 
      borderRadius: '8px', 
      padding: '12px',
      marginBottom: '8px'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ color: '#58a6ff', margin: 0, fontWeight: 'bold' }}>{artifact.label}</p>
          <p style={{ color: '#8b949e', margin: '4px 0 0 0', fontSize: '14px' }}>
            {artifact.contentType || 'Unknown type'}
            {artifact.sizeBytes && ` • ${(artifact.sizeBytes / 1024 / 1024).toFixed(1)} MB`}
          </p>
        </div>
        <span style={{ 
          background: artifact.availability === 'available' ? '#238636' : '#8b949e', 
          color: '#fff', 
          padding: '2px 8px', 
          borderRadius: '4px', 
          fontSize: '12px' 
        }}>
          {artifact.availability || 'unknown'}
        </span>
      </div>
    </div>
  )
}

export function RenderResultDetailPage() {
  const productId = window.location.pathname.split('/').pop() || ''
  const { data, isLoading, error } = useProductDetail(
    { tenantId: 'default', projectId: 'default' },
    productId
  )

  if (isLoading) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Result Detail</h2>
        <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#8b949e', margin: 0 }}>Loading...</p>
        </div>
      </div>
    )
  }

  if (error || !data?.success) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Result Detail</h2>
        <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#f85149', margin: 0 }}>Render result not found</p>
        </div>
      </div>
    )
  }

  const product = data.data

  return (
    <div style={{ padding: '20px' }}>
      <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Result Detail</h2>
      
      {/* Product Summary */}
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h3 style={{ color: '#bc8cff', margin: '0 0 12px 0' }}>Product Summary</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Product ID:</strong> {product.id}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Type:</strong> {product.type}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Status:</strong> {product.status}</p>
          </div>
          <div>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Created:</strong> {product.createdAt ? new Date(product.createdAt).toLocaleString() : 'N/A'}</p>
            <p style={{ color: '#8b949e', margin: '4px 0' }}><strong>Updated:</strong> {product.updatedAt ? new Date(product.updatedAt).toLocaleString() : 'N/A'}</p>
          </div>
        </div>
      </div>

      {/* Render Status */}
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h3 style={{ color: '#bc8cff', margin: '0 0 12px 0' }}>Render Status</h3>
        <p style={{ color: '#8b949e', margin: 0 }}>
          <em>Render status will be available when job linkage is implemented.</em>
        </p>
      </div>

      {/* Artifacts */}
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px', marginBottom: '16px' }}>
        <h3 style={{ color: '#bc8cff', margin: '0 0 12px 0' }}>Artifacts</h3>
        <p style={{ color: '#8b949e', margin: 0 }}>
          <em>Artifact metadata will be available when artifact linkage is implemented.</em>
        </p>
      </div>

      {/* Access Boundary */}
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
        <h3 style={{ color: '#bc8cff', margin: '0 0 12px 0' }}>Artifact Access</h3>
        <p style={{ color: '#8b949e', margin: 0 }}>
          <em>Artifact access will be requested on demand in a later task. Signed access is short-lived and is not canonical metadata.</em>
        </p>
      </div>
    </div>
  )
}
