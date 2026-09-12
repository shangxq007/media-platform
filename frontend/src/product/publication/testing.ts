// Explicit test-only data; never imported by the ordinary product route.
import { vi } from 'vitest'
import type { PublicationAccount, PublicationPost, PublicationPostList, PublicationReadSource } from './types'

export function fixtureAccount(changes: Partial<PublicationAccount> = {}): PublicationAccount {
  return {
    id: 'a', displayName: 'Studio North', displayNameAvailability: 'AVAILABLE', platformType: 'YOUTUBE',
    projectId: 'p', bindingVersion: 4, endpointAccess: 'AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ', globalEffectiveAccess: 'UNKNOWN_FAIL_CLOSED',
    ...changes,
  }
}

export function fixturePost(changes: Partial<PublicationPost> = {}): PublicationPost {
  return {
    id: 'one', projectId: 'p', connectedAccountId: 'a', bindingVersion: 4,
    contentText: 'Opening story', contentAvailability: 'AVAILABLE', contentVersionRelationState: 'NOT_PROVIDED',
    artifactId: 'artifact-one', artifactRelationState: 'AVAILABLE', platformType: 'YOUTUBE',
    scheduledAt: '2026-09-10T12:00:00Z', timeMeaning: 'PLANNED_PUBLISH_TIME', timePrecision: 'EXACT_INSTANT',
    sourceVerification: 'VERIFIED_LOCAL_RECORD', endpointAccess: 'AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ', globalEffectiveAccess: 'UNKNOWN_FAIL_CLOSED',
    ...changes,
  }
}

export function fixtureList(changes: Partial<PublicationPostList> = {}): PublicationPostList {
  return { items: [fixturePost()], coverage: 'BOUNDED_PARTIAL', ...changes }
}

interface SourceOptions {
  readonly origin?: PublicationReadSource['origin']
  readonly accounts?: PublicationAccount[]
  readonly list?: PublicationPostList
  readonly detail?: PublicationPost
}

export function source(options: SourceOptions = {}) {
  const accounts = options.accounts ?? [fixtureAccount()]
  const getAccounts = vi.fn(async (_projectId: string, _signal: AbortSignal) => accounts)
  const getPosts = vi.fn(async (request: Parameters<PublicationReadSource['getPosts']>[0], _signal: AbortSignal) => options.list ?? (request.connectedAccountId === 'a'
    ? fixtureList({ items: [fixturePost({
      projectId: request.projectId,
      connectedAccountId: request.connectedAccountId,
      bindingVersion: request.bindingVersion,
    })] })
    : fixtureList({ items: [fixturePost({
      id: `post-${request.connectedAccountId}`,
      connectedAccountId: request.connectedAccountId,
      bindingVersion: request.bindingVersion,
      contentText: undefined,
      contentAvailability: 'NOT_PROVIDED',
      artifactId: undefined,
      artifactRelationState: 'NOT_PROVIDED',
    })] })))
  const getPost = vi.fn(async (request: Parameters<PublicationReadSource['getPost']>[0], _signal: AbortSignal) => options.detail ?? fixturePost({
    id: request.id,
    projectId: request.projectId,
    connectedAccountId: request.connectedAccountId,
    bindingVersion: request.bindingVersion,
  }))
  return { origin: options.origin ?? 'fixture-verification', owner: {}, getAccounts, getPosts, getPost } satisfies PublicationReadSource
}
