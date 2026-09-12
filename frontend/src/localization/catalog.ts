export const CATALOG_SCHEMA_VERSION = 1 as const
export const CATALOG_VERSION = '2026.09.0' as const
export const SOURCE_LOCALE = 'en' as const
export const SUPPORTED_LOCALES = ['en', 'zh-CN'] as const
export const CATALOG_NAMESPACES = ['common', 'shell', 'agent', 'canvas', 'timeline', 'review', 'workflow', 'render', 'settings', 'errors'] as const

declare const productUILocaleBrand: unique symbol
declare const agentConversationLanguageBrand: unique symbol

export type SupportedLocale = typeof SUPPORTED_LOCALES[number]
export type ProductUILocale = SupportedLocale & { readonly [productUILocaleBrand]: true }
export type AgentConversationLanguage = string & { readonly [agentConversationLanguageBrand]: true }
export type CatalogNamespace = typeof CATALOG_NAMESPACES[number]
export type MessageParameters = Readonly<Record<string, string | number>>
export type MessageDefinition = { readonly message: string; readonly params: readonly string[] }
export type CatalogNamespaces = Partial<Record<CatalogNamespace, Readonly<Record<string, MessageDefinition>>>>

export interface LocalizationCatalog {
  readonly locale: SupportedLocale
  readonly catalogVersion: typeof CATALOG_VERSION
  readonly schemaVersion: typeof CATALOG_SCHEMA_VERSION
  readonly sourceLocale: typeof SOURCE_LOCALE
  readonly fallbackLocale: typeof SOURCE_LOCALE
  readonly namespaces: CatalogNamespaces
  readonly contentDigest: string
}

export type ValidationResult<T> = { readonly ok: true; readonly value: T } | { readonly ok: false; readonly error: string }

const catalogFields = ['locale', 'catalogVersion', 'schemaVersion', 'sourceLocale', 'fallbackLocale', 'namespaces', 'contentDigest']
const messageFields = ['message', 'params']
const identifierPattern = /^[a-z][a-zA-Z0-9]*(?:[._-][a-zA-Z0-9]+)*$/
const digestPattern = /^sha256:[a-f0-9]{64}$/
const htmlPattern = /<\/?[a-z][^>]*>/i
const placeholderPattern = /\{([a-z][a-zA-Z0-9]*)\}/g
const forbiddenPropertyNames = new Set(['__proto__', 'prototype', 'constructor'])

function isRecord(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

function hasExactFields(value: Record<string, unknown>, fields: readonly string[]): boolean {
  const keys = Object.keys(value).sort()
  return keys.length === fields.length && [...fields].sort().every((field, index) => keys[index] === field)
}

function hasOwn(value: object, key: PropertyKey): boolean {
  return Object.prototype.hasOwnProperty.call(value, key)
}

function hasUnpairedSurrogate(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(index + 1)
      if (!(next >= 0xdc00 && next <= 0xdfff)) return true
      index += 1
    } else if (code >= 0xdc00 && code <= 0xdfff) {
      return true
    }
  }
  return false
}

export function toProductUILocale(locale: SupportedLocale): ProductUILocale {
  return locale as ProductUILocale
}

export function isSupportedLocale(value: unknown): value is SupportedLocale {
  return typeof value === 'string' && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

/**
 * Canonical digest input is UTF-8 JSON with fixed top-level field order, namespace
 * and message keys sorted lexicographically, and each message represented as
 * `{message,params}` with sorted parameter names. contentDigest is excluded.
 */
export function canonicalCatalogSerialization(catalog: Omit<LocalizationCatalog, 'contentDigest'>): string {
  const namespaces: Record<string, Record<string, MessageDefinition>> = {}
  for (const namespace of Object.keys(catalog.namespaces).sort()) {
    const messages = catalog.namespaces[namespace as CatalogNamespace] ?? {}
    namespaces[namespace] = {}
    for (const key of Object.keys(messages).sort()) {
      const definition = messages[key]
      namespaces[namespace][key] = { message: definition.message, params: [...definition.params].sort() }
    }
  }
  return JSON.stringify({
    locale: catalog.locale,
    catalogVersion: catalog.catalogVersion,
    schemaVersion: catalog.schemaVersion,
    sourceLocale: catalog.sourceLocale,
    fallbackLocale: catalog.fallbackLocale,
    namespaces,
  })
}

// Compact synchronous SHA-256 keeps bundled fallback validation synchronous in browsers.
export function sha256(value: string): string {
  const rightRotate = (number: number, amount: number) => (number >>> amount) | (number << (32 - amount))
  const maxWord = 2 ** 32
  const words: number[] = []
  const bytes = new TextEncoder().encode(value)
  const bitLength = bytes.length * 8
  const hash: number[] = []
  const constants: number[] = []
  const composite: Record<number, boolean> = {}
  let candidate = 2
  while (constants.length < 64) {
    if (!composite[candidate]) {
      for (let multiple = candidate * candidate; multiple < 313; multiple += candidate) composite[multiple] = true
      if (hash.length < 8) hash.push((candidate ** 0.5 * maxWord) | 0)
      constants.push((candidate ** (1 / 3) * maxWord) | 0)
    }
    candidate += 1
  }
  const padded = [...bytes, 0x80]
  while ((padded.length % 64) !== 56) padded.push(0)
  for (let index = 0; index < padded.length; index += 1) words[index >> 2] |= padded[index] << ((3 - index) % 4) * 8
  words.push(Math.floor(bitLength / maxWord), bitLength)
  for (let offset = 0; offset < words.length; offset += 16) {
    const schedule = words.slice(offset, offset + 16)
    const previous = hash.slice(0, 8)
    const working = hash.slice(0, 8)
    for (let round = 0; round < 64; round += 1) {
      const w15 = schedule[round - 15]
      const w2 = schedule[round - 2]
      const word = round < 16 ? schedule[round] : (schedule[round - 16]
        + (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3))
        + schedule[round - 7]
        + (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))) | 0
      schedule[round] = word
      const e = working[4]
      const a = working[0]
      const temp1 = (working[7] + (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25))
        + ((e & working[5]) ^ (~e & working[6])) + constants[round] + word) | 0
      const temp2 = ((rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22))
        + ((a & working[1]) ^ (a & working[2]) ^ (working[1] & working[2]))) | 0
      working.pop()
      working.unshift((temp1 + temp2) | 0)
      working[4] = (working[4] + temp1) | 0
    }
    for (let index = 0; index < 8; index += 1) hash[index] = (working[index] + previous[index]) | 0
  }
  return hash.map(word => (word >>> 0).toString(16).padStart(8, '0')).join('')
}

export function createCatalog(locale: SupportedLocale, namespaces: CatalogNamespaces): LocalizationCatalog {
  const base = {
    locale,
    catalogVersion: CATALOG_VERSION,
    schemaVersion: CATALOG_SCHEMA_VERSION,
    sourceLocale: SOURCE_LOCALE,
    fallbackLocale: SOURCE_LOCALE,
    namespaces,
  } satisfies Omit<LocalizationCatalog, 'contentDigest'>
  return { ...base, contentDigest: `sha256:${sha256(canonicalCatalogSerialization(base))}` }
}

function validateMessage(namespace: string, key: string, value: unknown): string | null {
  if (!identifierPattern.test(key) || forbiddenPropertyNames.has(key)) return `${namespace}: malformed message key`
  if (!isRecord(value) || !hasExactFields(value, messageFields)) return `${namespace}.${key}: malformed message definition`
  if (typeof value.message !== 'string' || !value.message || hasUnpairedSurrogate(value.message) || [...value.message].some(character => character.charCodeAt(0) < 32 && ![9, 10, 13].includes(character.charCodeAt(0))) || htmlPattern.test(value.message)) return `${namespace}.${key}: message must be plain text`
  if (!Array.isArray(value.params) || !value.params.every(param => typeof param === 'string' && identifierPattern.test(param) && !forbiddenPropertyNames.has(param))) return `${namespace}.${key}: malformed params`
  const params = value.params as string[]
  if (new Set(params).size !== params.length) return `${namespace}.${key}: duplicate params`
  const placeholders = [...new Set([...value.message.matchAll(placeholderPattern)].map(match => match[1]))].sort()
  const contract = [...params].sort()
  if (placeholders.length !== contract.length || placeholders.some((name, index) => name !== contract[index])) return `${namespace}.${key}: params do not match placeholders`
  return null
}

function validateCatalogShape(input: unknown): ValidationResult<LocalizationCatalog> {
  if (!isRecord(input) || !hasExactFields(input, catalogFields)) return { ok: false, error: 'catalog fields are malformed or unknown' }
  if (input.schemaVersion !== CATALOG_SCHEMA_VERSION) return { ok: false, error: 'incompatible schemaVersion' }
  if (input.catalogVersion !== CATALOG_VERSION) return { ok: false, error: 'incompatible catalogVersion' }
  if (!isSupportedLocale(input.locale)) return { ok: false, error: 'unsupported locale' }
  if (input.sourceLocale !== SOURCE_LOCALE || input.fallbackLocale !== SOURCE_LOCALE) return { ok: false, error: 'invalid source or fallback locale' }
  if (!isRecord(input.namespaces)) return { ok: false, error: 'malformed namespaces' }
  for (const [namespace, messages] of Object.entries(input.namespaces)) {
    if (!(CATALOG_NAMESPACES as readonly string[]).includes(namespace) || !isRecord(messages)) return { ok: false, error: `${namespace}: malformed namespace` }
    for (const [key, value] of Object.entries(messages)) {
      const error = validateMessage(namespace, key, value)
      if (error) return { ok: false, error }
    }
  }
  if (typeof input.contentDigest !== 'string' || !digestPattern.test(input.contentDigest)) return { ok: false, error: 'malformed contentDigest' }
  const catalog = input as unknown as LocalizationCatalog
  const base: Omit<LocalizationCatalog, 'contentDigest'> = {
    locale: catalog.locale,
    catalogVersion: catalog.catalogVersion,
    schemaVersion: catalog.schemaVersion,
    sourceLocale: catalog.sourceLocale,
    fallbackLocale: catalog.fallbackLocale,
    namespaces: catalog.namespaces,
  }
  const expected = `sha256:${sha256(canonicalCatalogSerialization(base))}`
  if (catalog.contentDigest !== expected) return { ok: false, error: 'contentDigest mismatch' }
  return { ok: true, value: catalog }
}

export function validateCatalog(input: unknown): ValidationResult<LocalizationCatalog> {
  try {
    return validateCatalogShape(input)
  } catch {
    return { ok: false, error: 'catalog could not be safely inspected' }
  }
}

export function messageAt(catalog: LocalizationCatalog, fullKey: string): MessageDefinition | undefined {
  const separator = fullKey.indexOf('.')
  if (separator < 1) return undefined
  const namespace = fullKey.slice(0, separator) as CatalogNamespace
  const key = fullKey.slice(separator + 1)
  if (!(CATALOG_NAMESPACES as readonly string[]).includes(namespace) || forbiddenPropertyNames.has(key) || !hasOwn(catalog.namespaces, namespace)) return undefined
  const messages = catalog.namespaces[namespace]
  return messages && hasOwn(messages, key) ? messages[key] : undefined
}
