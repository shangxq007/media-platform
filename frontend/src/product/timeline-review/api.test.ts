import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import api from '../../api'
import { timelineReviewSource as source, ReviewDetail, Revision } from './api'
import { detail, project, review, revision } from './testing'
const signal = () => new AbortController().signal
afterEach(() => vi.restoreAllMocks())
describe('strict accepted Timeline read DTOs and shared authenticated transport', () => {
  it('uses only versionless GET paths, explicit limits, encoding, signals and project discovery', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValueOnce({ data: [project] }).mockResolvedValueOnce({ data: project }).mockResolvedValueOnce({ data: [revision] }).mockResolvedValueOnce({ data: revision }).mockResolvedValueOnce({ data: [review] }).mockResolvedValueOnce({ data: detail })
    await source.projects('tenant', signal()); await source.project('tenant', 'p', signal()); await source.revisions('p/one', signal()); await source.head('p', signal()); await source.reviews('p', signal()); await source.detail('p', 'review1', signal())
    expect(get.mock.calls.map(call => call[0])).toEqual(['/api/identity/tenants/tenant/projects', '/api/identity/projects/p', '/api/render/projects/p%2Fone/timeline/revisions?limit=30', '/api/render/projects/p/timeline/revisions/head', '/api/render/projects/p/timeline/reviews?limit=30', '/api/render/projects/p/timeline/reviews/review1'])
    for (const call of get.mock.calls) expect(call[1]).toEqual({ baseURL: '', signal: expect.any(AbortSignal) })
  })
  it('rejects missing, extra, wrong-type, duplicated and foreign-scope fields', async () => {
    expect(Revision.safeParse({ ...revision, isMerge: undefined }).success).toBe(false)
    expect(Revision.safeParse({ ...revision, revisionNumber: '1' }).success).toBe(false)
    expect(ReviewDetail.safeParse({ ...detail, invented: true }).success).toBe(false)
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: [review, review] })
    await expect(source.reviews('p', signal())).rejects.toThrow('Duplicate')
    get.mockResolvedValue({ data: [{ ...review, projectId: 'foreign' }] }); await expect(source.reviews('p', signal())).rejects.toThrow('scope')
    get.mockResolvedValue({ data: { ...detail, comments: [{ ...detail.comments[0], reviewId: 'foreign' }] } }); await expect(source.detail('p', 'review1', signal())).rejects.toThrow('scope')
  })
  it.each([401, 403, 404, 500])('preserves Axios HTTP %s failures through the actual shared interceptors', async status => {
    const original = api.defaults.adapter
    api.defaults.adapter = async config => { throw new AxiosError('Read failed', undefined, config as InternalAxiosRequestConfig, undefined, { status, statusText: 'Failure', headers: {}, config: config as InternalAxiosRequestConfig, data: {} }) }
    vi.spyOn(console, 'error').mockImplementation(() => {})
    try { await expect(source.reviews('p', signal())).rejects.toMatchObject({ response: { status } }) } finally { api.defaults.adapter = original }
  })
  it('accepts missing head only as the head endpoint 404, preserves other failures and cancellation', async () => {
    const get = vi.spyOn(api, 'get').mockRejectedValue({ response: { status: 404 } }); expect(await source.head('p', signal())).toBeNull()
    get.mockRejectedValue({ response: { status: 403 } }); await expect(source.head('p', signal())).rejects.toMatchObject({ response: { status: 403 } })
    get.mockResolvedValue({ data: [revision] }); const controller = new AbortController(); controller.abort(); await expect(source.revisions('p', controller.signal)).rejects.toThrow()
  })
})
