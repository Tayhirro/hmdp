export function getApiErrorMessage(error: unknown): string | undefined {
  if (typeof error !== 'object' || error === null) return undefined
  const details = error as Record<string, unknown>
  if (typeof details.statusMessage === 'string') return details.statusMessage
  if (typeof details.message === 'string') return details.message
  return undefined
}
