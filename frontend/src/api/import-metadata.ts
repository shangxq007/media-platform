import api from './index'

export interface ProjectImportedMetadataSummaryDto {
  importId: string
  sourceProjectId?: string
  sourceExportId?: string
  schemaVersion?: string
  timelinePresent: boolean
  timelineOtioPresent: boolean
  renderPlanPresent: boolean
  spatialPlanPresent: boolean
  exportProfilesPresent: boolean
  effectTaxonomyPresent: boolean
  appliedEffectsPresent: boolean
  assetMappingPresent: boolean
  assetsNeedUpload: boolean
  createdAt: string
}

export interface ImportAssetMappingEntry {
  targetAssetId: string | null
  status: string
}

export interface ProjectImportedMetadataDetailDto {
  summary: ProjectImportedMetadataSummaryDto
  timeline?: unknown
  timelineOtio?: unknown
  renderPlan?: unknown
  spatialPlan?: unknown
  exportProfiles?: unknown
  effectTaxonomy?: unknown
  appliedEffects?: unknown
  assetMapping?: Record<string, ImportAssetMappingEntry>
  warnings: string[]
}

export const ImportMetadataAPI = {
  async getSummary(
    tenantId: string,
    projectId: string
  ): Promise<ProjectImportedMetadataSummaryDto | null> {
    try {
      const { data } = await api.get(
        `/identity/tenants/${tenantId}/projects/${projectId}/import-metadata`
      )
      return data
    } catch (e: unknown) {
      if (e instanceof Error && 'response' in e) {
        const resp = (e as { response?: { status: number } }).response
        if (resp?.status === 404) return null
      }
      throw e
    }
  },

  async getDetail(
    tenantId: string,
    projectId: string
  ): Promise<ProjectImportedMetadataDetailDto | null> {
    try {
      const { data } = await api.get(
        `/identity/tenants/${tenantId}/projects/${projectId}/import-metadata/detail`
      )
      return data
    } catch (e: unknown) {
      if (e instanceof Error && 'response' in e) {
        const resp = (e as { response?: { status: number } }).response
        if (resp?.status === 404) return null
      }
      throw e
    }
  },

  async getSummaryByImportId(
    tenantId: string,
    importId: string
  ): Promise<ProjectImportedMetadataSummaryDto | null> {
    try {
      const { data } = await api.get(
        `/identity/tenants/${tenantId}/project-imports/${importId}/metadata`
      )
      return data
    } catch (e: unknown) {
      if (e instanceof Error && 'response' in e) {
        const resp = (e as { response?: { status: number } }).response
        if (resp?.status === 404) return null
      }
      throw e
    }
  },

  async getDetailByImportId(
    tenantId: string,
    importId: string
  ): Promise<ProjectImportedMetadataDetailDto | null> {
    try {
      const { data } = await api.get(
        `/identity/tenants/${tenantId}/project-imports/${importId}/metadata/detail`
      )
      return data
    } catch (e: unknown) {
      if (e instanceof Error && 'response' in e) {
        const resp = (e as { response?: { status: number } }).response
        if (resp?.status === 404) return null
      }
      throw e
    }
  }
}
