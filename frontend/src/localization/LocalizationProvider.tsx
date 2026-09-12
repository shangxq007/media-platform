import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  CATALOG_VERSION,
  messageAt,
  toProductUILocale,
  validateCatalog,
  type LocalizationCatalog,
  type MessageParameters,
  type ProductUILocale,
  type SupportedLocale,
} from './catalog'
import { defaultBundledCatalogProvider, validatePlatformCatalogContract } from './catalogs'

export interface RemoteCatalogProvider {
  loadCatalog(locale: SupportedLocale, signal: AbortSignal): Promise<unknown>
}

export class MemoryCatalogCache {
  private readonly catalogs = new Map<string, LocalizationCatalog>()

  get(locale: SupportedLocale, catalogVersion = CATALOG_VERSION): LocalizationCatalog | undefined {
    return this.catalogs.get(`${catalogVersion}:${locale}`)
  }

  set(catalog: LocalizationCatalog): void {
    this.catalogs.set(`${catalog.catalogVersion}:${catalog.locale}`, catalog)
  }
}

export interface TranslationAPI {
  readonly locale: ProductUILocale
  readonly setLocale: (locale: SupportedLocale) => void
  readonly t: (key: string, parameters?: MessageParameters) => string
  readonly formatDate: (value: Date | number, options?: Intl.DateTimeFormatOptions) => string
  readonly formatNumber: (value: number, options?: Intl.NumberFormatOptions) => string
  readonly formatUnit: (value: number, unit: Intl.NumberFormatOptions['unit'], options?: Omit<Intl.NumberFormatOptions, 'style' | 'unit'>) => string
  readonly formatDuration: (totalSeconds: number) => string
}

function translate(locale: SupportedLocale, catalog: LocalizationCatalog, key: string, parameters: MessageParameters = {}, environment: 'development' | 'production'): string {
  const definition = messageAt(catalog, key)
    ?? messageAt(defaultBundledCatalogProvider.getCatalog(locale), key)
    ?? messageAt(defaultBundledCatalogProvider.getSourceCatalog(), key)
  if (!definition) return environment === 'development' ? `⟦missing:${key}⟧` : key
  const missing = definition.params.filter(parameter => !Object.prototype.hasOwnProperty.call(parameters, parameter) || parameters[parameter] === undefined)
  if (missing.length) return environment === 'development' ? `⟦params:${key}:${missing.join(',')}⟧` : definition.message
  return definition.message.replace(/\{([a-z][a-zA-Z0-9]*)\}/g, (_placeholder, parameter: string) => String(parameters[parameter]))
}

function createAPI(locale: SupportedLocale, catalog: LocalizationCatalog, setLocale: (locale: SupportedLocale) => void, environment: 'development' | 'production'): TranslationAPI {
  const intlLocale = locale
  return {
    locale: toProductUILocale(locale),
    setLocale,
    t: (key, parameters) => translate(locale, catalog, key, parameters, environment),
    formatDate: (value, options) => new Intl.DateTimeFormat(intlLocale, options).format(value),
    formatNumber: (value, options) => new Intl.NumberFormat(intlLocale, options).format(value),
    formatUnit: (value, unit, options) => new Intl.NumberFormat(intlLocale, { ...options, style: 'unit', unit }).format(value),
    formatDuration: (totalSeconds) => {
      const safeSeconds = Math.max(0, Math.floor(totalSeconds))
      const minutes = Math.floor(safeSeconds / 60)
      const seconds = safeSeconds % 60
      const parts = []
      if (minutes) parts.push(new Intl.NumberFormat(intlLocale, { style: 'unit', unit: 'minute', unitDisplay: 'short' }).format(minutes))
      if (seconds || !parts.length) parts.push(new Intl.NumberFormat(intlLocale, { style: 'unit', unit: 'second', unitDisplay: 'short' }).format(seconds))
      return parts.join(' ')
    },
  }
}

const fallbackAPI = createAPI('en', defaultBundledCatalogProvider.getSourceCatalog(), () => undefined, 'development')
const LocalizationContext = createContext<TranslationAPI>(fallbackAPI)

export function LocalizationProvider({
  children,
  initialLocale = 'en',
  remoteProvider,
  cache: suppliedCache,
  environment = import.meta.env.DEV ? 'development' : 'production',
}: {
  children: ReactNode
  initialLocale?: SupportedLocale
  remoteProvider?: RemoteCatalogProvider
  cache?: MemoryCatalogCache
  environment?: 'development' | 'production'
}) {
  const [ownedCache] = useState(() => new MemoryCatalogCache())
  const cache = suppliedCache ?? ownedCache
  const [locale, setLocaleState] = useState<SupportedLocale>(initialLocale)
  const [catalog, setCatalog] = useState<LocalizationCatalog>(() => cache.get(initialLocale) ?? defaultBundledCatalogProvider.getCatalog(initialLocale))
  const requestGeneration = useRef(0)
  const remoteProviderRef = useRef(remoteProvider)
  remoteProviderRef.current = remoteProvider
  const remoteEnabled = remoteProvider !== undefined

  const setLocale = useCallback((nextLocale: SupportedLocale) => {
    requestGeneration.current += 1
    setLocaleState(nextLocale)
    setCatalog(cache.get(nextLocale) ?? defaultBundledCatalogProvider.getCatalog(nextLocale))
  }, [cache])

  useEffect(() => {
    document.documentElement.lang = locale
    const cached = cache.get(locale)
    if (cached) {
      setCatalog(cached)
      return
    }
    const activeRemoteProvider = remoteProviderRef.current
    if (!activeRemoteProvider) return
    const generation = ++requestGeneration.current
    const controller = new AbortController()
    void activeRemoteProvider.loadCatalog(locale, controller.signal).then(input => {
      const validation = validateCatalog(input)
      if (!validation.ok) return
      const platformValidation = validatePlatformCatalogContract(validation.value)
      if (!platformValidation.ok || platformValidation.value.locale !== locale || generation !== requestGeneration.current || controller.signal.aborted) return
      cache.set(platformValidation.value)
      setCatalog(platformValidation.value)
    }).catch(() => {
      // Remote delivery is availability-optional; bundled synchronous copy remains active.
    })
    return () => controller.abort()
  }, [cache, locale, remoteEnabled])

  const api = useMemo(() => createAPI(locale, catalog, setLocale, environment), [catalog, environment, locale, setLocale])
  return <LocalizationContext.Provider value={api}>{children}</LocalizationContext.Provider>
}

export function useTranslation(): TranslationAPI {
  return useContext(LocalizationContext)
}
