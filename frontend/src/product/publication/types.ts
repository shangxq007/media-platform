import type { PublicationAccountDto, PublicationDetailRequest, PublicationListRequest, PublicationPostDto, PublicationPostListDto } from '../../api/publish'
import type { PublicationReadSource as PlatformPublicationReadSource } from '../../foundation/platformClient'

export type PublicationAccount = PublicationAccountDto
export type PublicationPost = PublicationPostDto
export type PublicationPostList = PublicationPostListDto
export type PublicationRequest = PublicationListRequest
export type PublicationDetail = PublicationDetailRequest
export type PublicationReadSource = PlatformPublicationReadSource

export interface PublicationFilters {
  readonly query: string
  readonly order: 'asc' | 'desc'
}

export interface PublicationWindow {
  readonly start: string
  readonly end: string
}

export interface PublicationSnapshot {
  readonly account: PublicationAccount
  readonly bindingVersion: number
  readonly window: PublicationWindow
  readonly coverage: 'BOUNDED_PARTIAL'
  readonly posts: readonly PublicationPost[]
}
