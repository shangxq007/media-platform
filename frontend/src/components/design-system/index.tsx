import {
  useId,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
} from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

export function Button({ variant = 'secondary', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }) {
  return <button className={`ff-button ff-button--${variant} ${className}`} {...props} />
}

export function IconButton({ label, icon, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; icon: ReactNode }) {
  return <button className="ff-icon-button" aria-label={label} title={label} {...props}>{icon}</button>
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`ff-input ${props.className ?? ''}`} {...props} />
}

export function Search({ label = 'Search', ...props }: InputHTMLAttributes<HTMLInputElement> & { label?: string }) {
  return <Input type="search" aria-label={label} placeholder={props.placeholder ?? 'Search'} {...props} />
}

export interface TabDefinition { id: string; label: string; disabled?: boolean }

export function Tabs({ tabs, activeId, onChange, label }: { tabs: readonly TabDefinition[]; activeId: string; onChange: (id: string) => void; label: string }) {
  return (
    <div className="ff-tabs" role="tablist" aria-label={label}>
      {tabs.map(tab => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={activeId === tab.id}
          disabled={tab.disabled}
          tabIndex={activeId === tab.id ? 0 : -1}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger' }) {
  return <span className={`ff-badge ff-badge--${tone}`}>{children}</span>
}

export function Status({ label, tone = 'neutral' }: { label: string; tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger' }) {
  return <span className={`ff-status ff-status--${tone}`}><span aria-hidden="true" />{label}</span>
}

export function Panel({ title, actions, children, className = '' }: { title?: string; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`ff-panel ${className}`}>
      {title ? <header className="ff-panel__header"><h2>{title}</h2>{actions}</header> : null}
      <div className="ff-panel__body">{children}</div>
    </section>
  )
}

export function ResizablePanel({ title, side, initialSize = 260, min = 180, max = 480, children }: {
  title: string
  side: 'left' | 'right' | 'bottom'
  initialSize?: number
  min?: number
  max?: number
  children: ReactNode
}) {
  const [size, setSize] = useState(initialSize)
  const horizontal = side !== 'bottom'
  const change = (delta: number) => setSize(current => Math.min(max, Math.max(min, current + delta)))
  return (
    <section className={`ff-resizable ff-resizable--${side}`} style={horizontal ? { width: size } : { height: size }}>
      <div
        className="ff-resizable__handle"
        role="separator"
        aria-label={`Resize ${title}`}
        aria-orientation={horizontal ? 'vertical' : 'horizontal'}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={size}
        tabIndex={0}
        onKeyDown={event => {
          if (event.key === (horizontal ? 'ArrowLeft' : 'ArrowUp')) { event.preventDefault(); change(-16) }
          if (event.key === (horizontal ? 'ArrowRight' : 'ArrowDown')) { event.preventDefault(); change(16) }
        }}
      />
      <Panel title={title}>{children}</Panel>
    </section>
  )
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="ff-empty"><span aria-hidden="true">◇</span><h2>{title}</h2><p>{description}</p>{action}</div>
}

export function Skeleton({ label = 'Loading' }: { label?: string }) {
  return <div className="ff-skeleton" role="status" aria-label={label}><span className="sr-only">{label}</span></div>
}

export interface BreadcrumbItem { label: string; href?: string }
export function Breadcrumb({ items }: { items: readonly BreadcrumbItem[] }) {
  return (
    <nav aria-label="Breadcrumb"><ol className="ff-breadcrumb">
      {items.map((item, index) => <li key={`${item.label}-${index}`}>{item.href ? <a href={item.href}>{item.label}</a> : <span aria-current="page">{item.label}</span>}</li>)}
    </ol></nav>
  )
}

export function InspectorSection({ title, children }: { title: string; children: ReactNode }) {
  return <section className="ff-inspector-section"><h3>{title}</h3>{children}</section>
}

export function PropertyRow({ label, children }: { label: string; children: ReactNode }) {
  return <div className="ff-property-row"><span>{label}</span><div>{children}</div></div>
}

export function Toast({ title, message, onDismiss }: { title: string; message: string; onDismiss?: () => void }) {
  return <div className="ff-toast" role="status"><div><strong>{title}</strong><p>{message}</p></div>{onDismiss ? <IconButton label="Dismiss notification" icon="×" onClick={onDismiss} /> : null}</div>
}

export interface PaletteAction { id: string; label: string; shortcut?: string; disabledReason?: string; onSelect: () => void }
export function CommandPalette({ open, actions, onClose }: { open: boolean; actions: readonly PaletteAction[]; onClose: () => void }) {
  const [query, setQuery] = useState('')
  const headingId = useId()
  if (!open) return null
  const visible = actions.filter(action => action.label.toLowerCase().includes(query.toLowerCase()))
  return (
    <div className="ff-dialog-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}>
      <section className="ff-command-palette" role="dialog" aria-modal="true" aria-labelledby={headingId} onKeyDown={event => { if (event.key === 'Escape') onClose() }}>
        <h2 id={headingId} className="sr-only">Command palette</h2>
        <Search autoFocus label="Search commands" value={query} onChange={event => setQuery(event.target.value)} placeholder="Type a command…" />
        <ul>
          {visible.map(action => <li key={action.id}><button type="button" disabled={Boolean(action.disabledReason)} title={action.disabledReason} onClick={action.onSelect}><span>{action.label}</span>{action.shortcut ? <kbd>{action.shortcut}</kbd> : null}</button></li>)}
        </ul>
      </section>
    </div>
  )
}
