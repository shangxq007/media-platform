import { useState, type KeyboardEvent } from 'react'
import { Badge, Button } from '../../components/design-system'
import { useProjectContext } from '../../foundation/projectContext'
import { PageHeading, ProjectFrame } from '../../surfaces/FoundationPages'
import { changeZoom, moveViewport, selectCanvasNode, type WorkspaceCanvasState } from './model'

function CanvasContent() {
  const project = useProjectContext()
  const [canvas, setCanvas] = useState<WorkspaceCanvasState>({
    references: [{ referenceId: 'project-ref', kind: 'PROJECT', entityId: project.projectId, label: project.projectName ?? project.projectId }],
    nodes: [
      { presentationId: 'project-node', semanticReferenceId: 'project-ref', title: 'Project reference', x: 120, y: 90 },
      { presentationId: 'note-node', semanticReferenceId: null, title: 'Local composition note', x: 430, y: 230 },
    ],
    edges: [{ edgeId: 'visual-guide', fromPresentationId: 'project-node', toPresentationId: 'note-node', meaning: 'VISUAL_ONLY' }],
    zoom: 1,
    viewportX: 0,
    viewportY: 0,
    selectedPresentationId: null,
  })

  const pan = (x: number, y: number) => setCanvas(current => moveViewport(current, x, y))
  const zoom = (delta: number) => setCanvas(current => changeZoom(current, delta))
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowLeft') { event.preventDefault(); pan(24, 0) }
    if (event.key === 'ArrowRight') { event.preventDefault(); pan(-24, 0) }
    if (event.key === 'ArrowUp') { event.preventDefault(); pan(0, 24) }
    if (event.key === 'ArrowDown') { event.preventDefault(); pan(0, -24) }
    if (event.key === '+' || event.key === '=') { event.preventDefault(); zoom(0.1) }
    if (event.key === '-') { event.preventDefault(); zoom(-0.1) }
    if (event.key === 'Escape') setCanvas(current => selectCanvasNode(current, null))
  }

  return (
    <>
      <PageHeading eyebrow="Creative · Canvas" title="Infinite canvas" description="Semantic references are separate from local node placement, viewport, zoom, selection, and visual guides." actions={<Button disabled title="No canonical relationship command is integrated.">Create semantic relationship</Button>} />
      <div className="ff-canvas-toolbar" aria-label="Canvas viewport controls">
        <Button onClick={() => pan(24, 0)}>Pan left</Button><Button onClick={() => pan(-24, 0)}>Pan right</Button>
        <Button onClick={() => pan(0, 24)}>Pan up</Button><Button onClick={() => pan(0, -24)}>Pan down</Button>
        <Button onClick={() => zoom(-0.1)}>Zoom out</Button><output aria-live="polite">{Math.round(canvas.zoom * 100)}%</output><Button onClick={() => zoom(0.1)}>Zoom in</Button>
      </div>
      <p className="ff-canvas-guidance" id="canvas-keyboard-help"><Badge>Presentation only</Badge> Arrow keys pan, plus/minus zoom, Tab reaches nodes, and Escape clears selection. A visual edge never creates a semantic relationship.</p>
      <div className="ff-workspace-canvas" role="application" aria-label="Infinite canvas workspace" aria-describedby="canvas-keyboard-help" tabIndex={0} onKeyDown={onKeyDown} style={{ backgroundSize: `${24 * canvas.zoom}px ${24 * canvas.zoom}px` }}>
        <div className="ff-canvas-viewport" style={{ transform: `translate(${canvas.viewportX}px, ${canvas.viewportY}px) scale(${canvas.zoom})` }}>
          <div className="ff-visual-edge" aria-label="Visual guide only" style={{ left: 250, top: 190, width: 250 }}><span>visual only</span></div>
          {canvas.nodes.map(node => {
            const reference = canvas.references.find(item => item.referenceId === node.semanticReferenceId)
            return <button key={node.presentationId} type="button" className="ff-canvas-node" style={{ left: node.x, top: node.y }} aria-pressed={canvas.selectedPresentationId === node.presentationId} onClick={() => setCanvas(current => selectCanvasNode(current, node.presentationId))}><Badge tone={reference ? 'info' : 'neutral'}>{reference?.kind ?? 'LOCAL'}</Badge><strong>{node.title}</strong><span>{reference?.label ?? 'No semantic reference'}</span>{reference ? <code>{reference.entityId}</code> : null}</button>
          })}
        </div>
      </div>
      <div className="ff-canvas-status" role="status"><strong>Selected presentation node:</strong> {canvas.selectedPresentationId ?? 'none'} · <strong>Viewport:</strong> {canvas.viewportX}, {canvas.viewportY}</div>
    </>
  )
}

export function CanvasPage() {
  return <ProjectFrame surfaceId="canvas"><CanvasContent /></ProjectFrame>
}
