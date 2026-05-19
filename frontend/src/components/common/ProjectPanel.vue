<script setup lang="ts">
import { ref, computed } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useTimelineStore } from '@/stores/timeline'
import { ProjectAPI } from '@/api'

const projectStore = useProjectStore()
const timelineStore = useTimelineStore()

const showNewProject = ref(false)
const newProjectName = ref('')
const newProjectDesc = ref('')
const saving = ref(false)

const canSave = computed(() => newProjectName.value.trim().length > 0)

async function createProject() {
  if (!canSave.value) return
  saving.value = true
  try {
    const project = await ProjectAPI.create(newProjectName.value, newProjectDesc.value)
    projectStore.addProject(project)
    projectStore.setProject(project)
    showNewProject.value = false
    newProjectName.value = ''
    newProjectDesc.value = ''
  } catch (err: any) {
    projectStore.setError(err.message || 'Failed to create project')
  } finally {
    saving.value = false
  }
}

function saveTimeline() {
  const json = timelineStore.toJSON()
  localStorage.setItem(`timeline_${projectStore.currentProject?.id || 'draft'}`, JSON.stringify(json))
}

function loadTimeline(projectId: string) {
  const data = localStorage.getItem(`timeline_${projectId}`)
  if (data) {
    try {
      timelineStore.loadFromJSON(JSON.parse(data))
    } catch {
      console.warn('Failed to parse timeline JSON')
    }
  }
}

function selectProject(project: { id: string; name: string }) {
    const p = projectStore.projects.find(prj => prj.id === project.id)
    if (p) {
      projectStore.setProject(p)
      loadTimeline(project.id)
    }
  }

function exportTimelineJSON() {
  const json = timelineStore.toJSON()
  const blob = new Blob([JSON.stringify(json, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `timeline_${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="flex flex-col h-full p-2 space-y-3">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-white">Projects</h3>
      <button
        class="text-xs px-2 py-1 bg-clip-video/20 text-clip-video rounded hover:bg-clip-video/30"
        @click="showNewProject = !showNewProject"
      >
        {{ showNewProject ? '✕' : '+ New' }}
      </button>
    </div>

    <div v-if="showNewProject" class="space-y-2 p-2 rounded bg-gray-800/50 border border-gray-700">
      <input
        v-model="newProjectName"
        type="text"
        placeholder="Project name"
        class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white"
      />
      <input
        v-model="newProjectDesc"
        type="text"
        placeholder="Description (optional)"
        class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-xs text-white"
      />
      <button
        class="w-full py-1.5 text-xs rounded"
        :class="canSave && !saving ? 'bg-clip-video text-white' : 'bg-gray-600 text-gray-400 cursor-not-allowed'"
        :disabled="!canSave || saving"
        @click="createProject"
      >
        {{ saving ? 'Creating...' : 'Create Project' }}
      </button>
    </div>

    <div v-if="projectStore.currentProject" class="p-2 rounded bg-gray-800/50 border border-gray-700">
      <div class="text-xs text-gray-400">Current Project</div>
      <div class="text-sm text-white font-medium">{{ projectStore.currentProject.name }}</div>
      <div class="text-xs text-gray-500 mt-1">{{ projectStore.currentProject.description }}</div>
    </div>

    <div class="space-y-1">
      <div class="text-xs text-gray-400">Saved Projects</div>
      <div
        v-for="p in projectStore.projects"
        :key="p.id"
        class="flex items-center justify-between p-2 rounded cursor-pointer hover:bg-gray-700/50"
        :class="projectStore.currentProject?.id === p.id ? 'bg-clip-video/10 border border-clip-video/30' : 'border border-transparent'"
        @click="selectProject({ ...p, name: p.name })"
      >
        <div>
          <div class="text-xs text-white">{{ p.name }}</div>
          <div class="text-xs text-gray-500">{{ p.status || 'ACTIVE' }}</div>
        </div>
      </div>
      <div v-if="!projectStore.projects.length" class="text-xs text-gray-500 text-center py-2">
        No projects yet
      </div>
    </div>

    <div class="space-y-1 pt-2 border-t border-gray-700">
      <button
        class="w-full py-1.5 text-xs bg-gray-700 text-white rounded hover:bg-gray-600"
        @click="saveTimeline"
      >
        💾 Save Timeline
      </button>
      <button
        class="w-full py-1.5 text-xs bg-gray-700 text-white rounded hover:bg-gray-600"
        @click="exportTimelineJSON"
      >
        📥 Export JSON
      </button>
    </div>
  </div>
</template>
