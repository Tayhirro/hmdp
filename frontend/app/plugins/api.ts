import type { ApiResult } from '~/types/api'

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object'
}

function isAppError(error: unknown): boolean {
  if (!isObject(error)) return false
  return typeof error.statusCode === 'number' || typeof error.statusMessage === 'string'
}

function getErrorMessage(error: unknown): string {
  if (isObject(error) && typeof error.message === 'string') return error.message
  if (typeof error === 'string') return error
  return '请求失败'
}

export default defineNuxtPlugin(() => {
  const runtimeConfig = useRuntimeConfig()
  if (import.meta.client) {
    console.warn('[api][client] plugin initialized', {
      dev: import.meta.dev
    })
  }

  const apiRaw = $fetch.create({
    baseURL: runtimeConfig.public.apiBase,
    credentials: 'include',
    retry: 0,
    onRequest({ request, options }) {
      const tokenCookie = useCookie<string | null>('hmdp_token', { sameSite: 'lax' })
      const currentToken = tokenCookie.value

      if (!currentToken) {
        if (import.meta.client) {
          console.warn('[api:onRequest][client] no token', {
            request: String(request)
          })
        }
        return
      }

      const headers = new Headers(options.headers)
      headers.set('authorization', currentToken)
      options.headers = headers

      if (import.meta.client) {
        console.warn('[api:onRequest][client] authorization attached', {
          request: String(request),
          tokenPreview: `${currentToken.slice(0, 8)}...`,
          tokenLength: currentToken.length
        })
      }
    },
    onResponse({ request, response }) {
      const result = response._data as ApiResult<unknown> | undefined
      if (import.meta.client) {
        console.warn('[api:onResponse][client]', {
          request: String(request),
          status: response.status,
          success: result?.success
        })
      }
      if (!result) return

      if (result.success === false) {
        throw createError({
          statusCode: response.status || 400,
          statusMessage: result.errorMsg || '请求失败'
        })
      }
    },
    onResponseError({ request, response, error }) {
      const tokenCookie = useCookie<string | null>('hmdp_token', { sameSite: 'lax' })
      if (import.meta.client) {
        console.warn('[api:onResponseError][client]', {
          request: String(request),
          status: response?.status,
          hasTokenBeforeClear: Boolean(tokenCookie.value)
        })
      }
      if (response?.status === 401) {
        tokenCookie.value = null
        if (import.meta.client) {
          console.warn('[api:onResponseError][client] token cleared due to 401', {
            request: String(request)
          })
        }
      }

      if (isAppError(error)) {
        throw error
      }

      throw createError({
        statusCode: response?.status || 500,
        statusMessage: getErrorMessage(error)
      })
    }
  })

  async function apiData<T>(request: Parameters<typeof apiRaw>[0], options?: Parameters<typeof apiRaw>[1]) {
    const result = await apiRaw<ApiResult<T>>(request, options)
    return result.data
  }

  return {
    provide: {
      apiRaw,
      apiData
    }
  }
})
