<script setup lang="ts">
import { z } from 'zod'
import type { LoginFormDTO } from '~/types/api'

definePageMeta({ 
  layout: 'auth'
  // 移除了 middleware 检查，因为在第一步注册后用户已经有 token
})

const auth = useAuth()
const toast = useToast()
const router = useRouter()
const { $apiData } = useNuxtApp()

// 临时存储的用户信息（从注册第一步传递过来）
const tempUserInfo = useState<{ username: string; password: string } | null>('temp_user_info', () => null)

// 如果没有临时用户信息，重定向到注册页面
if (!tempUserInfo.value) {
  await navigateTo('/login?mode=signup')
}

const bindPhoneSchema = z.object({
  phone: z.string().regex(/^1\d{10}$/, '请输入正确的手机号'),
  code: z.string().min(4, '请输入验证码')
})

type BindPhoneSchema = z.output<typeof bindPhoneSchema>

const bindPhoneState = reactive<Partial<BindPhoneSchema>>({
  phone: '',
  code: ''
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

// 完成手机号绑定
async function bindPhone() {
  if (!tempUserInfo.value) {
    toast.add({ title: '用户信息丢失，请重新注册', color: 'error', icon: 'i-lucide-x-circle' })
    await navigateTo('/login?mode=signup')
    return
  }

  sending.value = true
  try {
    // 调用专门的绑定手机号接口
    const form: LoginFormDTO = {
      account: tempUserInfo.value.username,
      password: tempUserInfo.value.password,
      phone: bindPhoneState.phone,
      code: bindPhoneState.code
    }
    
    const response = await $apiData<string>('/user/bind-phone', {
      method: 'POST',
      body: form
    })
    
    if (response) {
      // 绑定成功，设置 token
      const auth = useAuth()
      auth.token.value = response
      await auth.fetchMe().catch(() => null)
      
      toast.add({ title: '注册成功！手机号绑定完成', color: 'success', icon: 'i-lucide-check-circle' })
      
      // 清除临时用户信息
      tempUserInfo.value = null
      
      // 跳转到首页或其他目标页面
      await navigateTo('/')
    } else {
      throw new Error('绑定失败')
    }
  } catch (error) {
    toast.add({
      title: '绑定失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  } finally {
    sending.value = false
  }
}

// 跳过绑定（可选功能）
async function skipBinding() {
  if (!tempUserInfo.value) return
  
  try {
    // 只注册账号密码，不绑定手机号
    const form: LoginFormDTO = {
      account: tempUserInfo.value.username,
      password: tempUserInfo.value.password
    }
    
    await auth.signup(form)
    toast.add({ title: '注册成功！您可以在个人中心 later 绑定手机号', color: 'success', icon: 'i-lucide-check-circle' })
    
    // 清除临时用户信息
    tempUserInfo.value = null
    
    await navigateTo('/')
  } catch (error) {
    toast.add({
      title: '注册失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  }
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
            绑定手机号
          </h1>
          <p class="text-sm text-muted">
            为了账户安全，请绑定您的手机号
          </p>
        </div>

        <!-- 手机号绑定表单 -->
        <UForm
          :schema="bindPhoneSchema"
          :state="bindPhoneState"
          class="space-y-4"
          @submit="bindPhone()"
        >
          <UFormField name="phone" label="手机号" required>
            <UInput 
              v-model="bindPhoneState.phone" 
              placeholder="请输入手机号" 
              inputmode="numeric" 
              class="w-full"
            />
          </UFormField>

          <UFormField name="code" label="验证码" required>
            <div class="flex gap-2">
              <UInput 
                v-model="bindPhoneState.code" 
                placeholder="请输入验证码" 
                inputmode="numeric" 
                class="flex-1"
              />
              <UButton
                color="neutral"
                variant="outline"
                :loading="sending"
                :disabled="countdown > 0"
                @click="sendCode(bindPhoneState.phone || '')"
              >
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </UButton>
            </div>
          </UFormField>

          <div class="flex gap-3">
            <UButton type="submit" color="primary" block :loading="sending" class="flex-1">
              完成绑定
            </UButton>
            <UButton 
              color="neutral" 
              variant="outline" 
              block 
              @click="skipBinding()"
              class="flex-1"
            >
              暂不绑定
            </UButton>
          </div>
        </UForm>

        <USeparator />

        <div class="text-center text-sm text-muted">
          <p>绑定手机号后可以使用手机号直接登录</p>
          <p>也可以在个人中心随时绑定或更换手机号</p>
        </div>
      </div>
    </UCard>
  </div>
</template>
