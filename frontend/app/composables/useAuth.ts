import type { LoginFormDTO, UserDTO } from '~/types/api'

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object'
}

function isUnauthorized(error: unknown): boolean {
  if (!isObject(error)) return false
  return error.statusCode === 401
}

export function useAuth() {
  const { $apiData } = useNuxtApp()

  const token = useCookie<string | null>('hmdp_token', { sameSite: 'lax' })
  // 全局认证状态必须跨页面、布局和菜单共享；普通 ref 会让每次调用 useAuth() 都得到不同用户对象。
  const user = useState<UserDTO | null>('auth_user', () => null)
  if (import.meta.client) {
    console.warn('[auth][client] composable initialized', {
      dev: import.meta.dev
    })
  }

  const isLoggedIn = computed(() => Boolean(token.value))

  async function fetchMe() {
    if (!token.value) {
      if (import.meta.client) {
        console.warn('[auth:fetchMe][client] skip because token is empty')
      }
      user.value = null
      return null
    }

    if (import.meta.client) {
      console.warn('[auth:fetchMe][client] start', {
        tokenPreview: `${token.value.slice(0, 8)}...`,
        tokenLength: token.value.length
      })
    }

    try {
      const me = await $apiData<UserDTO>('/user/me')
      user.value = me ?? null
      if (import.meta.client) {
        console.warn('[auth:fetchMe][client] success', { hasUser: Boolean(user.value) })
      }
      return user.value
    } catch (error) {
      if (isUnauthorized(error)) {
        if (import.meta.client) {
          console.warn('[auth:fetchMe][client] unauthorized, token will be cleared')
        }
        token.value = null
        user.value = null
        return null
      }
      throw error
    }
  }

  async function sendCode(phone: string) {
    await $apiData<unknown>(`/user/code?phone=${encodeURIComponent(phone)}`, { method: 'POST' })
  }

  async function login(form: LoginFormDTO) {
    const newToken = await $apiData<string>('/user/login', {
      method: 'POST',
      body: form
    })

    if (import.meta.client) {
      console.warn('[auth:login][client] login response token', {
        hasToken: Boolean(newToken),
        tokenLength: newToken?.length ?? 0,
        tokenPreview: typeof newToken === 'string' ? `${newToken.slice(0, 8)}...` : ''
      })
    }

    token.value = newToken ?? null
    await nextTick()

    if (import.meta.client) {
      console.warn('[auth:login][client] token saved to cookie ref', {
        hasToken: Boolean(token.value),
        tokenLength: token.value?.length ?? 0
      })
    }

    await fetchMe().catch(() => null)
    return token.value
  }

  async function signup(form: LoginFormDTO) {
    const response = await $apiData<unknown>('/user/signup', {
      method: 'POST',
      body: form
    })

    // 如果返回的是 token（string），说明是完整注册（手机号注册或带手机号的账号注册）
    if (typeof response === 'string') {
      token.value = response
      await nextTick()
      await fetchMe().catch(() => null)
      return { success: true, token: response, requiresPhoneBinding: false }
    }

    // 第一阶段账号注册：后端返回 object（避免和 token 字符串冲突）
    if (isObject(response) && response.requiresPhoneBinding === true) {
      return { success: true, token: null, requiresPhoneBinding: true }
    }

    // 其他情况
    return { success: false, token: null, requiresPhoneBinding: false }
  }

  async function logout() {
    try {
      await $apiData<unknown>('/user/logout', { method: 'POST' })
    } finally {
      token.value = null
      user.value = null
    }
  }

  return {
    token,
    user,
    isLoggedIn,
    fetchMe,
    sendCode,
    login,
    signup,
    logout
  }
}
