import { ref } from 'vue'

type Locale = 'en' | 'zh'

const currentLocale = ref<Locale>('en')

const messages: Record<Locale, Record<string, string>> = {
  en: {
    // Common
    'COMMON-400-001': 'Invalid request',
    'COMMON-404-001': 'Resource not found',
    'COMMON-409-001': 'Conflict',
    'COMMON-500-001': 'Internal server error',
    'COMMON-502-001': 'Integration error',
    'COMMON-401-001': 'Authentication required',
    'COMMON-403-001': 'Insufficient permission',
    // Render
    'RENDER-400-001': 'Invalid render job request',
    'RENDER-404-001': 'Render job not found',
    'RENDER-422-001': 'Render quality check failed',
    'RENDER-500-001': 'Render execution failed',
    'RENDER-503-001': 'No render provider available',
    // Subtitle
    'SUBTITLE-400-001': 'Subtitle parsing failed',
    'SUBTITLE-400-002': 'Unsupported subtitle format',
    'SUBTITLE-404-001': 'Subtitle font not found',
    'SUBTITLE-422-001': 'Font glyph coverage insufficient',
    // Effect
    'EFFECT-400-001': 'Invalid effect parameters',
    'EFFECT-404-001': 'Effect not found',
    'EFFECT-403-001': 'Effect not available for current tier',
    // Timeline
    'TIMELINE-400-001': 'Invalid timeline data',
    'TIMELINE-422-001': 'Timeline schema version incompatible',
    // Migration
    'MIGRATION-400-001': 'Migration plan not found',
    'MIGRATION-409-001': 'Migration conflict detected',
    'MIGRATION-422-001': 'Migration validation failed',
    // Monitoring
    'MONITORING-500-001': 'Monitoring service error',
    'MONITORING-503-001': 'Session replay service unavailable',
    // Feedback
    'FEEDBACK-400-001': 'Invalid feedback submission',
    'FEEDBACK-500-001': 'Feedback submission failed',
    // Navigation
    'NAV-404-HIDDEN': 'This page is not available',
    'NAV-404-SYNC': 'Navigation is syncing with the server',
    'NAV-403-DISABLED': 'This feature is currently disabled',
    'NAV-403-SOURCE': 'Not available for this access method',
    'NAV-403-ROLE': 'You do not have the required role',
    'NAV-403-PERM': 'You do not have the required permission',
    'NAV-403-TIER': 'This feature requires a higher subscription tier',
    'NAV-403-FEAT': 'Required features are not enabled',
    'NAV-403-ENT': 'Required entitlement not granted',
    // Notification
    'NOTIFICATION-404-001': 'Notification event not found',
    'NOTIFICATION-403-002': 'This event is not subscribable',
    'NOTIFICATION-404-003': 'Notification subscription not found',
    'NOTIFICATION-400-001': 'Failed to update notification subscription',
    'NOTIFICATION-404-004': 'Notification channel not found',
    'NOTIFICATION-400-002': 'Unsupported notification channel',
    'NOTIFICATION-400-003': 'Channel verification failed',
    'NOTIFICATION-400-004': 'Channel test failed',
    'NOTIFICATION-400-005': 'Invalid webhook URL',
    'NOTIFICATION-403-005': 'Webhook URL points to private/internal IP and is blocked',
    'NOTIFICATION-400-006': 'Webhook signature verification failed',
    'NOTIFICATION-503-001': 'Notification provider unavailable',
    'NOTIFICATION-503-002': 'Novu is not configured',
    'NOTIFICATION-400-007': 'Notification delivery failed',
    'NOTIFICATION-400-008': 'Notification delivery retry exhausted',
    'NOTIFICATION-403-003': 'You do not have permission to manage this notification',
    'NOTIFICATION-403-004': 'Required entitlement not granted for this notification feature',
    'NOTIFICATION-429-001': 'Notification quota exceeded',
    'NOTIFICATION-403-001': 'Critical notifications cannot be disabled',
    'NOTIFICATION-400-009': 'Invalid notification preference',
    'NOTIFICATION-400-010': 'System-only event cannot be subscribed',
    'NOTIFICATION-403-006': 'Admin permission required for this notification operation',
    'NOTIFICATION-FALLBACK': 'Using local notification provider (Novu not configured)',
  },
  zh: {
    'COMMON-400-001': '请求参数无效',
    'COMMON-404-001': '资源不存在',
    'COMMON-409-001': '资源冲突',
    'COMMON-500-001': '服务器内部错误',
    'COMMON-502-001': '外部服务集成错误',
    'COMMON-401-001': '需要身份验证',
    'COMMON-403-001': '权限不足',
    'RENDER-400-001': '渲染任务请求无效',
    'RENDER-404-001': '渲染任务不存在',
    'RENDER-422-001': '渲染质量检查未通过',
    'RENDER-500-001': '渲染执行失败',
    'RENDER-503-001': '无可用渲染提供者',
    'SUBTITLE-400-001': '字幕解析失败',
    'SUBTITLE-400-002': '不支持的字幕格式',
    'SUBTITLE-404-001': '字幕字体不存在',
    'SUBTITLE-422-001': '字体字形覆盖不足',
    'EFFECT-400-001': '特效参数无效',
    'EFFECT-404-001': '特效不存在',
    'EFFECT-403-001': '当前等级不可用此特效',
    'TIMELINE-400-001': '时间线数据无效',
    'TIMELINE-422-001': '时间线 schema 版本不兼容',
    'MIGRATION-400-001': '迁移计划不存在',
    'MIGRATION-409-001': '检测到迁移冲突',
    'MIGRATION-422-001': '迁移验证失败',
    'MONITORING-500-001': '监控服务异常',
    'MONITORING-503-001': '会话回放服务不可用',
    'FEEDBACK-400-001': '反馈提交无效',
    'FEEDBACK-500-001': '反馈提交失败',
    'NAV-404-HIDDEN': '此页面不可用',
    'NAV-404-SYNC': '导航正在与服务器同步',
    'NAV-403-DISABLED': '此功能当前已禁用',
    'NAV-403-SOURCE': '此访问方式不可用',
    'NAV-403-ROLE': '您没有所需的角色',
    'NAV-403-PERM': '您没有所需的权限',
    'NAV-403-TIER': '此功能需要更高的订阅等级',
    'NAV-403-FEAT': '所需功能未启用',
    'NAV-403-ENT': '所需的授权未授予',
    'NOTIFICATION-404-001': '通知事件不存在',
    'NOTIFICATION-403-002': '此事件不可订阅',
    'NOTIFICATION-404-003': '通知订阅不存在',
    'NOTIFICATION-400-001': '更新通知订阅失败',
    'NOTIFICATION-404-004': '通知渠道不存在',
    'NOTIFICATION-400-002': '不支持的通知渠道',
    'NOTIFICATION-400-003': '渠道验证失败',
    'NOTIFICATION-400-004': '渠道测试失败',
    'NOTIFICATION-400-005': 'Webhook URL 无效',
    'NOTIFICATION-403-005': 'Webhook URL 指向私有/内部 IP，已被阻止',
    'NOTIFICATION-400-006': 'Webhook 签名验证失败',
    'NOTIFICATION-503-001': '通知提供者不可用',
    'NOTIFICATION-503-002': 'Novu 未配置',
    'NOTIFICATION-400-007': '通知投递失败',
    'NOTIFICATION-400-008': '通知投递重试已耗尽',
    'NOTIFICATION-403-003': '您没有权限管理此通知',
    'NOTIFICATION-403-004': '此通知功能需要相应的权益',
    'NOTIFICATION-429-001': '通知配额已超出',
    'NOTIFICATION-403-001': '关键通知不可关闭',
    'NOTIFICATION-400-009': '无效的通知偏好设置',
    'NOTIFICATION-400-010': '系统事件不可订阅',
    'NOTIFICATION-403-006': '此通知操作需要管理员权限',
    'NOTIFICATION-FALLBACK': '使用本地通知提供者（Novu 未配置）',
  }
}

export function useI18nError() {
  const t = (errorCode: string, fallback?: string): string => {
    return messages[currentLocale.value]?.[errorCode] || fallback || errorCode
  }

  const setLocale = (locale: Locale) => {
    currentLocale.value = locale
  }

  return { t, setLocale, currentLocale }
}

export function getErrorMessage(errorCode: string, locale: Locale = 'en'): string {
  return messages[locale]?.[errorCode] || messages['en']?.[errorCode] || errorCode
}
