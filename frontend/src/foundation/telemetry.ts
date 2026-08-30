export type FrontendTelemetryEvent =
  | { type: 'route'; routeTemplate: string; surfaceId: string }
  | { type: 'api'; operation: string; outcome: string; durationMs?: number; correlationId?: string }
  | { type: 'effective-access'; accessKey: string; outcome: string }
  | { type: 'action'; commandId: string; outcome: string }
  | { type: 'fatal'; category: string; correlationId?: string }
  | { type: 'performance'; metric: string; value: number; routeTemplate: string }

export type TelemetrySink = (event: FrontendTelemetryEvent) => void

let sink: TelemetrySink | null = null

export function configureFrontendTelemetry(nextSink: TelemetrySink | null): void {
  sink = nextSink
}

export function emitFrontendTelemetry(event: FrontendTelemetryEvent): void {
  sink?.(event)
}
