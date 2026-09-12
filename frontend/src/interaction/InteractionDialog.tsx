import { useEffect, useId, useRef, type ReactNode } from 'react'

export function InteractionDialog({ title, closeLabel, onClose, children, className = '', layout }: { title: string; closeLabel?: string; onClose: () => void; children: ReactNode; className?: string; layout?: 'agent' }) {
  const ref = useRef<HTMLElement>(null)
  const id = useId()
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const previousOverflow = layout === 'agent' ? document.body.style.overflow : null
    const root = ref.current
    const first = root?.querySelector<HTMLElement>('[data-dialog-entry]') ?? root?.querySelector<HTMLElement>('input, textarea, button')
    if (layout === 'agent') document.body.style.overflow = 'hidden'
    ;(first ?? root)?.focus()
    return () => {
      if (previousOverflow !== null) document.body.style.overflow = previousOverflow
      if (previous?.isConnected) previous.focus()
    }
  }, [])
  return <div className="ff-dialog-backdrop" onMouseDown={event => {
    if (event.target === event.currentTarget) {
      // Keep the browser's mousedown default from stealing restored launcher focus.
      event.preventDefault()
      onClose()
    }
  }}>
    <section ref={ref} role="dialog" aria-modal="true" aria-labelledby={id} tabIndex={-1} className={`ff-interaction-dialog${layout ? ` ff-interaction-dialog--${layout}` : ''} ${className}`} onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose(); return }
      if (event.key !== 'Tab') return
      const elements = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href], summary, [tabindex="0"]')).filter(element => !element.closest('[hidden]') && !element.closest('details:not([open]) > :not(summary)'))
      const first = elements[0], last = elements[elements.length - 1]
      if (!first) { event.preventDefault(); event.currentTarget.focus() }
      else if (event.shiftKey && (document.activeElement === first || document.activeElement === event.currentTarget)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }}>
      <header className="ff-dialog-heading"><h2 id={id}>{title}</h2><button type="button" aria-label={closeLabel ?? `Close ${title}`} onClick={onClose}>×</button></header>
      {children}
    </section>
  </div>
}
