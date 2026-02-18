export function firstCsvItem(value?: string | null): string | undefined {
  if (!value) return undefined
  const [first] = value.split(',')
  return first?.trim() || undefined
}

export function resolveImgUrl(path?: string | null): string | undefined {
  if (!path) return undefined
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  if (path.startsWith('/imgs/')) return path
  if (path.startsWith('/types/') || path.startsWith('/icons/') || path.startsWith('/blogs/')) return `/imgs${path}`
  return path
}

export function formatFen(value?: number | null): string {
  if (value === null || value === undefined) return ''

  const n = Math.trunc(value)
  if (n === 0) return '0.00'

  const s = String(Math.abs(n))
  const sign = n < 0 ? '-' : ''

  if (s.length === 1) return `${sign}0.0${s}`
  if (s.length === 2) return `${sign}0.${s}`
  return `${sign}${s.slice(0, -2)}.${s.slice(-2)}`
}

