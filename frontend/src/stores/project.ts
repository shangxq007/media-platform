import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Project, RenderJob } from '@/types'

export const useProjectStore = defineStore('project', () => {
  const currentProject = ref<Project | null>(null)
  const projects = ref<Project[]>([])
  const renderJobs = ref<RenderJob[]>([])
  const currentTenant = ref<string>('')
  const loading = ref(false)
  const error = ref<string | null>(null)

  const hasProject = computed(() => currentProject.value !== null)

  function setTenant(tenantId: string) {
    currentTenant.value = tenantId
  }

  function setProject(project: Project) {
    currentProject.value = project
  }

  function addProject(project: Project) {
    projects.value.push(project)
  }

  function addRenderJob(job: RenderJob) {
    renderJobs.value.push(job)
  }

  function updateRenderJob(jobId: string, updates: Partial<RenderJob>) {
    const idx = renderJobs.value.findIndex(j => j.id === jobId)
    if (idx >= 0) {
      renderJobs.value[idx] = { ...renderJobs.value[idx], ...updates }
    }
  }

  function setError(msg: string | null) {
    error.value = msg
  }

  function setLoading(val: boolean) {
    loading.value = val
  }

  return {
    currentProject, projects, renderJobs, currentTenant, loading, error, hasProject,
    setTenant, setProject, addProject, addRenderJob, updateRenderJob, setError, setLoading
  }
})
