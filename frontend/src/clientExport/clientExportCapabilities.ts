export interface WebCodecsCapability {
  supported: boolean
  videoEncoder: boolean
  reasons: string[]
}

export function detectClientExportCapabilities(): WebCodecsCapability {
  const reasons: string[] = []
  if (typeof window === 'undefined') {
    return { supported: false, videoEncoder: false, reasons: ['NO_WINDOW'] }
  }
  const hasCanvas = typeof HTMLCanvasElement !== 'undefined'
  const hasMediaRecorder = typeof MediaRecorder !== 'undefined'
  const videoEncoder = typeof VideoEncoder !== 'undefined'
  if (!hasCanvas) {
    reasons.push('NO_CANVAS')
  }
  if (!hasMediaRecorder && !videoEncoder) {
    reasons.push('NO_ENCODER')
  }
  const supported = hasCanvas && (hasMediaRecorder || videoEncoder)
  return { supported, videoEncoder, reasons }
}
