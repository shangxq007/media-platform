import { ref, shallowRef, watch, type Ref } from 'vue'
import { graphqlRequest, GraphQLError } from '@/api/graphqlClient'

export interface GraphQLQueryOptions<T> {
  query: string
  variables?: Record<string, unknown>
  fallbackFn?: () => Promise<T>
  immediate?: boolean
  transform?: (data: unknown) => T
}

export interface GraphQLQueryResult<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<GraphQLError | Error | null>
  errorCode: Ref<string | undefined>
  traceId: Ref<string | undefined>
  refetch: () => Promise<T | null>
}

export function useGraphQLQuery<T>(options: GraphQLQueryOptions<T>): GraphQLQueryResult<T> {
  const data = shallowRef<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<GraphQLError | Error | null>(null)
  const errorCode = ref<string | undefined>(undefined)
  const traceId = ref<string | undefined>(undefined)

  async function execute(): Promise<T | null> {
    loading.value = true
    error.value = null
    errorCode.value = undefined
    traceId.value = undefined

    try {
      const raw = await graphqlRequest<unknown>(options.query, options.variables)
      const result = options.transform ? options.transform(raw) : raw as T
      data.value = result
      return result
    } catch (err) {
      if (options.fallbackFn) {
        try {
          const fallbackResult = await options.fallbackFn()
          data.value = fallbackResult
          error.value = null
          errorCode.value = undefined
          traceId.value = undefined
          return fallbackResult
        } catch (fallbackErr) {
          error.value = fallbackErr instanceof Error ? fallbackErr : new Error(String(fallbackErr))
          if (fallbackErr instanceof GraphQLError) {
            errorCode.value = fallbackErr.errorCode
            traceId.value = fallbackErr.traceId
          }
          return null
        }
      }

      if (err instanceof GraphQLError) {
        error.value = err
        errorCode.value = err.errorCode
        traceId.value = err.traceId
      } else if (err instanceof Error) {
        error.value = err
      } else {
        error.value = new Error(String(err))
      }
      return null
    } finally {
      loading.value = false
    }
  }

  if (options.immediate !== false) {
    execute()
  }

  return {
    data,
    loading,
    error,
    errorCode,
    traceId,
    refetch: execute,
  }
}

export function useGraphQLQueryWithVars<T>(
  query: string,
  variables: Ref<Record<string, unknown>>,
  fallbackFn?: () => Promise<T>
): GraphQLQueryResult<T> {
  const result = useGraphQLQuery<T>({
    query,
    variables: variables.value,
    fallbackFn,
    immediate: false,
  })

  watch(
    variables,
    () => {
      result.refetch()
    },
    { deep: true, immediate: true }
  )

  return result
}
