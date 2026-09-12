import { useWorkspaceBinding } from '../../foundation/workspaceSession'
import { createContext, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { flushSync } from 'react-dom'
import { subscribeOidcSessionRetirement } from '../../auth/oidcClient'
import { Badge, Button, PropertyRow, Search } from '../../components/design-system'
import { InteractionDialog } from '../../interaction/InteractionDialog'
import { useInteractionStore, useSelection, useSurfaceAdapter } from '../../interaction/SelectionContext'
import { useTranslation } from '../../localization'
import { absoluteInstant, calendarDay, filterPosts, monthDays, monthWindow, shiftMonth } from './model'
import type { PublicationAccount, PublicationFilters, PublicationPost, PublicationReadSource, PublicationSnapshot } from './types'
import './publication.css'

const SourceContext = createContext<PublicationReadSource | undefined>(undefined)
/** Explicit verification injection only; the ordinary route supplies the authenticated platform source. */
export function PublicationSourceProvider({ source, children }: { source: PublicationReadSource; children: ReactNode }) {
  return <SourceContext.Provider value={source}>{children}</SourceContext.Provider>
}

const identities = new WeakMap<object, number>()
let nextIdentity = 0
function identity(value: object): number {
  if (!identities.has(value)) identities.set(value, ++nextIdentity)
  return identities.get(value)!
}

interface Props {
  workspaceId: string
  projectId: string
  tenantId: string | null
  source?: PublicationReadSource
  now?: () => Date
}

export function PublicationWorkspace(props: Props) {
  const { binding: workspaceBinding, retired } = useWorkspaceBinding()
  const injected = useContext(SourceContext)
  const store = useInteractionStore()
  const source = injected ?? props.source
  const binding = source ? JSON.stringify([source.origin, identity(source.owner), identity(source.getAccounts), identity(source.getPosts), identity(source.getPost)]) : ''
  const [retiredBinding, setRetiredBinding] = useState<string | null>(null)
  useLayoutEffect(() => subscribeOidcSessionRetirement(() => {
    // A retired native session cannot reuse the old source binding, even with the same local IDs.
    flushSync(() => setRetiredBinding(binding))
  }), [binding])
  const activeSource = retired || retiredBinding === binding ? undefined : source
  const presentationKey = JSON.stringify([props.workspaceId, props.projectId, binding])
  const remembered = workspaceBinding.publicationBrowsing
  if (!activeSource || (remembered && (remembered.scopeKey !== presentationKey || (props.tenantId !== null && remembered.tenantId !== null && remembered.tenantId !== props.tenantId)))) workspaceBinding.publicationBrowsing = null
  const key = JSON.stringify([identity(store), props.workspaceId, props.projectId, props.tenantId, activeSource ? binding : null])
  return <PublicationSession key={key} {...props} source={activeSource} presentationKey={presentationKey} />
}

const defaultFilters: PublicationFilters = { query: '', order: 'asc', content: '', artifact: '' }
const displayZones = ['UTC', 'America/Los_Angeles', 'America/New_York', 'Europe/London', 'Europe/Berlin', 'Asia/Shanghai', 'Asia/Kolkata', 'Pacific/Auckland']
type LoadStatus = 'loading' | 'ready' | 'unavailable' | 'restricted' | 'error' | 'invalid' | 'stale-error'

function errorStatus(error: unknown): LoadStatus {
  const candidate = error as { name?: unknown; response?: { status?: unknown } }
  if (candidate?.response?.status === 403) return 'restricted'
  if (candidate?.response?.status === 401 || candidate?.response?.status === 404) return 'unavailable'
  if (candidate?.name === 'ZodError' || candidate?.name === 'PublicationContractError') return 'invalid'
  return 'error'
}

function accountLabel(account: PublicationAccount): string {
  return account.displayNameAvailability === 'AVAILABLE'
    ? `${account.displayName} · ${account.platformType}`
    : `${account.id} · Account name not provided · ${account.platformType}`
}

function PublicationSession({ projectId, tenantId, source, presentationKey, now = () => new Date() }: Props & { presentationKey: string }) {
  const { binding: workspaceBinding } = useWorkspaceBinding()
  const remembered = useRef(workspaceBinding.publicationBrowsing?.scopeKey === presentationKey ? workspaceBinding.publicationBrowsing : null)
  const restoreSelection = useRef(remembered.current?.selectedId ?? null)
  const { locale, t } = useTranslation()
  const store = useInteractionStore()
  const selection = useSelection()
  const validProject = Boolean(projectId.trim())
  const [accounts, setAccounts] = useState<PublicationAccount[]>([])
  const [selectedAccountId, setSelectedAccountId] = useState('')
  const [snapshot, setSnapshot] = useState<PublicationSnapshot | null>(null)
  const [status, setStatus] = useState<LoadStatus>(source && validProject ? 'loading' : 'unavailable')
  const [filters, setFilters] = useState<PublicationFilters>(remembered.current?.filters ?? defaultFilters)
  const [view, setView] = useState<'list' | 'calendar'>(remembered.current?.view ?? 'list')
  const [zone, setZone] = useState(remembered.current?.zone ?? 'UTC')
  const today = calendarDay(now().toISOString(), zone) ?? now().toISOString().slice(0, 10)
  const [month, setMonth] = useState(remembered.current?.month ?? today.slice(0, 7))
  const [day, setDay] = useState(remembered.current?.day ?? today)
  const [detail, setDetail] = useState<{ id: string; lifetime: object } | null>(null)
  const [detailPost, setDetailPost] = useState<PublicationPost | null>(null)
  const [detailStatus, setDetailStatus] = useState<'loading' | 'ready' | 'unavailable' | 'error' | 'invalid'>('unavailable')
  const live = useRef(false)
  const accessLost = useRef(false)
  const lifetime = useRef(selection.lifetime)
  const accountsGeneration = useRef(0)
  const postsGeneration = useRef(0)
  const detailGeneration = useRef(0)
  const accountsController = useRef<AbortController | null>(null)
  const postsController = useRef<AbortController | null>(null)
  const detailController = useRef<AbortController | null>(null)
  const region = useRef<HTMLElement>(null)
  const results = useRef<HTMLDivElement>(null)
  const scroll = useRef({ ...(remembered.current?.scroll ?? { list: 0, calendar: 0 }) })
  const pageScroll = useRef(remembered.current?.pageScroll ?? 0)
  const restoredScroll = useRef(false)
  const launcher = useRef<HTMLElement | null>(null)
  const activeDetail = useRef(detail)
  const retryFocused = useRef(false)
  useEffect(() => {
    if (detailStatus === 'ready' && retryFocused.current) { region.current?.querySelector<HTMLElement>('[role="dialog"]')?.focus(); retryFocused.current = false }
  }, [detailStatus])
  const owns = () => live.current && !workspaceBinding.getSnapshot() && !accessLost.current && lifetime.current === store.getSnapshot().lifetime
  const owned = lifetime.current === selection.lifetime
  const selectedAccount = accounts.find(account => account.id === selectedAccountId)
  const rows = snapshot && owned ? filterPosts(snapshot.posts, filters) : []
  const objects = snapshot && owned ? snapshot.posts.map(post => ({ id: post.id, kind: 'PUBLICATION' as const, title: post.contentAvailability === 'AVAILABLE' && post.contentText?.trim() ? post.contentText : post.id })) : []
  useSurfaceAdapter({ objects: () => objects, supports: [], handle: () => false })

  useLayoutEffect(() => {
    live.current = true
    lifetime.current = store.getSnapshot().lifetime
    return () => {
      live.current = false
      ++accountsGeneration.current; ++postsGeneration.current; ++detailGeneration.current
      accountsController.current?.abort(); postsController.current?.abort(); detailController.current?.abort()
      activeDetail.current = null
    }
  }, [store])
  useLayoutEffect(() => { activeDetail.current = detail; return () => { activeDetail.current = null } }, [detail])
  useLayoutEffect(() => {
    if (!owned) {
      ++accountsGeneration.current; ++postsGeneration.current; ++detailGeneration.current
      accountsController.current?.abort(); postsController.current?.abort(); detailController.current?.abort()
      workspaceBinding.publicationBrowsing = null
      setAccounts([]); setSelectedAccountId(''); setSnapshot(null); setDetail(null); setDetailPost(null); setStatus('unavailable')
    }
  }, [owned])
  useLayoutEffect(() => store.subscribe(() => {
    const current = store.getSnapshot()
    const open = activeDetail.current
    if (open && (open.lifetime !== current.lifetime || open.id !== current.primarySelectedObject?.id || !current.inspectorOpen)) {
      detailController.current?.abort(); activeDetail.current = null; setDetail(null); setDetailPost(null)
    }
  }), [store])
  useLayoutEffect(() => {
    if (!snapshot || !owns()) return
    const id = restoreSelection.current
    restoreSelection.current = null
    if (id && snapshot.posts.some(post => post.id === id)) store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [id] })
    if (results.current) {
      results.current.scrollTop = Math.max(0, Math.min(scroll.current[view], results.current.scrollHeight - results.current.clientHeight))
      scroll.current[view] = results.current.scrollTop
    }
    const scroller = region.current?.closest<HTMLElement>('.ff-center-workspace')
    if (scroller && !restoredScroll.current) {
      scroller.scrollTop = Math.max(0, Math.min(pageScroll.current, scroller.scrollHeight - scroller.clientHeight))
      pageScroll.current = scroller.scrollTop
      restoredScroll.current = true
    }
  }, [snapshot, view])
  useLayoutEffect(() => {
    if (!selectedAccount || !owns()) return
    workspaceBinding.publicationBrowsing = {
      scopeKey: presentationKey, tenantId, accountId: selectedAccount.id, bindingVersion: selectedAccount.bindingVersion,
      filters, view, zone, month, day, selectedId: selection.primarySelectedObject?.id ?? restoreSelection.current,
      scroll: { ...scroll.current }, pageScroll: pageScroll.current,
    }
  }, [workspaceBinding, presentationKey, tenantId, selectedAccount, snapshot, filters, view, zone, month, day, selection.primarySelectedObject?.id])
  useEffect(() => {
    const scroller = region.current?.closest<HTMLElement>('.ff-center-workspace')
    if (!scroller) return
    const save = () => {
      if (!owns() || !restoredScroll.current) return
      pageScroll.current = scroller.scrollTop
      const saved = workspaceBinding.publicationBrowsing
      if (saved?.scopeKey === presentationKey) workspaceBinding.publicationBrowsing = { ...saved, pageScroll: pageScroll.current }
    }
    scroller.addEventListener('scroll', save)
    return () => scroller.removeEventListener('scroll', save)
  }, [workspaceBinding, presentationKey])

  function resetBrowsing() {
    remembered.current = null; restoreSelection.current = null
    workspaceBinding.publicationBrowsing = null
    scroll.current = { list: 0, calendar: 0 }; pageScroll.current = 0
    setFilters(defaultFilters); setView('list'); setZone('UTC')
    store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] })
  }


  function retireAccess(failure: LoadStatus) {
    accessLost.current = true
    workspaceBinding.publicationBrowsing = null
    ++accountsGeneration.current; ++postsGeneration.current; ++detailGeneration.current
    accountsController.current?.abort(); postsController.current?.abort(); detailController.current?.abort()
    activeDetail.current = null
    setAccounts([]); setSelectedAccountId(''); setSnapshot(null); setDetail(null); setDetailPost(null); setStatus(failure)
    store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] })
    region.current?.focus()
  }

  async function loadAccounts() {
    if (!source || !validProject || !owns()) return
    const epoch = ++accountsGeneration.current
    accountsController.current?.abort()
    const abort = new AbortController(); accountsController.current = abort
    if (!accounts.length) setStatus('loading')
    try {
      const response = await source.getAccounts(projectId, abort.signal)
      if (!owns() || abort.signal.aborted || epoch !== accountsGeneration.current) return
      const unique = new Set(response.map(account => account.id)).size === response.length
      const valid = unique && response.every(account => account.projectId === projectId
        && account.endpointAccess === 'AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ'
        && account.globalEffectiveAccess === 'UNKNOWN_FAIL_CLOSED')
      if (!valid) {
        setAccounts([]); setSelectedAccountId(''); setSnapshot(null); setStatus('invalid'); return
      }
      setAccounts(response)
      if (!response.length) {
        resetBrowsing()
        setSelectedAccountId(''); setSnapshot(null); setStatus('unavailable'); return
      }
      const saved = remembered.current
      const restoredAccount = response.find(account => account.id === saved?.accountId && account.bindingVersion === saved.bindingVersion)
      if (saved && !restoredAccount) resetBrowsing()
      setSelectedAccountId(current => response.some(account => account.id === current) ? current : restoredAccount?.id ?? response[0].id)
    } catch (error) {
      if (!owns() || abort.signal.aborted || epoch !== accountsGeneration.current) return
      if (!accounts.length) {
        setAccounts([]); setSelectedAccountId(''); setSnapshot(null)
      }
      const failure = errorStatus(error)
      if (failure === 'restricted' || failure === 'unavailable') retireAccess(failure)
      else setStatus(failure)
    }
  }

  async function loadPosts(refresh: boolean) {
    let account = accounts.find(candidate => candidate.id === selectedAccountId)
    const window = monthWindow(month)
    if (!source || !validProject || !account || !window || !owns()) return
    const epoch = ++postsGeneration.current
    postsController.current?.abort(); detailController.current?.abort()
    const abort = new AbortController(); postsController.current = abort
    activeDetail.current = null; setDetail(null); setDetailPost(null)
    const retainedTuple = snapshot?.account.projectId === projectId
      && snapshot.account.id === account.id
      && snapshot.bindingVersion === account.bindingVersion
      && snapshot.window.start === window.start
      && snapshot.window.end === window.end
    let mayRetain = Boolean(retainedTuple)
    if (!refresh || !retainedTuple) setSnapshot(null)
    setStatus('loading')
    try {
      if (refresh) {
        const accountResponse = await source.getAccounts(projectId, abort.signal)
        if (!owns() || abort.signal.aborted || epoch !== postsGeneration.current) return
        const unique = new Set(accountResponse.map(candidate => candidate.id)).size === accountResponse.length
        const validAccounts = unique && accountResponse.every(candidate => candidate.projectId === projectId
          && candidate.endpointAccess === 'AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ'
          && candidate.globalEffectiveAccess === 'UNKNOWN_FAIL_CLOSED')
        const current = validAccounts ? accountResponse.find(candidate => candidate.id === account!.id) : undefined
        if (!validAccounts || !current) {
          resetBrowsing()
          setAccounts(validAccounts ? accountResponse : []); setSelectedAccountId(''); setSnapshot(null)
          setDetail(null); setDetailPost(null); setStatus(validAccounts ? 'unavailable' : 'invalid')
          store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] })
          region.current?.focus()
          return
        }
        const bindingChanged = current.bindingVersion !== account.bindingVersion
        setAccounts(accountResponse)
        account = current
        if (bindingChanged) {
          resetBrowsing()
          mayRetain = false
          setSnapshot(null); setDetail(null); setDetailPost(null)
          store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] })
          region.current?.focus()
        }
      }
      const requestAccount = account
      if (!requestAccount) return
      const response = await source.getPosts({
        projectId,
        connectedAccountId: requestAccount.id,
        bindingVersion: requestAccount.bindingVersion,
        start: window.start,
        end: window.end,
        limit: 200,
      }, abort.signal)
      if (!owns() || abort.signal.aborted || epoch !== postsGeneration.current) return
      const valid = response.coverage === 'BOUNDED_PARTIAL' && response.items.every(post => post.platformType === requestAccount.platformType
        && post.projectId === projectId && post.connectedAccountId === requestAccount.id && post.bindingVersion === requestAccount.bindingVersion
        && post.timeMeaning === 'PLANNED_PUBLISH_TIME' && post.timePrecision === 'EXACT_INSTANT' && post.scheduledAt
        && post.endpointAccess === 'AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ' && post.globalEffectiveAccess === 'UNKNOWN_FAIL_CLOSED')
      if (!valid || new Set(response.items.map(post => post.id)).size !== response.items.length) {
        setSnapshot(null); setStatus('invalid'); return
      }
      const next: PublicationSnapshot = { account: requestAccount, bindingVersion: requestAccount.bindingVersion, window, coverage: response.coverage, posts: response.items }
      setSnapshot(next); setStatus('ready')
      const current = store.getSnapshot()
      if (current.primarySelectedObject?.kind === 'PUBLICATION' && !response.items.some(post => post.id === current.primarySelectedObject?.id)) {
        store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] }); region.current?.focus()
      }
    } catch (error) {
      if (!owns() || abort.signal.aborted || epoch !== postsGeneration.current) return
      const failure = errorStatus(error)
      if (failure === 'restricted' || failure === 'unavailable') {
        retireAccess(failure)
        return
      }
      if (failure === 'invalid') { setSnapshot(null); setDetailPost(null); setDetail(null) }
      setStatus(failure !== 'invalid' && mayRetain && snapshot ? 'stale-error' : failure)
    }
  }

  useEffect(() => { void loadAccounts() }, [])
  useEffect(() => { if (selectedAccountId) void loadPosts(false) }, [selectedAccountId, month])

  function selectAccount(id: string) {
    if (!owns() || id === selectedAccountId || !accounts.some(account => account.id === id)) return
    resetBrowsing()
    ++postsGeneration.current; ++detailGeneration.current
    postsController.current?.abort(); detailController.current?.abort()
    setSnapshot(null); setDetail(null); setDetailPost(null); setSelectedAccountId(id); setStatus('loading')
  }

  function moveMonth(delta: number) {
    if (!owns()) return
    const next = shiftMonth(month, delta)
    if (next === month || !monthWindow(next)) return
    ++postsGeneration.current; ++detailGeneration.current
    postsController.current?.abort(); detailController.current?.abort()
    setSnapshot(null); setDetail(null); setDetailPost(null); setMonth(next); setDay(`${next}-01`); setStatus('loading')
  }

  function chooseToday() {
    if (!owns()) return
    const nextMonth = today.slice(0, 7)
    if (nextMonth !== month) {
      ++postsGeneration.current; ++detailGeneration.current
      postsController.current?.abort(); detailController.current?.abort()
      setSnapshot(null); setDetail(null); setDetailPost(null); setStatus('loading')
    }
    setMonth(nextMonth); setDay(today)
  }

  async function open(post: PublicationPost, element: HTMLElement) {
    if (!source || !selectedAccount || !snapshot || !owns()) return
    launcher.current = element
    store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [post.id] })
    store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: true })
    const openState = { id: post.id, lifetime: store.getSnapshot().lifetime }
    activeDetail.current = openState; setDetail(openState); setDetailPost(null); setDetailStatus('loading')
    const epoch = ++detailGeneration.current
    detailController.current?.abort()
    const abort = new AbortController(); detailController.current = abort
    try {
      const response = await source.getPost({
        id: post.id,
        projectId,
        connectedAccountId: selectedAccount.id,
        bindingVersion: selectedAccount.bindingVersion,
      }, abort.signal)
      if (!owns() || abort.signal.aborted || epoch !== detailGeneration.current || activeDetail.current !== openState) return
      const valid = response.id === post.id && response.platformType === selectedAccount.platformType
        && response.projectId === projectId && response.connectedAccountId === selectedAccount.id
        && response.bindingVersion === selectedAccount.bindingVersion
        && response.endpointAccess === 'AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ' && response.globalEffectiveAccess === 'UNKNOWN_FAIL_CLOSED'
      if (!valid) { setDetailPost(null); setDetailStatus('unavailable'); return }
      setDetailPost(response); setDetailStatus('ready')
    } catch (error) {
      if (!owns() || abort.signal.aborted || epoch !== detailGeneration.current || activeDetail.current !== openState) return
      const failure = errorStatus(error)
      if (failure === 'restricted' || failure === 'unavailable') retireAccess(failure)
      else setDetailStatus(failure === 'invalid' ? 'invalid' : 'error')
    }
  }

  function close() {
    const open = detail
    if (!owns() || !open || activeDetail.current !== open) return
    ++detailGeneration.current; detailController.current?.abort(); activeDetail.current = null
    setDetail(null); setDetailPost(null)
    if (launcher.current?.isConnected) launcher.current.focus({ preventScroll: true }); else region.current?.focus({ preventScroll: true })
  }

  function timestamp(value: string | undefined) {
    if (!value) return 'Planned time not provided'
    const instant = absoluteInstant(value)
    if (instant === null) return 'Planned time unavailable'
    return `${new Intl.DateTimeFormat(locale, { timeZone: zone, dateStyle: 'medium', timeStyle: 'long' }).format(instant)} · ${value}`
  }

  const contentLabel = (post: PublicationPost) => post.contentAvailability === 'RESTRICTED'
    ? 'Content restricted'
    : post.contentAvailability === 'AVAILABLE' ? post.contentText?.trim() ? post.contentText : `Empty content · ${post.id}` : 'Content not provided'
  const row = (post: PublicationPost) => <li key={post.id} className="ff-publication-row">
    <Button type="button" aria-pressed={selection.primarySelectedObject?.id === post.id} onClick={event => void open(post, event.currentTarget)}>{contentLabel(post)}</Button>
    <span>{selectedAccount ? accountLabel(selectedAccount) : ''}</span><Badge>VERIFIED LOCAL RECORD</Badge>
    <small>Planned publish time · {timestamp(post.scheduledAt)}</small>
  </li>
  const outsideDays = [...new Set(rows.flatMap(post => { const date = calendarDay(post.scheduledAt, zone); return date && !date.startsWith(`${month}-`) ? [date] : [] }))].sort()
  const agenda = rows.filter(post => calendarDay(post.scheduledAt, zone) === day)
  const statusMessage = status === 'unavailable'
    ? source ? 'Publication read unavailable: authentication, Project, source, or bound account is missing.' : 'Publication source not connected'
    : { loading: snapshot ? 'Refreshing publications…' : 'Loading publications…', ready: 'Authorized local publication records', restricted: 'Publication access unavailable', error: 'Could not load publications', invalid: 'Publication response could not be verified', 'stale-error': 'Refresh failed; showing the last verified bounded result.' }[status]

  return <section ref={region} tabIndex={-1} className="ff-publication" aria-label="Publication workspace">
    <header className="ff-page-heading"><div><span>Project · Publication</span><h1>Publication workspace</h1><p>Browse read-only records for the selected account and planned month.</p></div></header>
    <p role="status">{statusMessage}</p>
    {accessLost.current ? <p>Access or the current account binding is unavailable. Reopen the workspace with current access.</p> : null}
    {source && owned && !accounts.length && (status === 'error' || status === 'invalid') ? <Button onClick={() => void loadAccounts()}>Retry account read</Button> : null}
    {source && owned && !accounts.length && status === 'unavailable' && !accessLost.current ? <Button onClick={() => void loadAccounts()}>Refresh accounts</Button> : null}
    {source ? <>
      <details><summary>Data source and access scope</summary><p className="ff-publication-note">{source.origin === 'fixture-verification'
        ? 'Explicit fixture verification data · no runtime fallback or backend authorization claim.'
        : accounts.length ? 'Accepted endpoint-authorized social.read account projection from the authenticated transport.' : 'Authenticated platform source configured; no authorized account receipt has been accepted.'} The separate global EffectiveAccess remains UNKNOWN_FAIL_CLOSED.</p></details>
      {accounts.length ? <div className="ff-publication-toolbar">
        <label>Account<select aria-label="Account" value={selectedAccountId} onChange={event => selectAccount(event.target.value)}>{accounts.map(account => <option key={account.id} value={account.id}>{accountLabel(account)} · binding v{account.bindingVersion}</option>)}</select></label>
        <Search label={t('shell.publication.search')} placeholder={t('shell.publication.search')} value={filters.query} onChange={event => { if (owns()) setFilters(current => ({ ...current, query: event.target.value })) }} />
        <label>Sort by planned publish time<select aria-label="Sort by planned publish time" value={filters.order} onChange={event => { if (owns()) setFilters(current => ({ ...current, order: event.target.value as 'asc' | 'desc' })) }}><option value="asc">Earliest first</option><option value="desc">Latest first</option></select></label>
        <label>Content availability<select aria-label="Content availability" value={filters.content ?? ''} onChange={event => { if (owns()) setFilters(current => ({ ...current, content: event.target.value as PublicationFilters['content'] })) }}><option value="">All content</option><option value="AVAILABLE">Available</option><option value="NOT_PROVIDED">Not provided</option><option value="RESTRICTED">Restricted</option></select></label>
        <label>Artifact availability<select aria-label="Artifact availability" value={filters.artifact ?? ''} onChange={event => { if (owns()) setFilters(current => ({ ...current, artifact: event.target.value as PublicationFilters['artifact'] })) }}><option value="">All artifacts</option><option value="AVAILABLE">Available</option><option value="NOT_PROVIDED">Not provided</option><option value="RESTRICTED">Restricted</option></select></label>
        <Button type="button" onClick={() => { if (owns()) setFilters(defaultFilters) }}>Reset filters</Button>
        <Button type="button" onClick={() => void loadPosts(true)}>{status === 'error' || status === 'invalid' || status === 'restricted' || status === 'stale-error' ? 'Retry publications' : 'Refresh publications'}</Button>
      </div> : null}
      {accounts.length ? <>
        <p>Search and availability filters apply to loaded records only, not all publications.</p>
        <p aria-label="Active publication filters">{filters.query.trim() ? `Search: ${filters.query.trim()} · ` : ''}Content: {filters.content || 'All'} · Artifact: {filters.artifact || 'All'} · {filters.order === 'asc' ? 'Earliest first' : 'Latest first'}</p>
        <div className="ff-publication-toolbar" role="group" aria-label="Source month"><Button type="button" disabled={!monthWindow(shiftMonth(month, -1)) || shiftMonth(month, -1) === month} onClick={() => moveMonth(-1)}>Previous month</Button><h2>{month}</h2><Button type="button" disabled={!monthWindow(shiftMonth(month, 1))} onClick={() => moveMonth(1)}>Next month</Button><Button type="button" onClick={chooseToday}>Today</Button></div>
      </> : null}
      <p className="ff-publication-note">Unscheduled records are outside every ranged list and can be inspected only through an exact authorized detail link. This bounded source cannot claim they are absent.</p>
      {snapshot ? <>
        <p role="status">{rows.length} of {snapshot.posts.length} loaded records · partial result, up to 200</p>
        <details><summary>Loaded UTC interval</summary><p>BOUNDED_PARTIAL · Project: {projectId} · account binding v{snapshot.bindingVersion}</p>
        <p>Source window: [{snapshot.window.start}, {snapshot.window.end}) · scheduledAt means planned publish time only.</p></details>
        <div className="ff-publication-toolbar"><div role="group" aria-label="Publication view"><Button type="button" aria-pressed={view === 'list'} onClick={() => { if (owns()) setView('list') }}>List</Button><Button type="button" aria-pressed={view === 'calendar'} onClick={() => { if (owns()) setView('calendar') }}>Calendar</Button></div>
          <label>Display timezone<select aria-label="Display timezone" value={zone} onChange={event => { if (owns() && displayZones.includes(event.target.value)) setZone(event.target.value) }}>{displayZones.map(candidate => <option key={candidate}>{candidate}</option>)}</select></label><small>Display timezone changes presentation only.</small>
        </div>
        <div ref={results} role="region" aria-label="Publication results" className="ff-publication-results" onScroll={event => { if (owns()) { scroll.current[view] = event.currentTarget.scrollTop; const saved = workspaceBinding.publicationBrowsing; if (saved?.scopeKey === presentationKey) workspaceBinding.publicationBrowsing = { ...saved, scroll: { ...scroll.current } } } }}>
          {!rows.length ? <p>{snapshot.posts.length ? 'No publications match these filters' : 'No planned publications in this bounded window'}</p> : null}
          {view === 'list' ? <ul className="ff-publication-list">{rows.map(row)}</ul> : <>
            <p>UTC source interval: [{snapshot.window.start}, {snapshot.window.end})</p><p>Selected day: {day}</p>
            <div className="ff-publication-calendar" role="group" aria-label="Publication month">
              {Array.from({ length: 7 }, (_, index) => <span key={index} className="ff-publication-weekday">{new Intl.DateTimeFormat(locale, { weekday: 'short', timeZone: 'UTC' }).format(new Date(Date.UTC(2024, 0, 7 + index)))}</span>)}
              {monthDays(month).map(date => {
                const items = rows.filter(post => calendarDay(post.scheduledAt, zone) === date)
                return <div key={date} className="ff-publication-day" style={date.endsWith('-01') ? { gridColumnStart: new Date(`${date}T12:00:00Z`).getUTCDay() + 1 } : undefined}><Button type="button" aria-label={date} aria-pressed={date === day} aria-current={date === today ? 'date' : undefined} onClick={() => { if (owns()) setDay(date) }}>{date.slice(-2)}</Button><ul>{items.slice(0, 2).map(post => <li key={post.id}><span>{contentLabel(post)}</span><small>Planned</small></li>)}</ul>{items.length > 2 ? <Button type="button" onClick={() => { if (owns()) setDay(date) }}>{items.length - 2} more · {date}</Button> : null}</div>
              })}
            </div>
            {outsideDays.length ? <div className="ff-publication-toolbar" aria-label="Other display dates in this source window"><span>Some loaded records fall outside this calendar month in the display timezone:</span>{outsideDays.map(date => <Button key={date} aria-pressed={day === date} onClick={() => { if (owns()) setDay(date) }}>View {date}</Button>)}</div> : null}
            <section role="region" aria-label="Selected day agenda" className="ff-publication-agenda"><h3>Selected day agenda · {day}</h3>{agenda.length ? <ul>{agenda.map(row)}</ul> : <p>No planned publications on this day in the bounded source window</p>}</section>
          </>}
        </div>
      </> : null}
    </> : null}
    {detail && owned && selection.inspectorOpen && detail.lifetime === selection.lifetime && detail.id === selection.primarySelectedObject?.id ? <InteractionDialog title="Publication details" closeLabel="Close publication details" onClose={close} className="ff-publication-details">
      {detailStatus === 'loading' ? <p>Loading publication detail…</p> : null}
      {detailStatus === 'unavailable' || detailStatus === 'invalid' ? <p>Publication detail unavailable</p> : null}
      {detailStatus === 'error' ? <><p>Could not load publication detail. The read can be retried.</p><Button onClick={event => { retryFocused.current = true; const post = snapshot?.posts.find(item => item.id === detail.id); if (post) void open(post, launcher.current?.isConnected ? launcher.current : event.currentTarget) }}>Retry detail read</Button></> : null}
      {detailPost ? <>
        <PropertyRow label="Post ID">{detailPost.id}</PropertyRow>
        <PropertyRow label="Project">{detailPost.projectId}</PropertyRow>
        <PropertyRow label="Binding">{detailPost.connectedAccountId} · v{detailPost.bindingVersion}</PropertyRow>
        <PropertyRow label="Content">{contentLabel(detailPost)}</PropertyRow>
        <PropertyRow label="Content version relation">{detailPost.contentVersionRelationState === 'RESTRICTED' ? 'Restricted' : 'Not provided by the authoritative SocialPost record'}</PropertyRow>
        <PropertyRow label="Account">{selectedAccount ? accountLabel(selectedAccount) : selectedAccountId}</PropertyRow>
        <PropertyRow label="Planned publish time">{detailPost.timeMeaning === 'PLANNED_PUBLISH_TIME' ? timestamp(detailPost.scheduledAt) : 'Planned time not provided'}</PropertyRow>
        <PropertyRow label="Publication status">Not provided by this read contract</PropertyRow>
        <PropertyRow label="Source verification">VERIFIED_LOCAL_RECORD</PropertyRow>
        <PropertyRow label="Artifact">{detailPost.artifactRelationState === 'AVAILABLE'
          ? detailPost.artifactId
          : detailPost.artifactRelationState === 'RESTRICTED' ? 'Restricted' : 'Not provided'}</PropertyRow>
        <p>Attempts and outcomes: NOT_PROVIDED / unknown</p>
        <PropertyRow label="Failure information">Not provided by this read contract</PropertyRow>
        <p>Provider diagnostics, operational status, actual publication timestamps, retries and external URLs are not supplied by this contract.</p>
      </> : null}
    </InteractionDialog> : null}
  </section>
}
