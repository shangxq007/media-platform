<script setup lang="ts">
import { ref, watch } from 'vue'
import type { EffectParameterDef } from '@/types'

const props = defineProps<{
  parameterName: string
  definition: EffectParameterDef
  modelValue: unknown
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
}>()

const localValue = ref<unknown>(props.modelValue)

watch(() => props.modelValue, (v) => {
  localValue.value = v
}, { immediate: true })

function updateValue(v: unknown) {
  localValue.value = v
  emit('update:modelValue', v)
}

function handleSliderInput(e: Event) {
  const val = parseFloat((e.target as HTMLInputElement).value)
  if (props.definition.type === 'int') {
    updateValue(Math.round(val))
  } else {
    updateValue(val)
  }
}

function handleNumberInput(e: Event) {
  const val = (e.target as HTMLInputElement).value
  if (props.definition.type === 'int') {
    const parsed = parseInt(val, 10)
    if (!isNaN(parsed)) updateValue(parsed)
  } else {
    const parsed = parseFloat(val)
    if (!isNaN(parsed)) updateValue(parsed)
  }
}

function handleToggle(e: Event) {
  updateValue((e.target as HTMLInputElement).checked)
}

function handleTextInput(e: Event) {
  updateValue((e.target as HTMLInputElement).value)
}

function handleColorInput(e: Event) {
  updateValue((e.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center justify-between">
      <label class="text-[10px] text-gray-400">{{ parameterName }}</label>
      <span v-if="definition.description" class="text-[9px] text-gray-600" :title="definition.description">ⓘ</span>
    </div>

    <template v-if="definition.type === 'float'">
      <div class="flex items-center gap-2">
        <input
          type="range"
          :value="Number(localValue ?? definition.defaultValue ?? 0)"
          :min="definition.min ?? 0"
          :max="definition.max ?? 100"
          :step="0.01"
          class="flex-1 h-1 accent-primary-500"
          @input="handleSliderInput"
        />
        <input
          type="number"
          :value="Number(localValue ?? definition.defaultValue ?? 0)"
          :min="definition.min ?? 0"
          :max="definition.max ?? 100"
          :step="0.01"
          class="w-16 bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-[10px] text-white font-mono text-right focus:outline-none focus:border-primary-400"
          @input="handleNumberInput"
        />
      </div>
    </template>

    <template v-else-if="definition.type === 'int'">
      <div class="flex items-center gap-2">
        <input
          type="range"
          :value="Number(localValue ?? definition.defaultValue ?? 0)"
          :min="definition.min ?? 0"
          :max="definition.max ?? 100"
          :step="1"
          class="flex-1 h-1 accent-primary-500"
          @input="handleSliderInput"
        />
        <input
          type="number"
          :value="Number(localValue ?? definition.defaultValue ?? 0)"
          :min="definition.min ?? 0"
          :max="definition.max ?? 100"
          :step="1"
          class="w-16 bg-gray-800 border border-gray-600 rounded px-1 py-0.5 text-[10px] text-white font-mono text-right focus:outline-none focus:border-primary-400"
          @input="handleNumberInput"
        />
      </div>
    </template>

    <template v-else-if="definition.type === 'boolean'">
      <div class="flex items-center gap-2">
        <button
          class="relative w-8 h-4 rounded-full transition-colors"
          :class="Boolean(localValue) ? 'bg-primary-500' : 'bg-gray-600'"
          @click="handleToggle({ target: { checked: !localValue } } as unknown as Event)"
        >
          <span
            class="absolute top-0.5 w-3 h-3 rounded-full bg-white transition-transform"
            :class="Boolean(localValue) ? 'left-4.5' : 'left-0.5'"
          />
        </button>
        <span class="text-[10px] text-gray-500">{{ Boolean(localValue) ? 'On' : 'Off' }}</span>
      </div>
    </template>

    <template v-else-if="definition.type === 'color'">
      <div class="flex items-center gap-2">
        <input
          type="color"
          :value="String(localValue ?? definition.defaultValue ?? '#ffffff')"
          class="w-6 h-6 rounded border border-gray-600 cursor-pointer bg-transparent"
          @input="handleColorInput"
        />
        <input
          type="text"
          :value="String(localValue ?? definition.defaultValue ?? '#ffffff')"
          class="flex-1 bg-gray-800 border border-gray-600 rounded px-1.5 py-0.5 text-[10px] text-white font-mono focus:outline-none focus:border-primary-400"
          placeholder="#rrggbb"
          @input="handleTextInput"
        />
      </div>
    </template>

    <template v-else-if="definition.type === 'string'">
      <input
        type="text"
        :value="String(localValue ?? definition.defaultValue ?? '')"
        class="w-full bg-gray-800 border border-gray-600 rounded px-2 py-1 text-[10px] text-white focus:outline-none focus:border-primary-400"
        :placeholder="definition.description || ''"
        @input="handleTextInput"
      />
    </template>
  </div>
</template>
