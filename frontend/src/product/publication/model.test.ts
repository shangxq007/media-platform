import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/index', () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }))

import api from '../../api/index'
import { PublicationReadAPI } from '../../api/publish'
import { absoluteInstant, calendarDay, filterPosts, monthDays, monthWindow, shiftMonth } from './model'
import { fixtureAccount, fixturePost } from './testing'

const get = vi.mocked(api.get)
const account = fixtureAccount({ id: 'account-1', displayName: 'Channel name', projectId: 'project-1' })
const post = fixturePost({
  id: 'post-1', projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4,
  contentText: 'Source text', scheduledAt: '2026-09-10T03:00:00Z',
})

beforeEach(() => get.mockReset())

describe('PublicationReadAPI authenticated read contract', () => {
  it('queries Project-scoped authorized accounts on the canonical route', async () => {
    const signal = new AbortController().signal
    get.mockResolvedValue({ data: [account] })
    await expect(PublicationReadAPI.getAccounts('project-1', signal)).resolves.toEqual([account])
    expect(get).toHaveBeenCalledWith('/api/social/platforms', { baseURL: '', params: { projectId: 'project-1' }, signal })
  })

  it('binds list and every receipt to the Project, exact account, binding revision and half-open window', async () => {
    const signal = new AbortController().signal
    get.mockResolvedValue({ data: { items: [post], coverage: 'BOUNDED_PARTIAL' } })
    const request = { projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4, start: '2026-09-01T00:00:00.000Z', end: '2026-10-01T00:00:00.000Z', limit: 200 as const }
    await expect(PublicationReadAPI.getPosts(request, signal)).resolves.toEqual({ items: [post], coverage: 'BOUNDED_PARTIAL' })
    expect(get).toHaveBeenCalledWith('/api/social/posts', { baseURL: '', params: request, signal })
    expect(get.mock.calls[0][1]?.params).toHaveProperty('bindingVersion', 4)
    expect(get.mock.calls[0][1]?.params).not.toHaveProperty('page')
  })

  it('uses corrected exact-account detail and safely encodes the post ID', async () => {
    const signal = new AbortController().signal
    const detail = { ...post, id: 'post:1' }
    get.mockResolvedValue({ data: detail })
    await expect(PublicationReadAPI.getPost({ id: 'post:1', projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4 }, signal)).resolves.toEqual(detail)
    expect(get).toHaveBeenCalledWith('/api/social/posts/post%3A1', { baseURL: '', params: { projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4 }, signal })
  })

  it('rejects incoherent optional fields, diagnostics and legacy envelopes rather than falling back', async () => {
    for (const data of [
      [{ ...account, displayNameAvailability: 'NOT_PROVIDED' }],
      { items: [{ ...post, scheduledAt: undefined }], coverage: 'BOUNDED_PARTIAL' },
      { items: [{ ...post, status: 'PUBLISHED' }], coverage: 'BOUNDED_PARTIAL' },
      { posts: [post], total: 1 },
    ]) get.mockResolvedValueOnce({ data })
    await expect(PublicationReadAPI.getAccounts('project-1', new AbortController().signal)).rejects.toBeTruthy()
    const request = { projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4, start: '2026-09-01T00:00:00.000Z', end: '2026-10-01T00:00:00.000Z', limit: 200 as const }
    await expect(PublicationReadAPI.getPosts(request, new AbortController().signal)).rejects.toBeTruthy()
    await expect(PublicationReadAPI.getPosts(request, new AbortController().signal)).rejects.toBeTruthy()
    await expect(PublicationReadAPI.getPosts(request, new AbortController().signal)).rejects.toBeTruthy()
  })

  it('rejects stale or mismatched list/detail binding tuples and incoherent relation states', async () => {
    const signal = new AbortController().signal
    const request = { projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4, start: '2026-09-01T00:00:00.000Z', end: '2026-10-01T00:00:00.000Z', limit: 200 as const }
    get.mockResolvedValueOnce({ data: { items: [{ ...post, bindingVersion: 5 }], coverage: 'BOUNDED_PARTIAL' } })
      .mockResolvedValueOnce({ data: { ...post, id: 'post-1', connectedAccountId: 'other' } })
      .mockResolvedValueOnce({ data: { items: [{ ...post, artifactId: 'hidden', artifactRelationState: 'RESTRICTED' }], coverage: 'BOUNDED_PARTIAL' } })
    await expect(PublicationReadAPI.getPosts(request, signal)).rejects.toThrow(/tuple mismatch/)
    await expect(PublicationReadAPI.getPost({ id: 'post-1', projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4 }, signal)).rejects.toThrow(/tuple mismatch/)
    await expect(PublicationReadAPI.getPosts(request, signal)).rejects.toBeTruthy()
  })

  it('parses positive, absent and state-only restricted content/Artifact projections', async () => {
    const request = { projectId: 'project-1', connectedAccountId: 'account-1', bindingVersion: 4, start: '2026-09-01T00:00:00.000Z', end: '2026-10-01T00:00:00.000Z', limit: 200 as const }
    const absentFixture = fixturePost({
      ...post,
      id: 'absent',
      contentText: undefined,
      contentAvailability: 'NOT_PROVIDED',
      artifactId: undefined,
      artifactRelationState: 'NOT_PROVIDED',
    })
    const restrictedFixture = fixturePost({
      ...post,
      id: 'restricted',
      contentText: undefined,
      contentAvailability: 'RESTRICTED',
      contentVersionRelationState: 'RESTRICTED',
      artifactId: undefined,
      artifactRelationState: 'RESTRICTED',
    })
    const absent = JSON.parse(JSON.stringify(absentFixture))
    const restricted = JSON.parse(JSON.stringify(restrictedFixture))
    get.mockResolvedValue({ data: { items: [post, absent, restricted], coverage: 'BOUNDED_PARTIAL' } })

    const response = await PublicationReadAPI.getPosts(request, new AbortController().signal)

    expect(response.items.map(item => [item.id, item.contentAvailability, item.artifactRelationState])).toEqual([
      ['post-1', 'AVAILABLE', 'AVAILABLE'],
      ['absent', 'NOT_PROVIDED', 'NOT_PROVIDED'],
      ['restricted', 'RESTRICTED', 'RESTRICTED'],
    ])
    expect(response.items[2]).not.toHaveProperty('contentText')
    expect(response.items[2]).not.toHaveProperty('artifactId')
  })
})

describe('publication planned-time model', () => {
  it('accepts strict offset instants and rejects invalid, missing, date-only and offsetless values', () => {
    expect(absoluteInstant('2024-02-29T23:59:59.125+05:30')).toBe(Date.parse('2024-02-29T18:29:59.125Z'))
    for (const value of [undefined, null, '', '2024-02-30T12:00:00Z', '2024-03-10', '2024-03-10T12:00:00', '2024-03-10T24:00:00Z']) expect(absoluteInstant(value)).toBeNull()
  })

  it('creates the exact UTC half-open source window', () => {
    expect(monthWindow('2026-09')).toEqual({ start: '2026-09-01T00:00:00.000Z', end: '2026-10-01T00:00:00.000Z' })
    expect(monthWindow('bad')).toBeNull()
  })

  it('calculates calendar months and display-zone days without changing source instants', () => {
    expect(monthDays('2024-02')).toHaveLength(29)
    expect(shiftMonth('2024-12', 1)).toBe('2025-01')
    expect(calendarDay('2024-03-01T00:30:00Z', 'America/Los_Angeles')).toBe('2024-02-29')
    expect(calendarDay('2024-03-10T00:00:00Z', 'bad-zone')).toBeNull()
  })

  it('filters only sourced content and stably sorts planned publish time with ID ties', () => {
    const posts = [fixturePost({ id: 'b', contentText: undefined, contentAvailability: 'NOT_PROVIDED' }), fixturePost({ id: 'a', contentText: 'Opening story' })]
    expect(filterPosts(posts, { query: 'opening', order: 'asc' }).map(row => row.id)).toEqual(['a'])
    expect(filterPosts(posts, { query: '', order: 'desc' }).map(row => row.id)).toEqual(['a', 'b'])
    expect(fixtureAccount().globalEffectiveAccess).toBe('UNKNOWN_FAIL_CLOSED')
  })
})


it('places missing/invalid planned times last in either direction with deterministic ID ties', () => {
  const posts = [fixturePost({ id: 'a', scheduledAt: undefined }), fixturePost({ id: 'b', scheduledAt: 'invalid' }), fixturePost({ id: 'c', scheduledAt: '2026-09-11T00:00:00Z' }), fixturePost({ id: 'd', scheduledAt: '2026-09-10T00:00:00Z' })]
  for (const input of [posts, [...posts].reverse()]) {
    expect(filterPosts(input, { query: '', order: 'asc' }).map(post => post.id)).toEqual(['d', 'c', 'a', 'b'])
    expect(filterPosts(input, { query: '', order: 'desc' }).map(post => post.id)).toEqual(['c', 'd', 'a', 'b'])
  }
  expect(monthWindow('9999-12')).toBeNull()
})
