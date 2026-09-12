import type { ProjectSummary } from '../../foundation/platformClient'

export type ProjectOrder = 'created-desc' | 'name-asc' | 'name-desc'
export interface ProjectBrowsing {
  query: string
  status: string
  order: ProjectOrder
  selectedId: string | null
  scrollTop: number
}
export const defaultProjectBrowsing = (): ProjectBrowsing => ({ query: '', status: '', order: 'created-desc', selectedId: null, scrollTop: 0 })
const compare = (a: string, b: string) => a < b ? -1 : a > b ? 1 : 0
const nameKey = (name: string) => name.toLowerCase()
const date = (value?: string | null) => value && Number.isFinite(Date.parse(value)) ? Date.parse(value) : -Infinity
export function selectRecentProjects(projects: readonly ProjectSummary[], browsing: ProjectBrowsing): ProjectSummary[] {
  const query = browsing.query.trim().toLowerCase()
  return projects.filter(project => nameKey(project.name).includes(query) && (!browsing.status || project.status === browsing.status))
    .sort((a, b) => {
      const names = compare(nameKey(a.name), nameKey(b.name))
      const primary = browsing.order === 'created-desc'
        ? (date(a.createdAt) === date(b.createdAt) ? 0 : date(a.createdAt) > date(b.createdAt) ? -1 : 1)
        : names * (browsing.order === 'name-desc' ? -1 : 1)
      return primary || names || compare(a.name, b.name) || compare(a.id, b.id)
    })
}
