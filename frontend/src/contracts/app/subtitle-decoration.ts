/** Wire identity syntax only. The authenticated backend catalog owns availability and artwork. */
export function subtitleDecorationId(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 64
    && /^(?:[a-z0-9]+-)+v[1-9][0-9]*$(?![\s\S])/.test(value)
}
