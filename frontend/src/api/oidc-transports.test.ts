import { z } from 'zod'
import { apiRequest } from './core/api-client'
import { AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const sdk = vi.hoisted(() => ({
  user: null as unknown,
  getUser: vi.fn<() => Promise<unknown>>(),
  redirect: vi.fn<() => Promise<void>>(),
  callback: vi.fn<() => Promise<unknown>>(),
  signout: vi.fn<() => Promise<void>>(),
  events: new Map<string, Set<(...args: unknown[]) => unknown>>(),
}))
vi.mock('../auth/oidcConfig', () => ({ isOidcEnabled: () => true, getOidcSettings: () => ({
  issuer: 'https://issuer.test', clientId: 'frontend-test', redirectUri: 'http://localhost/oauth/callback', scope: 'openid profile',
}) }))
vi.mock('oidc-client-ts', () => {
  const events = Object.fromEntries(['UserLoaded', 'UserUnloaded', 'AccessTokenExpired', 'UserSignedIn', 'UserSignedOut', 'UserSessionChanged'].flatMap(name => {
    const listeners = new Set<(...args: unknown[]) => unknown>(); sdk.events.set(name, listeners)
    return [[`add${name}`, (listener: (...args: unknown[]) => unknown) => listeners.add(listener)], [`remove${name}`, (listener: (...args: unknown[]) => unknown) => listeners.delete(listener)]]
  }))
  return { WebStorageStateStore: class {}, UserManager: class {
    events = events
    getUser() { return sdk.getUser() }
    signinRedirect() { return sdk.redirect() }
    signinRedirectCallback() { return sdk.callback() }
    signoutRedirect() { return sdk.signout() }
  } }
})
import api from './index'
import { versionlessApi } from './app/versionless-api'
import { getOidcRequestBinding, getOidcUser, handleOAuthCallback, subscribeOidcSessionRetirement } from '../auth/oidcClient'


const clients = [['shared', api, '/api/v1'], ['versionless', versionlessApi, '/api']] as const
const adapters = clients.map(([, client]) => client.defaults.adapter)
const subscriptions: (() => void)[] = []
let sequence = 0
function user(key: string) {
  return { access_token: `token-${key}`, expired: false, expires_at: 4102444800, scopes: ['openid'],
    profile: { iss: 'https://issuer.test', aud: 'frontend-test', sub: `user-${key}`, sid: `sid-${key}`, tenantId: `tenant-${key}` } }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function login(key: string) {
  sdk.user = user(key)
  await handleOAuthCallback()
  for (const listener of sdk.events.get('UserLoaded') ?? []) await listener(sdk.user)
  return sdk.user
}
async function watch() {
  const state = { privateData: 'private session data' as string | null }
  const retired = vi.fn(() => { state.privateData = null })
  const hydrated = deferred<unknown>()
  sdk.getUser.mockImplementationOnce(() => hydrated.promise)
  subscriptions.push(subscribeOidcSessionRetirement(retired))
  hydrated.resolve(sdk.user)
  await hydrated.promise
  return { state, retired }
}
function request(client: AxiosInstance, signal?: AbortSignal) {
  const sent = deferred<InternalAxiosRequestConfig>()
  const response = deferred<AxiosResponse>()
  client.defaults.adapter = config => { sent.resolve(config); return response.promise }
  const result = client.get('/session-probe', { signal }).catch((error: unknown) => error)
  return { sent: sent.promise, response, result }
}
function failure(config: InternalAxiosRequestConfig, status = 401) {
  return new AxiosError('Read rejected', undefined, config, undefined,
    { status, statusText: 'Rejected', headers: {}, config, data: { reason: 'denied' } })
}
async function succeed(client: AxiosInstance, expectedUser = sdk.user) {
  const pending = request(client)
  const config = await pending.sent
  expect(config.headers.Authorization).toBe(`Bearer ${(expectedUser as ReturnType<typeof user>).access_token}`)
  pending.response.resolve({ status: 200, statusText: 'OK', headers: {}, config, data: { usable: true } })
  expect(await pending.result).toMatchObject({ data: { usable: true }, status: 200 })
  expect(await getOidcUser()).toBe(expectedUser)
  expect((await getOidcRequestBinding()).retired).toBe(false)
}
beforeEach(async () => {
  sdk.getUser.mockReset().mockImplementation(async () => sdk.user)
  sdk.callback.mockReset().mockImplementation(async () => sdk.user)
  sdk.redirect.mockReset().mockResolvedValue(undefined)
  vi.spyOn(console, 'error').mockImplementation(() => {})
  await login(String(++sequence))
})
afterEach(() => {
  subscriptions.splice(0).forEach(stop => stop())
  clients.forEach(([, client], i) => { client.defaults.adapter = adapters[i] })
  vi.restoreAllMocks(); sessionStorage.clear(); localStorage.clear()
})

describe.each(clients)('%s OIDC transport', (_name, client, baseURL) => {
  it('binds A credentials, then ignores A 401 after B logs in', async () => {
    const old = sdk.user
    const pending = request(client), config = await pending.sent
    expect(config.baseURL).toBe(baseURL)
    expect(config.url).toBe('/session-probe')
    expect(config.headers.Authorization).toBe(`Bearer ${(old as ReturnType<typeof user>).access_token}`)
    const next = await login(`replacement-${sequence}`), { retired, state } = await watch()
    const error = failure(config); pending.response.reject(error)
    expect(await pending.result).toBe(error)
    expect(retired).not.toHaveBeenCalled(); expect(state.privateData).not.toBeNull()
    expect(sdk.redirect).not.toHaveBeenCalled(); expect(sessionStorage.getItem('oidc_post_login_redirect')).toBeNull()
    await succeed(client, next)
  })

  it('does not dispatch a credential capture superseded by login', async () => {
    const old = sdk.user, capture = deferred<unknown>(), reading = deferred<void>()
    sdk.getUser.mockImplementationOnce(() => { reading.resolve(); return capture.promise })
    const adapter = vi.fn(); client.defaults.adapter = adapter
    const pending = client.get('/binding-race').catch((error: unknown) => error)
    await reading.promise
    const next = await login(`capture-${sequence}`), { retired } = await watch()
    capture.resolve(old)
    expect(await pending).toMatchObject({ code: 'ERR_CANCELED' })
    expect(adapter).not.toHaveBeenCalled(); expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
    await succeed(client, next)
  })

  it('coalesces simultaneous expiry and clears subscriber private state once', async () => {
    const { retired, state } = await watch()
    const first = request(client), a = await first.sent
    const second = request(client), b = await second.sent
    const errors = [failure(a), failure(b)]
    first.response.reject(errors[0]); second.response.reject(errors[1])
    expect(await Promise.all([first.result, second.result])).toEqual(errors)
    expect(retired).toHaveBeenCalledOnce(); expect(state.privateData).toBeNull()
    expect(sdk.redirect).toHaveBeenCalledOnce(); expect((await getOidcRequestBinding()).retired).toBe(true)
    await expect(client.get('/retired')).rejects.toMatchObject({ code: 'ERR_CANCELED' })
  })

  it('preserves the original 401 when redirect rejects', async () => {
    const { retired } = await watch()
    sdk.redirect.mockRejectedValue(new Error('redirect unavailable'))
    const pending = request(client), config = await pending.sent, error = failure(config)
    pending.response.reject(error)
    expect(await pending.result).toBe(error)
    expect(retired).toHaveBeenCalledOnce(); expect(sdk.redirect).toHaveBeenCalledOnce()
    await expect(client.get('/retired')).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(sdk.redirect).toHaveBeenCalledOnce()
  })

  it('cancels before dispatch and before a late response without auth effects', async () => {
    const { retired, state } = await watch(), controller = new AbortController()
    const adapter = vi.fn(); client.defaults.adapter = adapter
    controller.abort()
    await expect(client.get('/never-sent', { signal: controller.signal })).rejects.toMatchObject({ code: 'ERR_CANCELED' })
    expect(adapter).not.toHaveBeenCalled()
    const active = new AbortController(), pending = request(client, active.signal), config = await pending.sent
    active.abort(); pending.response.reject(failure(config))
    expect(await pending.result).toMatchObject({ code: 'ERR_CANCELED' })
    expect(retired).not.toHaveBeenCalled(); expect(state.privateData).not.toBeNull(); expect(sdk.redirect).not.toHaveBeenCalled()
    await succeed(client)
  })

  it('abort racing with asynchronous expiry validation preserves 401 without retirement', async () => {
    const { retired, state } = await watch(), controller = new AbortController()
    const pending = request(client, controller.signal), config = await pending.sent, error = failure(config)
    const checking = deferred<void>(), current = deferred<unknown>()
    sdk.getUser.mockImplementationOnce(() => { checking.resolve(); return current.promise })
    pending.response.reject(error)
    await checking.promise
    controller.abort(); current.resolve(sdk.user)
    expect(await pending.result).toBe(error)
    expect(retired).not.toHaveBeenCalled(); expect(state.privateData).not.toBeNull(); expect(sdk.redirect).not.toHaveBeenCalled()
    await succeed(client)
  })

  it('fences an old SDK read that completes after B login', async () => {
    const old = sdk.user, pending = request(client), config = await pending.sent, error = failure(config)
    const checking = deferred<void>(), current = deferred<unknown>()
    sdk.getUser.mockImplementationOnce(() => { checking.resolve(); return current.promise })
    pending.response.reject(error); await checking.promise
    const next = await login(`sdk-race-${sequence}`), { retired, state } = await watch()
    current.resolve(old)
    expect(await pending.result).toBe(error)
    expect(retired).not.toHaveBeenCalled(); expect(state.privateData).not.toBeNull(); expect(sdk.redirect).not.toHaveBeenCalled()
    await succeed(client, next)
  })

  it.each(['resolve', 'reject'] as const)('old redirect %s and late 401 cannot invalidate a new login', async completion => {
    const { retired, state } = await watch()
    const redirect = deferred<void>(), started = deferred<void>()
    sdk.redirect.mockImplementation(() => { started.resolve(); return redirect.promise })
    const pending = request(client), config = await pending.sent, error = failure(config)
    const late = request(client), lateConfig = await late.sent, lateError = failure(lateConfig)
    pending.response.reject(error); await started.promise
    expect(retired).toHaveBeenCalledOnce(); expect(state.privateData).toBeNull()
    const next = await login(`new-login-${sequence}`)
    state.privateData = 'new session data'; const retireCount = retired.mock.calls.length
    await succeed(client, next)
    late.response.reject(lateError)
    expect(await late.result).toBe(lateError)
    if (completion === 'resolve') redirect.resolve(); else redirect.reject(new Error('old redirect failed'))
    expect(await pending.result).toBe(error)
    expect(retired).toHaveBeenCalledTimes(retireCount); expect(state.privateData).toBe('new session data')
    expect(sdk.redirect).toHaveBeenCalledOnce()
    await succeed(client, next)
  })

  it.each([403, 500])('preserves ordinary HTTP %s errors and the active session', async status => {
    const { retired, state } = await watch()
    const pending = request(client), config = await pending.sent, error = failure(config, status)
    pending.response.reject(error); expect(await pending.result).toBe(error)
    expect(retired).not.toHaveBeenCalled(); expect(state.privateData).not.toBeNull(); expect(sdk.redirect).not.toHaveBeenCalled()
    await succeed(client)
  })

  it('preserves network failures and valid authenticated requests', async () => {
    const { retired } = await watch(), pending = request(client)
    await pending.sent
    const error = new AxiosError('Network Error')
    pending.response.reject(error); expect(await pending.result).toBe(error)
    await succeed(client)
    expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
  })
})

it.each([false, true])('coalesces expiry across both actual clients (versionless first: %s)', async versionlessFirst => {
  const { retired, state } = await watch()
  const first = request(versionlessFirst ? versionlessApi : api), a = await first.sent
  const second = request(versionlessFirst ? api : versionlessApi), b = await second.sent
  const errors = [failure(a), failure(b)]
  first.response.reject(errors[0]); second.response.reject(errors[1])
  expect(await Promise.all([first.result, second.result])).toEqual(errors)
  expect(retired).toHaveBeenCalledOnce(); expect(state.privateData).toBeNull(); expect(sdk.redirect).toHaveBeenCalledOnce()
  await login(`cross-client-${sequence}`)
  await succeed(api); await succeed(versionlessApi)
  expect(sdk.redirect).toHaveBeenCalledOnce()
})

// The reachable fetch client retains its ApiResult contract while sharing OIDC authority.
function fetchRequest(signal?: AbortSignal) {
  const sent = deferred<RequestInit>(), response = deferred<Response>()
  vi.spyOn(globalThis, 'fetch').mockImplementationOnce(async (_url, options) => {
    sent.resolve(options ?? {}); return response.promise
  })
  const result = apiRequest({ baseUrl: '/api' }, '/probe', z.object({ usable: z.boolean() }), { signal })
  return { sent: sent.promise, response, result }
}
function fetchResponse(status: number) {
  return new Response(JSON.stringify({ usable: true }), { status, headers: { 'Content-Type': 'application/json' } })
}
async function fetchSuccess() {
  const pending = fetchRequest(), config = await pending.sent
  expect(new Headers(config.headers).get('Authorization')).toBe(`Bearer ${(sdk.user as ReturnType<typeof user>).access_token}`)
  pending.response.resolve(fetchResponse(200))
  expect(await pending.result).toEqual({ success: true, data: { usable: true } })
  expect((await getOidcRequestBinding()).retired).toBe(false)
}
const unauthorizedResult = { success: false, error: { message: 'HTTP 401', status: 401 } }

describe('reachable fetch OIDC transport', () => {
  it('binds A credentials and ignores stale 401 after B login', async () => {
    const old = sdk.user, pending = fetchRequest(), config = await pending.sent
    expect(new Headers(config.headers).get('Authorization')).toBe(`Bearer ${(old as ReturnType<typeof user>).access_token}`)
    const next = await login(`fetch-new-${sequence}`), { state, retired } = await watch()
    pending.response.resolve(fetchResponse(401)); expect(await pending.result).toEqual(unauthorizedResult)
    expect(sdk.redirect).not.toHaveBeenCalled(); expect(retired).not.toHaveBeenCalled(); expect(state.privateData).not.toBeNull()
    expect(await getOidcUser()).toBe(next); await fetchSuccess()
  })

  it('replaces supplied credentials with the bound OIDC credential', async () => {
    const network = vi.spyOn(globalThis, 'fetch').mockResolvedValue(fetchResponse(200))
    expect(await apiRequest({ baseUrl: '/api', headers: { authorization: 'Bearer obsolete' } }, '/probe', z.unknown(), {
      headers: { AUTHORIZATION: 'Bearer also-obsolete' },
    })).toMatchObject({ success: true })
    const headers = new Headers(network.mock.calls[0][1]?.headers)
    expect(headers.get('authorization')).toBe(`Bearer ${(sdk.user as ReturnType<typeof user>).access_token}`)
    expect(sdk.redirect).not.toHaveBeenCalled()
  })

  it.each(['fetch', 'shared', 'versionless'] as const)('coalesces expiry with %s and retires private state', async partner => {
    const { state, retired } = await watch(), first = fetchRequest(); await first.sent
    const second = partner === 'fetch' ? fetchRequest() : request(partner === 'shared' ? api : versionlessApi)
    const config = await second.sent
    first.response.resolve(fetchResponse(401))
    if (partner === 'fetch') (second.response as ReturnType<typeof deferred<Response>>).resolve(fetchResponse(401))
    else second.response.reject(failure(config as InternalAxiosRequestConfig))
    expect(await first.result).toEqual(unauthorizedResult)
    expect(await second.result).toMatchObject(partner === 'fetch' ? unauthorizedResult : { response: { status: 401 } })
    expect(sdk.redirect).toHaveBeenCalledOnce(); expect(retired).toHaveBeenCalledOnce(); expect(state.privateData).toBeNull()
    expect((await getOidcRequestBinding()).retired).toBe(true)
    const network = vi.mocked(fetch); network.mockClear()
    expect(await apiRequest({ baseUrl: '' }, '/retired', z.unknown())).toEqual({ success: false, error: { message: 'OIDC request binding retired' } })
    expect(network).not.toHaveBeenCalled()
    await login(`fetch-recovered-${sequence}`); await fetchSuccess(); await succeed(api); await succeed(versionlessApi)
  })

  it.each(['resolve', 'reject'] as const)('preserves 401 and B session after old redirect %s', async completion => {
    const { state, retired } = await watch(), redirect = deferred<void>(), started = deferred<void>()
    sdk.redirect.mockImplementation(() => { started.resolve(); return redirect.promise })
    const first = fetchRequest(); await first.sent
    const late = fetchRequest(); await late.sent
    first.response.resolve(fetchResponse(401)); await started.promise
    expect(retired).toHaveBeenCalledOnce(); expect(state.privateData).toBeNull()
    const next = await login(`fetch-callback-${sequence}`)
    state.privateData = 'new data'; const count = retired.mock.calls.length
    await fetchSuccess()
    late.response.resolve(fetchResponse(401)); expect(await late.result).toEqual(unauthorizedResult)
    if (completion === 'resolve') redirect.resolve(); else redirect.reject(new Error('redirect failed'))
    expect(await first.result).toEqual(unauthorizedResult)
    expect(retired).toHaveBeenCalledTimes(count); expect(state.privateData).toBe('new data'); expect(sdk.redirect).toHaveBeenCalledOnce()
    expect(await getOidcUser()).toBe(next); await fetchSuccess()
  })

  it('cancels before dispatch and before response without expiry effects', async () => {
    const { retired } = await watch(), controller = new AbortController(), network = vi.spyOn(globalThis, 'fetch')
    controller.abort()
    expect(await apiRequest({ baseUrl: '' }, '/aborted', z.unknown(), { signal: controller.signal })).toMatchObject({ success: false })
    expect(network).not.toHaveBeenCalled()
    const active = new AbortController(), pending = fetchRequest(active.signal); await pending.sent
    active.abort(); pending.response.reject(new DOMException('The operation was aborted.', 'AbortError'))
    expect(await pending.result).toEqual({ success: false, error: { message: 'The operation was aborted.' } })
    expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled(); await fetchSuccess()
  })

  it.each(['abort', 'login'] as const)('fences %s during asynchronous expiry validation', async action => {
    const old = sdk.user, controller = new AbortController(), pending = fetchRequest(controller.signal); await pending.sent
    const checking = deferred<void>(), current = deferred<unknown>()
    sdk.getUser.mockImplementationOnce(() => { checking.resolve(); return current.promise })
    pending.response.resolve(fetchResponse(401)); await checking.promise
    if (action === 'abort') controller.abort(); else await login(`fetch-check-${sequence}`)
    const { state, retired } = await watch()
    current.resolve(old)
    expect(await pending.result).toEqual(unauthorizedResult)
    expect(retired).not.toHaveBeenCalled(); expect(state.privateData).not.toBeNull(); expect(sdk.redirect).not.toHaveBeenCalled()
    await fetchSuccess()
  })

  it.each([403, 500])('preserves HTTP %s and valid requests', async status => {
    const { retired } = await watch(), pending = fetchRequest(); await pending.sent
    pending.response.resolve(fetchResponse(status))
    expect(await pending.result).toEqual({ success: false, error: { status, message: `HTTP ${status}` } })
    await fetchSuccess(); expect(retired).not.toHaveBeenCalled(); expect(sdk.redirect).not.toHaveBeenCalled()
  })
})
