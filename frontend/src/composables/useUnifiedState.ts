import { ref } from 'vue'
import { getErrorMessage } from '@/utils/i18n'

export type UnifiedStateType =
  | 'forbidden'
  | 'disabled-feature'
  | 'quota-exceeded'
  | 'billing-required'
  | 'provider-unavailable'
  | 'worker-offline'
  | 'error'
  | 'empty'
  | 'loading'

export type UserRole = 'admin' | 'user' | 'viewer'

export interface UnifiedStateAction {
  label: string
  action: string
  variant: 'primary' | 'secondary' | 'ghost'
  emit: string
}

export interface UnifiedStateConfig {
  type: UnifiedStateType
  title: string
  description: string
  errorCode?: string | number
  icon?: string
  actions: UnifiedStateAction[]
}

type Locale = 'en' | 'zh'

const locale = ref<Locale>('en')

const stateTitles: Record<UnifiedStateType, Record<Locale, string>> = {
  'forbidden': { en: 'Access Denied', zh: '访问被拒绝' },
  'disabled-feature': { en: 'Feature Disabled', zh: '功能已禁用' },
  'quota-exceeded': { en: 'Quota Exceeded', zh: '配额已超出' },
  'billing-required': { en: 'Billing Required', zh: '需要账单信息' },
  'provider-unavailable': { en: 'Provider Unavailable', zh: '提供者不可用' },
  'worker-offline': { en: 'Worker Offline', zh: '工作器离线' },
  'error': { en: 'Something went wrong', zh: '出错了' },
  'empty': { en: 'No data', zh: '暂无数据' },
  'loading': { en: 'Loading...', zh: '加载中...' },
}

const stateDescriptions: Record<UnifiedStateType, Record<Locale, string>> = {
  'forbidden': { en: 'You do not have permission to access this page.', zh: '您没有权限访问此页面。' },
  'disabled-feature': { en: 'This feature is currently disabled for your account.', zh: '此功能当前已对您的帐户禁用。' },
  'quota-exceeded': { en: 'You have exceeded your quota limit. Please upgrade or request an increase.', zh: '您已超出配额限制。请升级或申请增加。' },
  'billing-required': { en: 'A valid billing method is required to access this feature.', zh: '需要有效的付款方式才能访问此功能。' },
  'provider-unavailable': { en: 'The service provider is currently unavailable. Please try again later.', zh: '服务提供者当前不可用。请稍后重试。' },
  'worker-offline': { en: 'The render worker is currently offline.', zh: '渲染工作器当前离线。' },
  'error': { en: 'An unexpected error occurred. Please try again.', zh: '发生了意外错误。请重试。' },
  'empty': { en: 'There is no data to display.', zh: '没有可显示的数据。' },
  'loading': { en: 'Please wait while we load the data.', zh: '请稍候，正在加载数据。' },
}

const errorCodeMap: Record<string, UnifiedStateType> = {
  'COMMON-401-001': 'forbidden',
  'COMMON-403-001': 'forbidden',
  'EFFECT-403-001': 'forbidden',
  'NAV-403-DISABLED': 'disabled-feature',
  'NAV-403-SOURCE': 'disabled-feature',
  'NAV-403-ROLE': 'forbidden',
  'NAV-403-PERM': 'forbidden',
  'NAV-403-TIER': 'billing-required',
  'NAV-403-FEAT': 'disabled-feature',
  'NAV-403-ENT': 'forbidden',
  'RENDER-503-001': 'provider-unavailable',
  'MONITORING-503-001': 'provider-unavailable',
}

export function useUnifiedState() {
  function setLocale(l: Locale) {
    locale.value = l
  }

  function mapErrorCodeToState(errorCode: string): UnifiedStateType {
    return errorCodeMap[errorCode] || 'error'
  }

  function getErrorTitle(stateType: UnifiedStateType, loc?: Locale): string {
    const l = loc || locale.value
    return stateTitles[stateType]?.[l] || stateTitles[stateType]?.en || 'Error'
  }

  function getErrorDescription(stateType: UnifiedStateType, loc?: Locale): string {
    const l = loc || locale.value
    return stateDescriptions[stateType]?.[l] || stateDescriptions[stateType]?.en || ''
  }

  function getErrorActions(stateType: UnifiedStateType, userRole?: UserRole): UnifiedStateAction[] {
    const actions: UnifiedStateAction[] = []

    switch (stateType) {
      case 'forbidden':
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
        if (userRole !== 'admin') {
          actions.unshift({ label: 'Upgrade Plan', action: 'upgrade', variant: 'primary', emit: 'upgrade' })
          actions.splice(1, 0, { label: 'Contact Admin', action: 'contactAdmin', variant: 'secondary', emit: 'contactAdmin' })
        }
        break
      case 'disabled-feature':
        actions.push({ label: 'Upgrade Plan', action: 'upgrade', variant: 'primary', emit: 'upgrade' })
        actions.push({ label: 'Contact Admin', action: 'contactAdmin', variant: 'secondary', emit: 'contactAdmin' })
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
        break
      case 'quota-exceeded':
        actions.push({ label: 'Upgrade for More', action: 'upgrade', variant: 'primary', emit: 'upgrade' })
        actions.push({ label: 'Request Increase', action: 'requestIncrease', variant: 'secondary', emit: 'requestIncrease' })
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
        break
      case 'billing-required':
        actions.push({ label: 'Go to Billing', action: 'billing', variant: 'primary', emit: 'goToBilling' })
        actions.push({ label: 'Contact Admin', action: 'contactAdmin', variant: 'secondary', emit: 'contactAdmin' })
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
        break
      case 'provider-unavailable':
        actions.push({ label: 'Retry', action: 'retry', variant: 'primary', emit: 'retry' })
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
        break
      case 'worker-offline':
        actions.push({ label: 'Retry Connection', action: 'retry', variant: 'primary', emit: 'retry' })
        actions.push({ label: 'Use Local Fallback', action: 'fallback', variant: 'secondary', emit: 'useFallback' })
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
        break
      case 'error':
        actions.push({ label: 'Retry', action: 'retry', variant: 'primary', emit: 'retry' })
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
        break
      default:
        actions.push({ label: 'Go Home', action: 'home', variant: 'ghost', emit: 'goHome' })
    }

    return actions
  }

  function buildStateConfig(
    stateType: UnifiedStateType,
    overrides?: Partial<UnifiedStateConfig>,
    userRole?: UserRole
  ): UnifiedStateConfig {
    return {
      type: stateType,
      title: getErrorTitle(stateType),
      description: getErrorDescription(stateType),
      actions: getErrorActions(stateType, userRole),
      ...overrides,
    }
  }

  function resolveFromErrorCode(errorCode: string, userRole?: UserRole): UnifiedStateConfig {
    const stateType = mapErrorCodeToState(errorCode)
    const i18nMessage = getErrorMessage(errorCode, locale.value)
    return buildStateConfig(stateType, {
      description: i18nMessage !== errorCode ? i18nMessage : undefined,
      errorCode,
    }, userRole)
  }

  return {
    locale,
    setLocale,
    mapErrorCodeToState,
    getErrorTitle,
    getErrorDescription,
    getErrorActions,
    buildStateConfig,
    resolveFromErrorCode,
  }
}
