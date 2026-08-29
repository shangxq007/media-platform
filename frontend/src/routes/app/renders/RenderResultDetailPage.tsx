import React from 'react'
import { useRenderWorkspaceScope } from '../../../api/render-jobs'
import { useProductDetail } from '../../../query/app/useProducts'

export function RenderResultDetailPage() {
  const productId = window.location.pathname.split('/').pop() || ''
  const { data: workspaceScope, isLoading: scopeLoading, error: scopeError } = useRenderWorkspaceScope()
  const tenantId = workspaceScope?.tenantId ?? undefined
  const projectId = workspaceScope?.recentProjects[0]?.id
  const { data, isLoading, error } = useProductDetail(
    { tenantId, projectId },
    productId
  )

  if (scopeLoading) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Result Detail</h2>
        <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#8b949e', margin: 0 }}>Loading authenticated workspace...</p>
        </div>
      </div>
    )
  }

  if (scopeError || !tenantId || !projectId) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Result Detail</h2>
        <div style={{ background: '#161b22', border: '1px solid #8b6914', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#d29922', margin: 0 }}>
            An authenticated workspace with a recent project is required to load this render result.
          </p>
        </div>
      </div>
    )
  }

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
          <em>A tenant/project-scoped redacted artifact summary is required before artifacts can be listed.</em>
        </p>
      </div>

      {/* Access Boundary */}
      <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
        <h3 style={{ color: '#bc8cff', margin: '0 0 12px 0' }}>Artifact Access</h3>
        <p style={{ color: '#8b949e', margin: 0 }}>
          <em>Artifact access is on-demand. Signed access is short-lived and is not canonical metadata.</em>
        </p>
      </div>
    </div>
  )
}
