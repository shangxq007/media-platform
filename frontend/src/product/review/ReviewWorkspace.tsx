import { useEffect, useRef, useState } from 'react'
import { timelineQueryGateway as defaultTimelineQueryGateway } from '../../api/app/timeline-query.gateway'
import { Badge, Button, EmptyState, Panel, Tabs } from '../../components/design-system'
import { useProjectContext } from '../../foundation/projectContext'
import { PageHeading, ProjectFrame } from '../../surfaces/FoundationPages'
import { SemanticDiff } from '../timeline/SemanticDiff'
import type { RevisionComparison, RevisionListEntry, TimelineQueryGateway } from '../timeline/gateways'
import { projectId } from '../timeline/types'

const sections = ['Overview', 'Visual Changes', 'Semantic Changes', 'Conversation', 'Checks'] as const

export function ReviewWorkspace({ queryGateway }: { queryGateway: TimelineQueryGateway }) {
  const project = useProjectContext()
  const [active, setActive] = useState<string>('Semantic Changes')
  const [history, setHistory] = useState<readonly RevisionListEntry[]>([])
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [comparison, setComparison] = useState<RevisionComparison | null>(null)
  const [filter, setFilter] = useState('ALL')
  const [message, setMessage] = useState('Choose two explicit revisions. No client comparison is computed.')
  const historyGeneration = useRef(0)
  const comparisonGeneration = useRef(0)

  useEffect(() => {
    const generation = ++historyGeneration.current
    comparisonGeneration.current += 1
    setHistory([])
    setFrom('')
    setTo('')
    setComparison(null)
    const load = async () => {
      try {
        const result = await queryGateway.listRevisions(projectId(project.projectId))
        if (historyGeneration.current !== generation) return
        if (result.ok) setHistory(result.value)
        else setMessage(`${result.code}: ${result.message}`)
      } catch (error) {
        if (historyGeneration.current === generation) setMessage(error instanceof Error ? error.message : 'Revision history unavailable.')
      }
    }
    void load()
    return () => {
      historyGeneration.current += 1
      comparisonGeneration.current += 1
    }
  }, [project.projectId, queryGateway])

  const compare = async () => {
    const fromRevision = history.find(item => item.id === from)
    const toRevision = history.find(item => item.id === to)
    if (!fromRevision || !toRevision || fromRevision.id === toRevision.id) {
      setMessage('Select two distinct server-projected revisions.')
      return
    }
    const generation = ++comparisonGeneration.current
    const requestedProject = projectId(project.projectId)
    setMessage('Loading server semantic comparison…')
    try {
      const result = await queryGateway.compare(requestedProject, fromRevision.id, toRevision.id)
      if (comparisonGeneration.current !== generation) return
      if (result.ok) { setComparison(result.value); setMessage('Server comparison loaded.') }
      else setMessage(`${result.code}: ${result.message}`)
    } catch (error) {
      if (comparisonGeneration.current === generation) setMessage(error instanceof Error ? error.message : 'Revision comparison unavailable.')
    }
  }

  const changeRevision = (side: 'from' | 'to', value: string) => {
    comparisonGeneration.current += 1
    setComparison(null)
    setMessage('Choose two explicit revisions. No client comparison is computed.')
    if (side === 'from') setFrom(value)
    else setTo(value)
  }

  return <><PageHeading eyebrow="Review & collaboration" title="Project review" description="Semantic changes are a formatted server comparison. Timeline retains revision and merge authority." actions={<Button disabled title="Canonical merge resolution is not exposed by this bounded surface.">Merge resolution unavailable</Button>} /><Tabs label="Review sections" activeId={active} onChange={setActive} tabs={sections.map(label => ({ id: label, label }))} /><Panel title={active}>{active === 'Semantic Changes' ? <><div className="ff-review-compare-controls"><label>From revision<select aria-label="From revision" value={from} onChange={event => changeRevision('from', event.target.value)}><option value="">Select revision</option>{history.map(item => <option key={item.id} value={item.id}>r{item.revisionNumber} · {item.id}</option>)}</select></label><label>To revision<select aria-label="To revision" value={to} onChange={event => changeRevision('to', event.target.value)}><option value="">Select revision</option>{history.map(item => <option key={item.id} value={item.id}>r{item.revisionNumber} · {item.id}</option>)}</select></label><Button onClick={() => void compare()}>Compare on server</Button><Badge tone="warning">MERGE DISABLED</Badge></div><p role="status">{message}</p><SemanticDiff comparison={comparison} actionFilter={filter} onActionFilterChange={setFilter} /></> : <EmptyState title={`${active} unavailable`} description="This bounded product slice does not fabricate a review projection." />}</Panel></>
}

export function ReviewPage({ queryGateway = defaultTimelineQueryGateway }: { queryGateway?: TimelineQueryGateway }) {
  return <ProjectFrame surfaceId="review"><ReviewWorkspace queryGateway={queryGateway} /></ProjectFrame>
}
