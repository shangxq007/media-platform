import { ref, onUnmounted } from 'vue'

export function usePlayback(totalDuration: () => number, frameRate = 30) {
  const isPlaying = ref(false)
  const currentTime = ref(0)
  let intervalId: ReturnType<typeof setInterval> | null = null
  const frameInterval = 1000 / frameRate

  function togglePlayback() {
    isPlaying.value = !isPlaying.value
    if (isPlaying.value) {
      startPlayback()
    } else {
      stopPlayback()
    }
  }

  function startPlayback() {
    if (intervalId) return
    intervalId = setInterval(() => {
      if (currentTime.value >= totalDuration()) {
        currentTime.value = totalDuration()
        stopPlayback()
        isPlaying.value = false
        return
      }
      currentTime.value = Math.round((currentTime.value + frameInterval / 1000) * 1000) / 1000
    }, frameInterval)
  }

  function stopPlayback() {
    if (intervalId) {
      clearInterval(intervalId)
      intervalId = null
    }
  }

  function stepForward(step = 1) {
    currentTime.value = Math.min(currentTime.value + step, totalDuration())
  }

  function stepBackward(step = 1) {
    currentTime.value = Math.max(currentTime.value - step, 0)
  }

  function seek(time: number) {
    const clamped = Math.max(0, Math.min(time, totalDuration()))
    currentTime.value = clamped
    if (isPlaying.value && clamped >= totalDuration()) {
      stopPlayback()
      isPlaying.value = false
    }
  }

  onUnmounted(() => {
    stopPlayback()
  })

  return {
    isPlaying,
    currentTime,
    togglePlayback,
    stepForward,
    stepBackward,
    seek,
    startPlayback,
    stopPlayback,
  }
}
