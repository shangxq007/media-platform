import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/index'

// Health check
function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => api.get('/actuator/health').then(r => r.data),
    refetchInterval: 30000,
  })
}

// Workspaces
function useWorkspaces() {
  return useQuery({
    queryKey: ['workspaces'],
    queryFn: () => api.get('/workspaces').then(r => r.data),
  })
}

// Products
function useProducts() {
  return useQuery({
    queryKey: ['products'],
    queryFn: () => api.get('/product/products').then(r => r.data),
  })
}

// Render jobs
function useRenderJobs() {
  return useQuery({
    queryKey: ['renderJobs'],
    queryFn: () => api.get('/render/jobs').then(r => r.data),
  })
}

// Artifact lookup
function useArtifact(artifactId: string) {
  return useQuery({
    queryKey: ['artifact', artifactId],
    queryFn: () => api.get(`/artifacts/${artifactId}`).then(r => r.data),
    enabled: !!artifactId,
  })
}

export function DevConsolePage() {
  const [artifactId, setArtifactId] = useState('')
  const [workflowJson, setWorkflowJson] = useState('')

  const health = useHealth()
  const workspaces = useWorkspaces()
  const products = useProducts()
  const renderJobs = useRenderJobs()
  const artifact = useArtifact(artifactId)

  const parseWorkflow = () => {
    try {
      return JSON.parse(workflowJson)
    } catch {
      return null
    }
  }

  const workflow = parseWorkflow()

  return (
    <div style={{ padding: '24px', fontFamily: 'monospace', maxWidth: '1200px' }}>
      <h1>🔧 Developer Preview Console</h1>
      <p style={{ color: '#888' }}>Diagnostic view for API workflows. Not a product UI.</p>

      {/* API Health */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px' }}>
        <h2>📡 API Health</h2>
        <div>Base URL: <code>{import.meta.env.VITE_API_BASE_URL || 'https://api.render.cc.cd'}</code></div>
        <div>Status: {health.isLoading ? 'Loading...' : health.data?.status === 'UP' ? '✅ UP' : '❌ DOWN'}</div>
        {health.data && <pre style={{ fontSize: '12px', color: '#888' }}>{JSON.stringify(health.data, null, 2)}</pre>}
        <button onClick={() => health.refetch()} style={{ marginTop: '8px' }}>Refresh</button>
      </section>

      {/* Workspace/Product Summary */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px' }}>
        <h2>📦 Workspace / Product Summary</h2>
        <div>Workspaces: {workspaces.isLoading ? '...' : workspaces.data?.length ?? 0}</div>
        <div>Products: {products.isLoading ? '...' : products.data?.length ?? 0}</div>
        {workspaces.isError && <div style={{ color: 'red' }}>Error loading workspaces</div>}
        {products.isError && <div style={{ color: 'red' }}>Error loading products</div>}
      </section>

      {/* Render Jobs */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px' }}>
        <h2>🎬 Render Jobs</h2>
        <div>Count: {renderJobs.isLoading ? '...' : renderJobs.data?.length ?? 0}</div>
        {renderJobs.data && renderJobs.data.length > 0 && (
          <table style={{ width: '100%', marginTop: '8px', fontSize: '12px' }}>
            <thead>
              <tr style={{ textAlign: 'left' }}>
                <th>ID</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {renderJobs.data.slice(0, 10).map((job: any) => (
                <tr key={job.id}>
                  <td><code>{job.id}</code></td>
                  <td>{job.status}</td>
                  <td>{job.createdAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* Artifact Lookup */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px' }}>
        <h2>📎 Artifact Lookup</h2>
        <input
          type="text"
          placeholder="Artifact ID"
          value={artifactId}
          onChange={e => setArtifactId(e.target.value)}
          style={{ width: '300px', padding: '8px', marginRight: '8px' }}
        />
        <button onClick={() => artifact.refetch()} disabled={!artifactId}>Fetch</button>
        {artifact.data && <pre style={{ fontSize: '12px', color: '#888', marginTop: '8px' }}>{JSON.stringify(artifact.data, null, 2)}</pre>}
        {artifact.isError && <div style={{ color: 'red', marginTop: '8px' }}>Not found or error</div>}
      </section>

      {/* Agent Workflow JSON Viewer */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px' }}>
        <h2>🤖 Agent Workflow Result</h2>
        <textarea
          placeholder="Paste agent workflow JSON output here..."
          value={workflowJson}
          onChange={e => setWorkflowJson(e.target.value)}
          style={{ width: '100%', height: '120px', padding: '8px', fontFamily: 'monospace', fontSize: '12px' }}
        />
        {workflow && (
          <div style={{ marginTop: '8px', padding: '8px', background: '#0a0a0a', borderRadius: '4px' }}>
            <div>OK: {workflow.ok ? '✅' : '❌'}</div>
            <div>Health: {workflow.health?.status}</div>
            <div>Workspaces: {workflow.workspaces?.count}</div>
            <div>Products: {workflow.products?.count}</div>
            <div>Render Jobs: {workflow.renderJobs?.count}</div>
          </div>
        )}
      </section>

      {/* Write Actions */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px', opacity: 0.6 }}>
        <h2>⚠️ Write Actions (Disabled)</h2>
        <p>Write workflows require CLI opt-in:</p>
        <code>AGENT_PRODUCT_WRITE=1 ./scripts/agent/agent-product-workflow.sh</code>
        <div style={{ marginTop: '8px', color: '#888' }}>Create render job, modify products — disabled in UI by default.</div>
      </section>
    </div>
  )
}
