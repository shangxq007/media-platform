import { describe, expect, it } from 'vitest'
import { defaultProjectBrowsing, selectRecentProjects } from './model'

const projects = [
  { id: 'z', name: 'Alpha', status: 'ACTIVE', createdAt: null },
  { id: 'b', name: 'Alpha', status: 'ACTIVE', createdAt: '2026-01-01' },
  { id: 'a', name: 'Alpha', status: 'ACTIVE', createdAt: '2026-01-01' },
  { id: 'c', name: 'Beta', status: 'ARCHIVED', createdAt: 'invalid' },
  { id: 'd', name: 'Gamma', status: 'ACTIVE', createdAt: '2026-02-01' },
]
describe('returned recent project presentation', () => {
  it('composes trimmed name search, real status and sorting without mutating the source', () => {
    expect(selectRecentProjects(projects, { ...defaultProjectBrowsing(), query: ' AL ', status: 'ACTIVE' }).map(p => p.id)).toEqual(['a', 'b', 'z'])
    expect(projects.map(p => p.id)).toEqual(['z', 'b', 'a', 'c', 'd'])
  })
  it('orders ties by name and ID, and missing or invalid dates last', () => {
    for (const input of [projects, [...projects].reverse()]) {
      expect(selectRecentProjects(input, defaultProjectBrowsing()).map(p => p.id)).toEqual(['d', 'a', 'b', 'z', 'c'])
      expect(selectRecentProjects(input, { ...defaultProjectBrowsing(), order: 'name-desc' }).map(p => p.id)).toEqual(['d', 'c', 'a', 'b', 'z'])
    }
  })
})
