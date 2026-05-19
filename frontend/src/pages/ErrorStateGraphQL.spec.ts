import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ErrorState from '@/components/ui/ErrorState.vue'

describe('ErrorState displays GraphQL errorCode', () => {
  it('renders GraphQL error code in error state', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Access Denied',
        description: 'You do not have permission to view this resource',
        errorCode: 'GRAPHQL-403-001',
        diagnosticId: 'trace-abc-123',
      },
    })

    expect(wrapper.text()).toContain('GRAPHQL-403-001')
    expect(wrapper.text()).toContain('Access Denied')
    expect(wrapper.text()).toContain('trace-abc-123')
  })

  it('renders ENTITLEMENT error code', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Feature Not Allowed',
        description: 'Your current plan does not include this feature',
        errorCode: 'ENTITLEMENT-403-001',
      },
    })

    expect(wrapper.text()).toContain('ENTITLEMENT-403-001')
  })

  it('renders PROMPT error code', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Template Not Found',
        description: 'The requested prompt template could not be found',
        errorCode: 'PROMPT-404-001',
      },
    })

    expect(wrapper.text()).toContain('PROMPT-404-001')
  })

  it('renders SECURITY error code', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Rate Limit Exceeded',
        description: 'Too many requests, please try again later',
        errorCode: 'SECURITY-429-001',
      },
    })

    expect(wrapper.text()).toContain('SECURITY-429-001')
  })

  it('renders INTERNAL error code', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Server Error',
        description: 'An unexpected error occurred',
        errorCode: 'COMMON-500-001',
      },
    })

    expect(wrapper.text()).toContain('COMMON-500-001')
  })

  it('does not show error code section when errorCode is not provided', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Simple Error',
        description: 'Something went wrong',
      },
    })

    expect(wrapper.text()).not.toContain('Error Code:')
  })

  it('renders error code in monospace font', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Error',
        description: 'Description',
        errorCode: 'GRAPHQL-403-001',
      },
    })

    const codeEl = wrapper.find('code')
    expect(codeEl.exists()).toBe(true)
    expect(codeEl.text()).toBe('GRAPHQL-403-001')
  })

  it('renders diagnostic ID alongside error code', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Error',
        description: 'Description',
        errorCode: 'GRAPHQL-500-001',
        diagnosticId: 'trace-xyz-789',
      },
    })

    expect(wrapper.text()).toContain('GRAPHQL-500-001')
    expect(wrapper.text()).toContain('trace-xyz-789')
    expect(wrapper.text()).toContain('Copy')
  })

  it('renders retry button for recoverable GraphQL errors', () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Server Error',
        description: 'Temporary error, please retry',
        errorCode: 'COMMON-502-001',
        showRetry: true,
      },
    })

    expect(wrapper.text()).toContain('Retry')
  })

  it('emits retry event for GraphQL errors', async () => {
    const wrapper = mount(ErrorState, {
      props: {
        title: 'Server Error',
        description: 'Temporary error',
        errorCode: 'COMMON-500-001',
        showRetry: true,
      },
    })

    const retryBtn = wrapper.findAll('button').find(b => b.text().includes('Retry'))
    await retryBtn?.trigger('click')
    expect(wrapper.emitted('retry')).toBeTruthy()
  })

  it('renders all GraphQL error code formats', () => {
    const errorCodes = [
      'GRAPHQL-400-001',
      'GRAPHQL-401-001',
      'GRAPHQL-403-001',
      'GRAPHQL-404-001',
      'GRAPHQL-500-001',
      'GRAPHQL-502-001',
    ]

    for (const code of errorCodes) {
      const wrapper = mount(ErrorState, {
        props: {
          title: 'Error',
          description: 'Description',
          errorCode: code,
        },
      })

      expect(wrapper.text()).toContain(code)
    }
  })
})
