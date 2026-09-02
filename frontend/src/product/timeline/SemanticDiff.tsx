import { useMemo } from 'react'
import { Badge, EmptyState, PropertyRow } from '../../components/design-system'
import type { RevisionComparison } from './gateways'

export function SemanticDiff({ comparison, actionFilter, onActionFilterChange }: {
  comparison: RevisionComparison | null
  actionFilter: string
  onActionFilterChange: (action: string) => void
}) {
  const actions = useMemo(() => comparison
    ? ['ALL', ...Array.from(new Set(comparison.entityChanges.map(change => change.action))).sort()]
    : ['ALL'], [comparison])
  const visible = comparison?.entityChanges.filter(change => actionFilter === 'ALL' || change.action === actionFilter) ?? []

  if (!comparison) {
    return <EmptyState title="No server comparison loaded" description="Choose an explicit revision to compare with canonical HEAD. No client diff is computed." />
  }

  return (
    <div className="ff-semantic-diff">
      <div className="ff-table-toolbar">
        <label>Action <select value={actionFilter} onChange={event => onActionFilterChange(event.target.value)}>{actions.map(action => <option key={action}>{action}</option>)}</select></label>
        <Badge tone={comparison.summary.supported ? 'info' : 'warning'}>{comparison.summary.supported ? 'SERVER SEMANTIC DIFF' : 'SERVER SUMMARY LIMITED'}</Badge>
      </div>
      <div className="ff-diff-summary" aria-label="Server change summary">
        <PropertyRow label="Tracks added / removed / changed"><span>{comparison.summary.tracksAdded} / {comparison.summary.tracksRemoved} / {comparison.summary.tracksModified}</span></PropertyRow>
        <PropertyRow label="Clips added / removed / changed"><span>{comparison.summary.clipsAdded} / {comparison.summary.clipsRemoved} / {comparison.summary.clipsModified}</span></PropertyRow>
      </div>
      {visible.length ? <ul className="ff-entity-change-list">{visible.map(change => <li key={`${change.kind}:${change.entityId}:${change.action}`}><Badge>{change.action}</Badge><strong>{change.kind}</strong><code>{change.entityId}</code></li>)}</ul> : <EmptyState title="No matching entity changes" description="The server comparison returned no entities for this presentation filter." />}
    </div>
  )
}
