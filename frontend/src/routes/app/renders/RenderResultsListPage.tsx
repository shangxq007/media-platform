import React from 'react'
import { useProducts } from '../../../query/app/useProducts'

interface RenderResultItem {
  productId: string
  label: string
  productStatus: string
  renderStatus?: string
  createdAt?: string
  updatedAt?: string
  artifactAvailability?: 'available' | 'pending' | 'unavailable' | 'unknown'
}

function RenderResultStatusBadge({ status }: { status?: string }) {
  if (!status) return null
  
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
      padding: '2px 8px', 
      borderRadius: '4px', 
      fontSize: '12px' 
    }}>
      {status}
    </span>
  )
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
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          {item.renderStatus && <RenderResultStatusBadge status={item.renderStatus} />}
          {item.artifactAvailability === 'available' && (
            <span style={{ background: '#238636', color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '12px' }}>
              Artifacts
            </span>
          )}
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
  const { data, isLoading, error } = useProducts({ tenantId: 'default', projectId: 'default' })

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
