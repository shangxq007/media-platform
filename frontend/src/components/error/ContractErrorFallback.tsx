// =============================================================================
// Contract Error Fallback Component
// =============================================================================
// Displayed when API response fails contract validation.
// Shows user-friendly error with debug panel in dev mode.
// =============================================================================

import { useState } from 'react'
import type { ContractViolationRecord } from '../../api/guard/contract-guard'
import { contractGuard } from '../../api/guard/contract-guard'

interface ContractErrorFallbackProps {
  violation: ContractViolationRecord
  context?: string
  onRetry?: () => void
}

export function ContractErrorFallback({ violation, context, onRetry }: ContractErrorFallbackProps) {
  const [showDebug, setShowDebug] = useState(false)

  return (
    <div className="rounded-lg border border-red-800 bg-red-950/50 p-4">
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0">
          <svg className="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clipRule="evenodd" />
          </svg>
        </div>
        <div className="flex-1">
          <h3 className="text-sm font-medium text-red-300">
            Data Format Error
          </h3>
          <p className="mt-1 text-sm text-red-400">
            {context
              ? `Unable to load ${context}. The server returned data in an unexpected format.`
              : 'The server returned data in an unexpected format.'}
          </p>

          {import.meta.env.DEV && (
            <div className="mt-3">
              <button
                type="button"
                onClick={() => setShowDebug(!showDebug)}
                className="text-xs text-red-500 hover:text-red-400 underline"
              >
                {showDebug ? 'Hide' : 'Show'} Debug Info
              </button>

              {showDebug && (
                <div className="mt-2 rounded bg-red-950 p-3 text-xs font-mono text-red-300 overflow-auto max-h-48">
                  <div className="mb-2">
                    <span className="text-red-500">Code:</span> {violation.code}
                  </div>
                  <div className="mb-2">
                    <span className="text-red-500">Context:</span> {violation.context}
                  </div>
                  <div className="mb-2">
                    <span className="text-red-500">Message:</span> {violation.message}
                  </div>
                  <div className="mb-2">
                    <span className="text-red-500">Time:</span>{' '}
                    {new Date(violation.timestamp).toISOString()}
                  </div>
                  {violation.details != null && (
                    <div>
                      <span className="text-red-500">Details:</span>
                      <pre className="mt-1 whitespace-pre-wrap">
                        {JSON.stringify(violation.details, null, 2) ?? 'null'}
                      </pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {onRetry && (
            <div className="mt-3">
              <button
                type="button"
                onClick={onRetry}
                className="rounded bg-red-800 px-3 py-1.5 text-xs font-medium text-red-200 hover:bg-red-700 transition-colors"
              >
                Retry
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// =============================================================================
// Contract Health Panel (dev only)
// =============================================================================

export function ContractHealthPanel() {
  if (!import.meta.env.DEV) return null

  const stats = contractGuard.getStats()
  const hasViolations = stats.violationCount > 0

  if (!hasViolations) return null

  return (
    <div className="fixed bottom-4 right-4 z-50 max-w-sm">
      <div className="rounded-lg border border-yellow-800 bg-yellow-950/90 p-3 shadow-lg backdrop-blur">
        <div className="flex items-center gap-2 mb-2">
          <svg className="h-4 w-4 text-yellow-400" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.168 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
          </svg>
          <span className="text-xs font-medium text-yellow-300">
            Contract Violations Detected
          </span>
        </div>
        <div className="text-xs text-yellow-400">
          <div>Health Score: {stats.healthScore}%</div>
          <div>Violations: {stats.violationCount}</div>
        </div>
      </div>
    </div>
  )
}
