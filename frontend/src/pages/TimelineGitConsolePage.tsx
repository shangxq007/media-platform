import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../api/index'

const TENANT_ID = 'ten_307b8956545642a9a45097f2f480a7b4'
const PROJECT_ID = 'prj_6802ca7a12c24aafa31cf77fa63890be'

// Types
interface TimelineRevision {
  id: string
  revisionNumber: number
  parentRevisionId: string | null
  snapshotId: string
  internalRevision: number
  source: string
  message: string | null
  labels: string[]
  authorUserId: string | null
  editSessionId: string | null
  patchOpCount: number
  createdAt: string
  changeSummary: {
    supported: boolean
    tracksAdded: number
    tracksRemoved: number
    tracksModified: number
    clipsAdded: number
    clipsRemoved: number
    clipsModified: number
    assetsAdded: number
    assetsRemoved: number
  }
  isMerge: boolean
}

interface SemanticChange {
  changeType: string
  entityKind: string
  entityId: string
  description: string
  renderAffecting: boolean
}

interface CompareResponse {
  fromRevision: TimelineRevision
  toRevision: TimelineRevision
  summary: {
    supported: boolean
    tracksAdded: number
    clipsAdded: number
    assetsAdded: number
  }
  entityChanges: Array<{ kind: string; entityId: string; action: string }>
  semanticDiff: {
    supported: boolean
    structurallyEqual: boolean
    changeCount: number
    changes: SemanticChange[]
  }
  patchPaths: Array<{ op: string; path: string }>
  patchOpCount: number
}

interface RenderResponse {
  renderJobId: string | null
  timelineRevisionId: string
  snapshotId: string | null
  outputProductId: string | null
  productStatus: string
  storageReferenceId: string | null
  mimeType: string | null
  outputFormat: string | null
  width: number
  height: number
  fps: number
  durationSeconds: number
  hasSubtitles: boolean
  baselineRenderer: string | null
  renderMode: string | null
  inputProductIds: string[]
  inputDependencyCount: number
  message: string
}

// Hooks
function useRevisions() {
  return useQuery({
    queryKey: ['timeline-revisions', PROJECT_ID],
    queryFn: () => api.get(`/render/projects/${PROJECT_ID}/timeline/revisions`).then(r => r.data as TimelineRevision[]),
  })
}

function useRevision(revisionId: string | null) {
  return useQuery({
    queryKey: ['timeline-revision', PROJECT_ID, revisionId],
    queryFn: () => api.get(`/render/projects/${PROJECT_ID}/timeline/revisions/${revisionId}`).then(r => r.data),
    enabled: !!revisionId,
  })
}

function useSnapshot(revisionId: string | null) {
  return useQuery({
    queryKey: ['timeline-snapshot', PROJECT_ID, revisionId],
    queryFn: () => api.get(`/render/projects/${PROJECT_ID}/timeline/revisions/${revisionId}/snapshot`).then(r => r.data),
    enabled: !!revisionId,
  })
}

function useCompare(fromId: string | null, toId: string | null) {
  return useQuery({
    queryKey: ['timeline-compare', PROJECT_ID, fromId, toId],
    queryFn: () => api.get(`/render/projects/${PROJECT_ID}/timeline/revisions/compare?from=${fromId}&to=${toId}`).then(r => r.data as CompareResponse),
    enabled: !!fromId && !!toId,
  })
}

function useRenderRevision() {
  return useMutation({
    mutationFn: (revisionId: string) =>
      api.post(`/render/projects/${PROJECT_ID}/timeline/revisions/${revisionId}/render`, { outputProfile: 'default_1080p' })
        .then(r => r.data as RenderResponse),
  })
}

function useRestoreRevision() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (revisionId: string) =>
      api.post(`/render/projects/${PROJECT_ID}/timeline/revisions/${revisionId}/restore`, { message: 'Restored from console' })
        .then(r => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['timeline-revisions', PROJECT_ID] })
    },
  })
}

// Components
function RevisionList({ revisions, selectedId, onSelect }: { revisions: TimelineRevision[]; selectedId: string | null; onSelect: (id: string) => void }) {
  return (
    <div style={{ borderRight: '1px solid #333', padding: 8, overflowY: 'auto', height: '100%' }}>
      <h3 style={{ margin: '0 0 8px', fontSize: 14 }}>Revisions ({revisions.length})</h3>
      {revisions.map((rev, i) => (
        <div
          key={rev.id}
          onClick={() => onSelect(rev.id)}
          style={{
            padding: 6,
            marginBottom: 4,
            cursor: 'pointer',
            background: rev.id === selectedId ? '#1a3a5c' : i === 0 ? '#1a2a1a' : 'transparent',
            borderRadius: 4,
            borderLeft: i === 0 ? '3px solid #4ade80' : rev.id === selectedId ? '3px solid #60a5fa' : '3px solid transparent',
          }}
        >
          <div style={{ fontSize: 12, fontWeight: 600 }}>
            #{rev.revisionNumber}
            {i === 0 && <span style={{ color: '#4ade80', marginLeft: 4 }}>HEAD</span>}
            {rev.isMerge && <span style={{ color: '#f59e0b', marginLeft: 4 }}>MERGE</span>}
          </div>
          <div style={{ fontSize: 11, color: '#9ca3af', fontFamily: 'monospace' }}>{rev.id.slice(0, 20)}...</div>
          <div style={{ fontSize: 11, color: '#9ca3af' }}>{new Date(rev.createdAt).toLocaleString()}</div>
          {rev.message && <div style={{ fontSize: 11, color: '#d1d5db', marginTop: 2 }}>{rev.message}</div>}
        </div>
      ))}
    </div>
  )
}

function RevisionDetail({ revision }: { revision: any }) {
  if (!revision) return <div style={{ color: '#9ca3af' }}>Select a revision</div>
  const r = revision.revision || revision
  return (
    <div>
      <h4 style={{ margin: '0 0 8px' }}>Revision #{r.revisionNumber}</h4>
      <table style={{ fontSize: 12, width: '100%' }}>
        <tbody>
          {[
            ['ID', r.id],
            ['Parent', r.parentRevisionId || '(root)'],
            ['Snapshot', r.snapshotId],
            ['Source', r.source],
            ['Message', r.message || '-'],
            ['Created', new Date(r.createdAt).toLocaleString()],
            ['Merge', r.isMerge ? 'YES' : 'no'],
          ].map(([k, v]) => (
            <tr key={k}>
              <td style={{ color: '#9ca3af', paddingRight: 8, whiteSpace: 'nowrap' }}>{k}</td>
              <td style={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{String(v)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {r.changeSummary && (
        <div style={{ marginTop: 8, fontSize: 12 }}>
          <strong>Changes:</strong>
          <span style={{ marginLeft: 4 }}>
            {r.changeSummary.tracksAdded > 0 && `+${r.changeSummary.tracksAdded}T `}
            {r.changeSummary.clipsAdded > 0 && `+${r.changeSummary.clipsAdded}C `}
            {r.changeSummary.assetsAdded > 0 && `+${r.changeSummary.assetsAdded}A `}
            {r.changeSummary.tracksAdded === 0 && r.changeSummary.clipsAdded === 0 && r.changeSummary.assetsAdded === 0 && '(none)'}
          </span>
        </div>
      )}
    </div>
  )
}

function SnapshotViewer({ snapshot }: { snapshot: any }) {
  if (!snapshot) return <div style={{ color: '#9ca3af' }}>Loading snapshot...</div>
  const json = typeof snapshot.internalTimelineJson === 'string'
    ? snapshot.internalTimelineJson
    : JSON.stringify(snapshot.internalTimelineJson, null, 2)
  return (
    <div>
      <h4 style={{ margin: '0 0 8px' }}>Snapshot</h4>
      <div style={{ fontSize: 11, color: '#9ca3af', marginBottom: 4 }}>
        Schema: {snapshot.schemaVersion} | Snapshot: {snapshot.snapshotId}
      </div>
      <pre style={{
        background: '#111',
        padding: 8,
        borderRadius: 4,
        fontSize: 11,
        overflow: 'auto',
        maxHeight: 400,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
      }}>
        {json}
      </pre>
    </div>
  )
}

function DiffViewer({ fromId, toId }: { fromId: string | null; toId: string | null }) {
  const { data: compare, isLoading } = useCompare(fromId, toId)
  if (!fromId || !toId) return <div style={{ color: '#9ca3af' }}>Select two revisions to compare</div>
  if (isLoading) return <div style={{ color: '#9ca3af' }}>Loading diff...</div>
  if (!compare) return <div style={{ color: '#ef4444' }}>Failed to load diff</div>
  return (
    <div>
      <h4 style={{ margin: '0 0 8px' }}>Semantic Diff</h4>
      <div style={{ fontSize: 12, marginBottom: 8 }}>
        <strong>From:</strong> #{compare.fromRevision.revisionNumber} ({compare.fromRevision.id.slice(0, 12)}...)
        <strong style={{ marginLeft: 16 }}>To:</strong> #{compare.toRevision.revisionNumber} ({compare.toRevision.id.slice(0, 12)}...)
      </div>
      <div style={{ fontSize: 12, marginBottom: 8 }}>
        Structurally equal: {compare.semanticDiff?.structurallyEqual ? '✅ YES' : '❌ NO'}
        | Changes: {compare.semanticDiff?.changeCount ?? 0}
      </div>
      {compare.semanticDiff?.changes?.length > 0 && (
        <table style={{ fontSize: 12, width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #333' }}>
              <th style={{ textAlign: 'left', padding: 4 }}>Type</th>
              <th style={{ textAlign: 'left', padding: 4 }}>Entity</th>
              <th style={{ textAlign: 'left', padding: 4 }}>ID</th>
              <th style={{ textAlign: 'left', padding: 4 }}>Description</th>
              <th style={{ textAlign: 'left', padding: 4 }}>Render?</th>
            </tr>
          </thead>
          <tbody>
            {compare.semanticDiff.changes.map((c, i) => (
              <tr key={i} style={{ borderBottom: '1px solid #222' }}>
                <td style={{ padding: 4, fontFamily: 'monospace', fontSize: 11 }}>{c.changeType}</td>
                <td style={{ padding: 4 }}>{c.entityKind}</td>
                <td style={{ padding: 4, fontFamily: 'monospace', fontSize: 11 }}>{c.entityId}</td>
                <td style={{ padding: 4 }}>{c.description}</td>
                <td style={{ padding: 4 }}>{c.renderAffecting ? '✅' : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {compare.entityChanges?.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <strong>Entity Changes:</strong>
          {compare.entityChanges.map((c, i) => (
            <div key={i} style={{ fontSize: 11, fontFamily: 'monospace' }}>
              {c.action} {c.kind} {c.entityId}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function RenderPanel({ revisionId, onRender }: { revisionId: string | null; onRender: () => void }) {
  const renderMutation = useRenderRevision()
  const result = renderMutation.data
  return (
    <div>
      <h4 style={{ margin: '0 0 8px' }}>Render</h4>
      <button
        onClick={() => revisionId && renderMutation.mutate(revisionId)}
        disabled={!revisionId || renderMutation.isPending}
        style={{ padding: '6px 16px', background: '#2563eb', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13 }}
      >
        {renderMutation.isPending ? 'Rendering...' : 'Render Selected Revision'}
      </button>
      {renderMutation.isError && (
        <div style={{ color: '#ef4444', marginTop: 8, fontSize: 12 }}>
          Error: {(renderMutation.error as any)?.message || 'Unknown error'}
        </div>
      )}
      {result && (
        <div style={{ marginTop: 12, fontSize: 12 }}>
          <h5 style={{ margin: '0 0 4px' }}>Render Result</h5>
          <table style={{ width: '100%' }}>
            <tbody>
              {[
                ['Job ID', result.renderJobId],
                ['Product ID', result.outputProductId],
                ['Status', result.productStatus],
                ['MIME', result.mimeType],
                ['Resolution', `${result.width}x${result.height}`],
                ['FPS', result.fps],
                ['Duration', `${result.durationSeconds}s`],
                ['Subtitles', result.hasSubtitles ? 'YES' : 'no'],
                ['Renderer', result.baselineRenderer],
                ['Mode', result.renderMode],
                ['Message', result.message],
              ].map(([k, v]) => (
                <tr key={k}>
                  <td style={{ color: '#9ca3af', paddingRight: 8, whiteSpace: 'nowrap' }}>{k}</td>
                  <td style={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{String(v ?? '-')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function RestorePanel({ revisionId }: { revisionId: string | null }) {
  const [confirming, setConfirming] = useState(false)
  const restoreMutation = useRestoreRevision()
  return (
    <div>
      <h4 style={{ margin: '0 0 8px' }}>Restore</h4>
      <p style={{ fontSize: 12, color: '#9ca3af', margin: '0 0 8px' }}>
        Restore creates a new revision from selected content. Does not mutate history.
      </p>
      {!confirming ? (
        <button
          onClick={() => setConfirming(true)}
          disabled={!revisionId}
          style={{ padding: '6px 16px', background: '#d97706', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13 }}
        >
          Restore This Revision
        </button>
      ) : (
        <div>
          <p style={{ color: '#f59e0b', fontSize: 12 }}>
            ⚠️ Confirm: Restore revision {revisionId?.slice(0, 16)}... as new head?
          </p>
          <button
            onClick={() => {
              if (revisionId) restoreMutation.mutate(revisionId)
              setConfirming(false)
            }}
            style={{ padding: '6px 16px', background: '#dc2626', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13, marginRight: 8 }}
          >
            Confirm Restore
          </button>
          <button
            onClick={() => setConfirming(false)}
            style={{ padding: '6px 16px', background: '#4b5563', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 13 }}
          >
            Cancel
          </button>
        </div>
      )}
      {restoreMutation.isSuccess && (
        <div style={{ color: '#4ade80', marginTop: 8, fontSize: 12 }}>
          ✅ Restored! New revision created.
        </div>
      )}
      {restoreMutation.isError && (
        <div style={{ color: '#ef4444', marginTop: 8, fontSize: 12 }}>
          ❌ Error: {(restoreMutation.error as any)?.message}
        </div>
      )}
    </div>
  )
}

// Main Page
export default function TimelineGitConsolePage() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [diffFromId, setDiffFromId] = useState<string | null>(null)
  const [diffToId, setDiffToId] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'details' | 'snapshot' | 'diff' | 'render' | 'restore'>('details')

  const { data: revisions, isLoading, error } = useRevisions()
  const { data: revisionDetail } = useRevision(selectedId)
  const { data: snapshot } = useSnapshot(selectedId)

  const handleSelect = (id: string) => {
    setSelectedId(id)
    setDiffToId(id)
    if (revisions) {
      const idx = revisions.findIndex(r => r.id === id)
      if (idx > 0) setDiffFromId(revisions[idx - 1].id)
    }
  }

  const tabs = ['details', 'snapshot', 'diff', 'render', 'restore'] as const

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: '#0d1117', color: '#e6edf3' }}>
      {/* Header */}
      <div style={{ padding: '8px 16px', borderBottom: '1px solid #30363d', display: 'flex', alignItems: 'center', gap: 16 }}>
        <h2 style={{ margin: 0, fontSize: 16 }}>🕐 Timeline Git Console</h2>
        <span style={{ fontSize: 11, color: '#9ca3af' }}>Project: {PROJECT_ID.slice(0, 16)}...</span>
        <span style={{ fontSize: 11, color: '#f59e0b', marginLeft: 'auto' }}>Merge: EXPERIMENTAL (not MVP)</span>
      </div>

      {/* Content */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* Left: Revision List */}
        <div style={{ width: 280, flexShrink: 0 }}>
          {isLoading ? (
            <div style={{ padding: 16, color: '#9ca3af' }}>Loading revisions...</div>
          ) : error ? (
            <div style={{ padding: 16, color: '#ef4444' }}>Error loading revisions</div>
          ) : revisions ? (
            <RevisionList revisions={revisions} selectedId={selectedId} onSelect={handleSelect} />
          ) : null}
        </div>

        {/* Right: Detail Panel */}
        <div style={{ flex: 1, padding: 16, overflow: 'auto' }}>
          {/* Tabs */}
          <div style={{ display: 'flex', gap: 4, marginBottom: 16 }}>
            {tabs.map(tab => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                style={{
                  padding: '4px 12px',
                  background: activeTab === tab ? '#2563eb' : '#21262d',
                  color: activeTab === tab ? 'white' : '#9ca3af',
                  border: 'none',
                  borderRadius: 4,
                  cursor: 'pointer',
                  fontSize: 12,
                  textTransform: 'capitalize',
                }}
              >
                {tab}
              </button>
            ))}
          </div>

          {/* Tab Content */}
          {!selectedId ? (
            <div style={{ color: '#9ca3af', textAlign: 'center', marginTop: 40 }}>
              Select a revision from the list
            </div>
          ) : (
            <>
              {activeTab === 'details' && <RevisionDetail revision={revisionDetail} />}
              {activeTab === 'snapshot' && <SnapshotViewer snapshot={snapshot} />}
              {activeTab === 'diff' && <DiffViewer fromId={diffFromId} toId={diffToId} />}
              {activeTab === 'render' && <RenderPanel revisionId={selectedId} onRender={() => {}} />}
              {activeTab === 'restore' && <RestorePanel revisionId={selectedId} />}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
