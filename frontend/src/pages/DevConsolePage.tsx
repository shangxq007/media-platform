import { useState, useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import api from '../api/index'

// Health check
function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => api.get('/actuator/health').then(r => r.data),
    refetchInterval: 30000,
  })
}

// Dev token
function useDevToken() {
  return useQuery({
    queryKey: ['devToken'],
    queryFn: () => api.post('/dev/auth/token', { tenantId: 'smoke-tenant', userId: 'dev-console' })
      .then(r => r.data.accessToken),
    staleTime: 300000,
  })
}

// Submit synthetic render
function useSubmitRender(token: string | undefined) {
  return useMutation({
    mutationFn: () => {
      if (!token) throw new Error('No token')
      return api.post(`/tenants/${'ten_307b8956545642a9a45097f2f480a7b4'}/projects/${'prj_6802ca7a12c24aafa31cf77fa63890be'}/render-jobs/incremental/submit`, {
        tenantId: 'ten_307b8956545642a9a45097f2f480a7b4',
        projectId: 'prj_6802ca7a12c24aafa31cf77fa63890be',
        prompt: JSON.stringify({
          version: '1.0',
          tracks: [{ id: 'v1', type: 'video', clips: [{ id: 'c1', source: 'testsrc', duration: 2 }] }]
        }),
        profile: 'synthetic_testsrc',
      }, { headers: { Authorization: `Bearer ${token}` } }).then(r => r.data)
    },
  })
}

// Poll job status
function useJobStatus(token: string | undefined, jobId: string | null) {
  return useQuery({
    queryKey: ['jobStatus', jobId],
    queryFn: () => {
      if (!token || !jobId) throw new Error('Missing')
      return api.get(`/render/jobs/${jobId}`, { headers: { Authorization: `Bearer ${token}` } }).then(r => r.data)
    },
    enabled: !!token && !!jobId,
    refetchInterval: (query: any) => {
      const status = query.state.data?.status
      return ['COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED'].includes(status) ? false : 1500
    },
  })
}

// Get artifacts
function useArtifacts(token: string | undefined, jobId: string | null, jobStatus: string | undefined) {
  return useQuery({
    queryKey: ['artifacts', jobId],
    queryFn: () => {
      if (!token || !jobId) throw new Error('Missing')
      return api.get(`/render/jobs/${jobId}/artifacts`, { headers: { Authorization: `Bearer ${token}` } }).then(r => r.data)
    },
    enabled: !!token && !!jobId && jobStatus === 'COMPLETED',
  })
}

const TERMINAL_STATES = ['COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED']
const STATUS_ORDER = ['QUEUED', 'SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING', 'COMPLETED']

export function DevConsolePage() {
  const health = useHealth()
  const token = useDevToken().data
  const submitMutation = useSubmitRender(token)
  const [jobId, setJobId] = useState<string | null>(null)
  const jobStatus = useJobStatus(token, jobId)
  const artifacts = useArtifacts(token, jobId, jobStatus.data?.status)

  const handleSubmit = () => {
    submitMutation.mutate(undefined, {
      onSuccess: (data) => setJobId(data.jobId),
    })
  }

  const isRunning = jobId && !TERMINAL_STATES.includes(jobStatus.data?.status)
  const isCompleted = jobStatus.data?.status === 'COMPLETED'
  const isFailed = TERMINAL_STATES.includes(jobStatus.data?.status) && !isCompleted

  return (
    <div style={{ padding: '24px', fontFamily: 'monospace', maxWidth: '1200px', color: '#e0e0e0' }}>
      <h1>🔧 Developer Preview Console</h1>
      <p style={{ color: '#888' }}>Diagnostic view for API workflows. Not a product UI.</p>

      {/* API Health */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px' }}>
        <h2>📡 API Health</h2>
        <div>Base URL: <code>{import.meta.env.VITE_API_BASE_URL || 'https://api.render.cc.cd'}</code></div>
        <div>Status: {health.isLoading ? 'Loading...' : health.data?.status === 'UP' ? '✅ UP' : '❌ DOWN'}</div>
        <button onClick={() => health.refetch()} style={{ marginTop: '8px' }}>Refresh</button>
      </section>

      {/* Synthetic Render Test */}
      <section style={{ marginBottom: '24px', padding: '16px', background: '#1a1a1a', borderRadius: '8px' }}>
        <h2>🎬 Synthetic Render Test</h2>
        <p style={{ color: '#888', fontSize: '14px', marginBottom: '12px' }}>
          Runs a preview-only synthetic FFmpeg test render (testsrc, 2s, 320x180).<br />
          This validates the preview FFmpeg bootstrap path. It does not use OpenCue and is not the final production execution architecture.
        </p>
        <div style={{ marginBottom: '8px', fontSize: '12px', color: '#666' }}>
          Execution Plane: READY for preview FFmpeg bootstrap | OpenCue: NOT STARTED
        </div>

        <button
          onClick={handleSubmit}
          disabled={submitMutation.isPending || !!isRunning}
          style={{
            padding: '10px 20px',
            background: submitMutation.isPending || isRunning ? '#333' : '#2563eb',
            color: 'white',
            border: 'none',
            borderRadius: '6px',
            cursor: submitMutation.isPending || isRunning ? 'not-allowed' : 'pointer',
            fontSize: '14px',
          }}
        >
          {submitMutation.isPending ? 'Submitting...' : isRunning ? 'Running...' : 'Create Synthetic Render'}
        </button>

        {submitMutation.isError && (
          <div style={{ marginTop: '8px', color: '#ef4444' }}>❌ Submit failed: {(submitMutation.error as any)?.message || 'Unknown error'}</div>
        )}

        {/* Job Status */}
        {jobId && (
          <div style={{ marginTop: '16px' }}>
            <div>Job: <code>{jobId}</code></div>

            {/* Status Timeline */}
            <div style={{ marginTop: '12px', display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
              {STATUS_ORDER.map((s) => {
                const current = jobStatus.data?.status
                const idx = STATUS_ORDER.indexOf(current)
                const stepIdx = STATUS_ORDER.indexOf(s)
                const isActive = s === current
                const isPast = stepIdx < idx
                const isTerminal = s === 'COMPLETED' && current === 'COMPLETED'
                return (
                  <div key={s} style={{
                    padding: '4px 10px',
                    borderRadius: '4px',
                    fontSize: '12px',
                    background: isActive ? '#2563eb' : isPast || isTerminal ? '#166534' : '#333',
                    color: isActive || isPast || isTerminal ? 'white' : '#666',
                    border: isActive ? '1px solid #60a5fa' : '1px solid transparent',
                  }}>
                    {s}
                  </div>
                )
              })}
            </div>

            {jobStatus.isLoading && <div style={{ marginTop: '8px', color: '#888' }}>Polling...</div>}

            {/* Failure */}
            {isFailed && (
              <div style={{ marginTop: '12px', padding: '12px', background: '#7f1d1d', borderRadius: '6px' }}>
                ❌ Job failed with status: <strong>{jobStatus.data?.status}</strong>
              </div>
            )}

            {/* Artifacts */}
            {isCompleted && artifacts.data && (
              <div style={{ marginTop: '16px' }}>
                <h3>📦 Artifacts ({artifacts.data.length})</h3>
                {artifacts.data.length === 0 && (
                  <div style={{ color: '#eab308' }}>⚠️ No artifacts found</div>
                )}
                {artifacts.data.map((a: any) => (
                  <ArtifactCard key={a.artifactId} artifact={a} jobId={jobId} token={token} />
                ))}
              </div>
            )}
          </div>
        )}
      </section>
    </div>
  )
}

function ArtifactCard({ artifact, jobId, token }: { artifact: any; jobId: string; token: string }) {
  const contentUrl = `${import.meta.env.VITE_API_BASE_URL || 'https://api.render.cc.cd'}/render/jobs/${jobId}/artifacts/${artifact.artifactId}/content`

  return (
    <div style={{ marginTop: '12px', padding: '12px', background: '#111', borderRadius: '6px', border: '1px solid #333' }}>
      <div style={{ fontSize: '12px', color: '#888' }}>
        <div>ID: <code>{artifact.artifactId}</code></div>
        <div>Format: {artifact.format} | Resolution: {artifact.resolution} | Duration: {artifact.duration}s</div>
        <div>Created: {artifact.createdAt}</div>
      </div>
      <div style={{ marginTop: '12px' }}>
        <video
          controls
          width="320"
          style={{ borderRadius: '4px', background: '#000' }}
          onError={(e) => { (e.target as HTMLVideoElement).style.display = 'none' }}
        >
          <source src={contentUrl} type="video/mp4" />
        </video>
      </div>
      <a href={contentUrl} download="output.mp4" style={{ color: '#60a5fa', fontSize: '12px', marginTop: '8px', display: 'inline-block' }}>
        ⬇️ Download output.mp4
      </a>
    </div>
  )
}
