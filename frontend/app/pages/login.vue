<script setup lang="ts">
import { z } from 'zod'
import type { LoginFormDTO } from '~/types/api'

definePageMeta({ layout: 'auth' })

const auth = useAuth()
const toast = useToast()
const route = useRoute()

function normalizeRedirect(target: unknown): string {
  if (typeof target !== 'string') return '/'
  const value = target.trim()
  if (!value.startsWith('/')) return '/'
  // Never navigate to Nuxt internal assets path.
  if (value.startsWith('/_nuxt')) return '/'
  return value
}

const redirectTo = computed(() => {
  return normalizeRedirect(route.query.redirect)
})

watchEffect(async () => {
  if (auth.isLoggedIn.value) {
    await navigateTo(redirectTo.value)
  }
})

// 界面模式：login(登录) 或 signup(注册)
const currentMode = ref<'login' | 'signup'>('login')
// 登录方式：code(验证码) 或 password(密码)
const loginMethod = ref<'code' | 'password'>('code')
// 注册方式：phone(手机号) 或 account(账号)
const signupMethod = ref<'phone' | 'account'>('phone')

const agreed = ref(false)

// 登录表单 - 验证码模式
const codeLoginSchema = z.object({
  phone: z.string().regex(/^1\d{10}$/, '请输入正确的手机号'),
  code: z.string().min(4, '请输入验证码')
})

type CodeLoginSchema = z.output<typeof codeLoginSchema>

const codeLoginState = reactive<Partial<CodeLoginSchema>>({
  phone: '',
  code: ''
})

// 登录表单 - 密码模式
const passwordLoginSchema = z.object({
  account: z.string().min(1, '请输入账号/手机号'),
  password: z.string().min(1, '请输入密码')
})

type PasswordLoginSchema = z.output<typeof passwordLoginSchema>

const passwordLoginState = reactive<Partial<PasswordLoginSchema>>({
  account: '',
  password: ''
})

// 注册表单 - 手机号模式
const phoneSignupSchema = z.object({
  phone: z.string().regex(/^1\d{10}$/, '请输入正确的手机号'),
  code: z.string().min(4, '请输入验证码')
})

type PhoneSignupSchema = z.output<typeof phoneSignupSchema>

const phoneSignupState = reactive<Partial<PhoneSignupSchema>>({
  phone: '',
  code: ''
})

// 注册表单 - 账号模式
const accountSignupSchema = z.object({
  username: z.string().min(3, '用户名至少 3 位'),
  password: z.string().min(6, '密码至少 6 位'),
  confirmPassword: z.string().min(6, '请确认密码')
}).refine(data => data.password === data.confirmPassword, {
  message: '两次输入的密码不一致',
  path: ['confirmPassword']
})

type AccountSignupSchema = z.output<typeof accountSignupSchema>

const accountSignupState = reactive<AccountSignupSchema>({
  username: '',
  password: '',
  confirmPassword: ''
})

const sending = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

function startCountdown(seconds = 60) {
  countdown.value = seconds
  timer = setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1)
    if (countdown.value === 0 && timer) {
      clearInterval(timer)
      timer = undefined
    }
  }, 1000)
}

onUnmounted(() => {
  if (!timer) return
  clearInterval(timer)
})

// 发送验证码
async function sendCode(phone: string) {
  if (!phone) {
    toast.add({ title: '请输入手机号', color: 'warning', icon: 'i-lucide-alert-triangle' })
    return
  }
  if (countdown.value > 0) return

  sending.value = true
  try {
    await auth.sendCode(phone)
    toast.add({ title: '验证码已发送', color: 'success', icon: 'i-lucide-check-circle' })
    startCountdown(60)
  } catch (error) {
    toast.add({
      title: '发送失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    sending.value = false
  }
}

// 执行登录
async function doLogin() {
  if (!agreed.value) {
    toast.add({ title: '请先同意用户协议', color: 'warning', icon: 'i-lucide-shield-alert' })
    return
  }

  sending.value = true
  try {
    let form: LoginFormDTO
    
    if (loginMethod.value === 'code') {
      // 验证码登录
      form = {
        phone: codeLoginState.phone,
        code: codeLoginState.code
      }
    } else {
      // 密码登录
      form = {
        account: passwordLoginState.account,
        phone: passwordLoginState.account,
        password: passwordLoginState.password
      }
    }
    
    await auth.login(form)
    toast.add({ title: '登录成功', color: 'success', icon: 'i-lucide-check-circle' })
    await navigateTo(redirectTo.value)
  } catch (error) {
    toast.add({
      title: '登录失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    sending.value = false
  }
}

// 执行注册
async function doSignup() {
  if (!agreed.value) {
    toast.add({ title: '请先同意用户协议', color: 'warning', icon: 'i-lucide-shield-alert' })
    return
  }

  sending.value = true
  try {
    if (signupMethod.value === 'phone') {
      // 手机号注册 - 保持原有逻辑
      const form: LoginFormDTO = {
        phone: phoneSignupState.phone,
        code: phoneSignupState.code
      }
      
      const result = await auth.signup(form)
      if (result.success && result.token) {
        toast.add({ title: '注册成功', color: 'success', icon: 'i-lucide-check-circle' })
        await navigateTo(redirectTo.value)
      } else {
        throw new Error('注册失败')
      }
    } else {
      // 账号注册 - 两阶段流程
      // 第一阶段：只提交账号密码
      const form: LoginFormDTO = {
        account: accountSignupState.username,
        password: accountSignupState.password
      }
      
      const result = await auth.signup(form)
      if (result.success) {
        if (result.requiresPhoneBinding) {
          // 第一阶段成功，跳转到手机号绑定页面
          // 临时存储用户信息
          const tempUserInfo = useState<{ username: string; password: string } | null>('temp_user_info', () => null)
          tempUserInfo.value = {
            username: accountSignupState.username,
            password: accountSignupState.password
          }
          
          toast.add({ title: '账号注册成功', color: 'success', icon: 'i-lucide-check-circle' })
          await navigateTo('/bind-phone')
        } else {
          // 完整注册成功（带手机号的账号注册）
          toast.add({ title: '注册成功', color: 'success', icon: 'i-lucide-check-circle' })
          await navigateTo(redirectTo.value)
        }
      } else {
        throw new Error('注册失败')
      }
    }
  } catch (error) {
    toast.add({
      title: '注册失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    sending.value = false
  }
}

// 切换到登录模式
function switchToLogin() {
  currentMode.value = 'login'
  // 重置表单状态
  Object.assign(codeLoginState, { phone: '', code: '' })
  Object.assign(passwordLoginState, { account: '', password: '' })
}

// 切换到注册模式
function switchToSignup() {
  currentMode.value = 'signup'
  // 重置表单状态
  Object.assign(phoneSignupState, { phone: '', code: '' })
  Object.assign(accountSignupState, { username: '', password: '', confirmPassword: '' })
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-center">
      <AppLogo />
    </div>

    <UCard>
      <div class="space-y-4">
        <!-- 标题区域 -->
        <div class="space-y-1 text-center">
          <h1 class="text-xl font-semibold text-highlighted">
            {{ currentMode === 'login' ? '欢迎回来' : '创建账号' }}
          </h1>
          <p class="text-sm text-muted">
            {{ currentMode === 'login' 
              ? '使用手机号验证码或账号密码登录' 
              : '选择注册方式创建您的账号' }}
          </p>
        </div>

        <!-- 登录表单 -->
        <template v-if="currentMode === 'login'">
          <!-- 登录方式切换 -->
          <div class="flex gap-2 mb-4">
            <UButton
              :color="loginMethod === 'code' ? 'primary' : 'neutral'"
              :variant="loginMethod === 'code' ? 'subtle' : 'ghost'"
              icon="i-lucide-message-square"
              size="sm"
              @click="loginMethod = 'code'"
            >
              验证码登录
            </UButton>
            <UButton
              :color="loginMethod === 'password' ? 'primary' : 'neutral'"
              :variant="loginMethod === 'password' ? 'subtle' : 'ghost'"
              icon="i-lucide-lock"
              size="sm"
              @click="loginMethod = 'password'"
            >
              密码登录
            </UButton>
          </div>

          <!-- 验证码登录 -->
          <UForm
            v-if="loginMethod === 'code'"
            :schema="codeLoginSchema"
            :state="codeLoginState"
            class="space-y-4"
            @submit="doLogin()"
          >
            <UFormField name="phone" label="手机号" required>
              <UInput 
                v-model="codeLoginState.phone" 
                placeholder="请输入手机号" 
                inputmode="numeric" 
                class="w-full"
              />
            </UFormField>

            <UFormField name="code" label="验证码" required>
              <div class="flex gap-2">
                <UInput 
                  v-model="codeLoginState.code" 
                  placeholder="请输入验证码" 
                  inputmode="numeric" 
                  class="flex-1"
                />
                <UButton
                  color="neutral"
                  variant="outline"
                  :loading="sending"
                  :disabled="countdown > 0"
                  @click="sendCode(codeLoginState.phone || '')"
                >
                  {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
                </UButton>
              </div>
            </UFormField>

            <UButton type="submit" color="primary" block :loading="sending">
              登录
            </UButton>
          </UForm>

          <!-- 密码登录 -->
          <UForm
            v-else
            :schema="passwordLoginSchema"
            :state="passwordLoginState"
            class="space-y-4"
            @submit="doLogin()"
          >
            <UFormField name="account" label="账号 / 手机号" required>
              <UInput 
                v-model="passwordLoginState.account" 
                placeholder="请输入账号或手机号" 
                class="w-full"
              />
            </UFormField>

            <UFormField name="password" label="密码" required>
              <UInput 
                v-model="passwordLoginState.password" 
                type="password" 
                placeholder="请输入密码" 
                class="w-full"
              />
            </UFormField>

            <UButton type="submit" color="primary" block :loading="sending">
              登录
            </UButton>
          </UForm>
        </template>

        <!-- 注册表单 -->
        <template v-else>
          <!-- 注册方式切换 -->
          <div class="flex gap-2 mb-4">
            <UButton
              :color="signupMethod === 'phone' ? 'primary' : 'neutral'"
              :variant="signupMethod === 'phone' ? 'subtle' : 'ghost'"
              icon="i-lucide-phone"
              size="sm"
              @click="signupMethod = 'phone'"
            >
              手机号注册
            </UButton>
            <UButton
              :color="signupMethod === 'account' ? 'primary' : 'neutral'"
              :variant="signupMethod === 'account' ? 'subtle' : 'ghost'"
              icon="i-lucide-user"
              size="sm"
              @click="signupMethod = 'account'"
            >
              账号注册
            </UButton>
          </div>

          <!-- 手机号注册 -->
          <UForm
            v-if="signupMethod === 'phone'"
            :schema="phoneSignupSchema"
            :state="phoneSignupState"
            class="space-y-4"
            @submit="doSignup()"
          >
            <UFormField name="phone" label="手机号" required>
              <UInput 
                v-model="phoneSignupState.phone" 
                placeholder="请输入手机号" 
                inputmode="numeric" 
                class="w-full"
              />
            </UFormField>

            <UFormField name="code" label="验证码" required>
              <div class="flex gap-2">
                <UInput 
                  v-model="phoneSignupState.code" 
                  placeholder="请输入验证码" 
                  inputmode="numeric" 
                  class="flex-1"
                />
                <UButton
                  color="neutral"
                  variant="outline"
                  :loading="sending"
                  :disabled="countdown > 0"
                  @click="sendCode(phoneSignupState.phone || '')"
                >
                  {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
                </UButton>
              </div>
            </UFormField>

            <UButton type="submit" color="primary" block :loading="sending">
              立即注册
            </UButton>
          </UForm>

          <!-- 账号注册 -->
          <UForm
            v-else
            :schema="accountSignupSchema"
            :state="accountSignupState"
            class="space-y-4"
            @submit="doSignup()"
          >
            <UFormField name="username" label="用户名" required>
              <UInput 
                v-model="accountSignupState.username" 
                placeholder="请输入用户名" 
                class="w-full"
              />
            </UFormField>

            <UFormField name="password" label="密码" required>
              <UInput 
                v-model="accountSignupState.password" 
                type="password" 
                placeholder="设置登录密码" 
                class="w-full"
              />
            </UFormField>

            <UFormField name="confirmPassword" label="确认密码" required>
              <UInput 
                v-model="accountSignupState.confirmPassword" 
                type="password" 
                placeholder="请再次输入密码" 
                class="w-full"
              />
            </UFormField>

            <UButton type="submit" color="primary" block :loading="sending">
              立即注册
            </UButton>
          </UForm>
        </template>

        <USeparator />

        <div class="flex items-center justify-between">
          <UCheckbox v-model="agreed" label="我已阅读并同意《用户协议》和《隐私政策》" />
          <UButton
            v-if="currentMode === 'login'"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="switchToSignup"
          >
            没有账号？立即注册
          </UButton>
          <UButton
            v-else
            color="neutral"
            variant="ghost"
            size="sm"
            @click="switchToLogin"
          >
            已有账号？立即登录
          </UButton>
        </div>
      </div>
    </UCard>
  </div>
</template>
