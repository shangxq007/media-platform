import React from 'react'
import { useRenderWorkspaceScope } from '../../../api/render-jobs'
import { useProducts } from '../../../query/app/useProducts'

interface RenderResultItem {
  productId: string
  label: string
  productStatus: string
  createdAt?: string
  updatedAt?: string
}

function RenderResultListItem({ item }: { item: RenderResultItem }) {
  return (
    <div style={{ 
      background: '#161b22', 
      border: '1px solid #30363d', 
      borderRadius: '8px', 
      padding: '16px',
      marginBottom: '8px'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h3 style={{ color: '#58a6ff', margin: '0 0 4px 0', fontSize: '16px' }}>{item.label}</h3>
          <p style={{ color: '#8b949e', margin: 0, fontSize: '14px' }}>
            {item.productId} • {item.productStatus}
          </p>
        </div>
      </div>
      {item.createdAt && (
        <p style={{ color: '#8b949e', margin: '8px 0 0 0', fontSize: '12px' }}>
          Created: {new Date(item.createdAt).toLocaleString()}
        </p>
      )}
    </div>
  )
}

export function RenderResultsListPage() {
  const { data: workspaceScope, isLoading: scopeLoading, error: scopeError } = useRenderWorkspaceScope()
  const tenantId = workspaceScope?.tenantId ?? undefined
  const projectId = workspaceScope?.recentProjects[0]?.id
  const { data, isLoading, error } = useProducts({ tenantId, projectId })

  if (scopeLoading) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Results</h2>
        <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#8b949e', margin: 0 }}>Loading authenticated workspace...</p>
        </div>
      </div>
    )
  }

  if (scopeError || !tenantId || !projectId) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Results</h2>
        <div style={{ background: '#161b22', border: '1px solid #8b6914', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#d29922', margin: 0 }}>
            An authenticated workspace with a recent project is required to load render results.
          </p>
        </div>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Results</h2>
        <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#8b949e', margin: 0 }}>Loading...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ padding: '20px' }}>
        <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Results</h2>
        <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#f85149', margin: 0 }}>Error loading render results</p>
        </div>
      </div>
    )
  }

  const items: RenderResultItem[] = data?.success ? data.data.items.map(item => ({
    productId: item.id,
    label: `Product ${item.id}`,
    productStatus: item.status,
    createdAt: item.createdAt,
  })) : []

  return (
    <div style={{ padding: '20px' }}>
      <h2 style={{ color: '#58a6ff', marginBottom: '16px' }}>Render Results</h2>
      
      {items.length === 0 ? (
        <div style={{ background: '#161b22', border: '1px solid #30363d', borderRadius: '8px', padding: '16px' }}>
          <p style={{ color: '#8b949e', margin: 0 }}>No render results yet.</p>
        </div>
      ) : (
        <div>
          {items.map(item => (
            <RenderResultListItem key={item.productId} item={item} />
          ))}
        </div>
      )}
    </div>
  )
}
