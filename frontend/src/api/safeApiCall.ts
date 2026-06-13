// =============================================================================
// Safe API Call Utility
// =============================================================================
// Wraps all API calls with mandatory schema validation.
// Returns discriminated union: { success, data } | { success, error, fallback }
// =============================================================================

import { z } from 'zod'
import { contractGuard } from './guard/contract-guard'

export interface SafeApiSuccess<T> {
  success: true
  data: T
  contractVersion: string
  validatedAt: number
}

export interface SafeApiError {
  success: false
  error: ContractViolation
  fallback: null
}

export type SafeApiResult<T> = SafeApiSuccess<T> | SafeApiError

export interface ContractViolation {
  code: 'SCHEMA_MISMATCH' | 'NETWORK_ERROR' | 'UNKNOWN'
  message: string
  context: string
  details?: z.ZodError
  timestamp: number
}

const CONTRACT_VERSION = '1.0.0'

export async function safeApiCall<T>(
  schema: z.ZodType<T>,
  fetchFn: () => Promise<unknown>,
  context: string
): Promise<SafeApiResult<T>> {
  try {
    const rawData = await fetchFn()

    const result = schema.safeParse(rawData)

    if (result.success) {
      contractGuard.recordSuccess(context)
      return {
        success: true,
        data: result.data,
        contractVersion: CONTRACT_VERSION,
        validatedAt: Date.now(),
      }
    }

    const violation: ContractViolation = {
      code: 'SCHEMA_MISMATCH',
      message: `Contract violation in ${context}`,
      context,
      details: result.error,
      timestamp: Date.now(),
    }

    contractGuard.recordViolation(violation)

    if (import.meta.env.DEV) {
      console.error(
        `[Contract Enforcement] Schema mismatch in ${context}:`,
        result.error.format()
      )
    }

    return {
      success: false,
      error: violation,
      fallback: null,
    }
  } catch (err) {
    const violation: ContractViolation = {
      code: 'NETWORK_ERROR',
      message: err instanceof Error ? err.message : 'Unknown API error',
      context,
      timestamp: Date.now(),
    }

    contractGuard.recordViolation(violation)

    return {
      success: false,
      error: violation,
      fallback: null,
    }
  }
}

export async function safeApiCallList<T>(
  schema: z.ZodType<T>,
  fetchFn: () => Promise<unknown>,
  context: string
): Promise<SafeApiResult<T[]>> {
  try {
    const rawData = await fetchFn()

    if (!Array.isArray(rawData)) {
      const violation: ContractViolation = {
        code: 'SCHEMA_MISMATCH',
        message: `Expected array in ${context}, got ${typeof rawData}`,
        context,
        timestamp: Date.now(),
      }
      contractGuard.recordViolation(violation)
      return { success: false, error: violation, fallback: null }
    }

    const validatedItems: T[] = []
    const errors: z.ZodError[] = []

    for (let i = 0; i < rawData.length; i++) {
      const result = schema.safeParse(rawData[i])
      if (result.success) {
        validatedItems.push(result.data)
      } else {
        errors.push(result.error)
      }
    }

    if (errors.length > 0) {
      const violation: ContractViolation = {
        code: 'SCHEMA_MISMATCH',
        message: `${errors.length}/${rawData.length} items failed validation in ${context}`,
        context,
        timestamp: Date.now(),
      }
      contractGuard.recordViolation(violation)

      if (import.meta.env.DEV) {
        console.warn(
          `[Contract Enforcement] ${errors.length} items failed in ${context}`
        )
      }
    }

    contractGuard.recordSuccess(context)

    return {
      success: true,
      data: validatedItems,
      contractVersion: CONTRACT_VERSION,
      validatedAt: Date.now(),
    }
  } catch (err) {
    const violation: ContractViolation = {
      code: 'NETWORK_ERROR',
      message: err instanceof Error ? err.message : 'Unknown API error',
      context,
      timestamp: Date.now(),
    }
    contractGuard.recordViolation(violation)
    return { success: false, error: violation, fallback: null }
  }
}
