// =============================================================================
// Contract Boundary Guard
// =============================================================================
// Tracks contract violations and provides mismatch statistics.
// Single source of truth for contract health monitoring.
// =============================================================================

export interface ContractViolationRecord {
  code: 'SCHEMA_MISMATCH' | 'NETWORK_ERROR' | 'UNKNOWN'
  message: string
  context: string
  timestamp: number
  details?: unknown
}

export interface ContractStats {
  totalCalls: number
  successCount: number
  violationCount: number
  violationsByContext: Record<string, number>
  recentViolations: ContractViolationRecord[]
  healthScore: number
}

const MAX_RECENT_VIOLATIONS = 50

class ContractGuard {
  private totalCalls = 0
  private successCount = 0
  private violations: ContractViolationRecord[] = []
  private violationsByContext: Record<string, number> = {}

  recordSuccess(context: string): void {
    this.totalCalls++
    this.successCount++
  }

  recordViolation(violation: ContractViolationRecord): void {
    this.totalCalls++
    this.violations.push(violation)

    if (this.violations.length > MAX_RECENT_VIOLATIONS) {
      this.violations = this.violations.slice(-MAX_RECENT_VIOLATIONS)
    }

    this.violationsByContext[violation.context] =
      (this.violationsByContext[violation.context] || 0) + 1
  }

  getStats(): ContractStats {
    return {
      totalCalls: this.totalCalls,
      successCount: this.successCount,
      violationCount: this.violations.length,
      violationsByContext: { ...this.violationsByContext },
      recentViolations: [...this.violations],
      healthScore: this.totalCalls > 0
        ? Math.round((this.successCount / this.totalCalls) * 100)
        : 100,
    }
  }

  hasViolations(): boolean {
    return this.violations.length > 0
  }

  getViolationsForContext(context: string): ContractViolationRecord[] {
    return this.violations.filter(v => v.context === context)
  }

  reset(): void {
    this.totalCalls = 0
    this.successCount = 0
    this.violations = []
    this.violationsByContext = {}
  }
}

export const contractGuard = new ContractGuard()
