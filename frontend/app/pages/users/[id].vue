<script setup lang="ts">
import type { BlogCard, CursorPage, UserDTO, UserInfo } from '~/types/api'
import { getApiErrorMessage } from '~/utils/api-error'
import { resolveImgUrl } from '~/utils/format'

definePageMeta({ middleware: 'auth' })

const route = useRoute()
const auth = useAuth()
const { $apiData } = useNuxtApp()
const toast = useToast()
const id = computed(() => Number(route.params.id))

await auth.fetchMe().catch(() => null)
const isMe = computed(() => auth.user.value?.id === id.value)

const { data: user } = await useAsyncData(
  () => `public-user-${id.value}`,
  () => $apiData<UserDTO>(`/user/${id.value}`),
  { watch: [id] }
)
const { data: info } = await useAsyncData(
  () => `public-user-info-${id.value}`,
  () => $apiData<UserInfo | null>(`/user/info/${id.value}`),
  { watch: [id] }
)

const followed = ref(false)
const changingFollow = ref(false)
const follows = ref<UserDTO[]>([])
const commons = ref<UserDTO[]>([])

async function loadSocialState() {
  if (!user.value) return
  if (!isMe.value) {
    followed.value = Boolean(await $apiData<boolean>(`/follow/or/not/${id.value}`))
    commons.value = await $apiData<UserDTO[]>(`/follow/common/${id.value}`) || []
  } else {
    commons.value = []
  }
  follows.value = await $apiData<UserDTO[]>(`/follow/list/${id.value}`) || []
}

async function toggleFollow() {
  if (isMe.value || changingFollow.value) return
  changingFollow.value = true
  try {
    const target = !followed.value
    await $apiData<unknown>(`/follow/${id.value}/${target}`, { method: 'PUT' })
    followed.value = target
    await loadSocialState()
    toast.add({ title: target ? '已关注' : '已取消关注', color: 'success' })
  } catch (error) {
    toast.add({
      title: '关注操作失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    changingFollow.value = false
  }
}

const blogs = ref<BlogCard[]>([])
const blogCursor = ref<string>()
const blogsDone = ref(false)
const loadingBlogs = ref(false)

async function loadBlogs(reset = false) {
  if (loadingBlogs.value) return
  if (reset) {
    blogs.value = []
    blogCursor.value = undefined
    blogsDone.value = false
  }
  loadingBlogs.value = true
  try {
    const params = new URLSearchParams({ id: String(id.value), limit: '12' })
    if (blogCursor.value) params.set('cursor', blogCursor.value)
    const page = await $apiData<CursorPage<BlogCard>>(`/blog/of/user?${params}`)
    blogs.value.push(...(page?.list ?? []))
    blogCursor.value = page?.nextCursor
    blogsDone.value = !page?.hasMore
  } finally {
    loadingBlogs.value = false
  }
}

await Promise.all([loadSocialState(), loadBlogs(true)])
</script>

<template>
  <div class="space-y-6">
    <UCard v-if="user">
      <div class="flex flex-col justify-between gap-5 sm:flex-row sm:items-start">
        <div class="flex gap-4">
          <UAvatar
            :src="resolveImgUrl(user.icon) || '/imgs/icons/default-icon.png'"
            size="xl"
          />
          <div>
            <h1 class="text-2xl font-semibold text-highlighted">
              {{ user.nickName }}
            </h1>
            <p class="mt-1 text-sm text-muted">
              {{ info?.introduce || '这个人还没有填写个人介绍' }}
            </p>
            <div class="mt-3 flex flex-wrap gap-2 text-sm text-muted">
              <UBadge
                color="neutral"
                variant="subtle"
              >
                {{ info?.city || '城市未设置' }}
              </UBadge>
              <span>关注 {{ follows.length }}</span>
              <span>粉丝 {{ info?.fans ?? 0 }}</span>
              <span>等级 {{ info?.level ?? 0 }}</span>
            </div>
          </div>
        </div>
        <div>
          <UButton
            v-if="isMe"
            to="/me"
            color="neutral"
            variant="outline"
            icon="i-lucide-pencil"
          >
            编辑资料
          </UButton>
          <UButton
            v-else
            :loading="changingFollow"
            :variant="followed ? 'outline' : 'solid'"
            @click="toggleFollow"
          >
            {{ followed ? '已关注' : '关注' }}
          </UButton>
        </div>
      </div>
    </UCard>

    <div class="grid gap-4 lg:grid-cols-2">
      <UCard>
        <template #header>
          <div class="font-semibold text-highlighted">
            关注的人
          </div>
        </template>
        <div
          v-if="follows.length"
          class="flex flex-wrap gap-2"
        >
          <NuxtLink
            v-for="item in follows"
            :key="item.id"
            :to="`/users/${item.id}`"
            class="flex items-center gap-2 rounded-full border border-default px-3 py-2"
          >
            <UAvatar
              :src="resolveImgUrl(item.icon) || '/imgs/icons/default-icon.png'"
              size="xs"
            />
            <span class="text-sm">{{ item.nickName }}</span>
          </NuxtLink>
        </div>
        <p
          v-else
          class="text-sm text-muted"
        >
          暂时没有关注任何人
        </p>
      </UCard>

      <UCard v-if="!isMe">
        <template #header>
          <div class="font-semibold text-highlighted">
            共同关注
          </div>
        </template>
        <div
          v-if="commons.length"
          class="flex flex-wrap gap-2"
        >
          <NuxtLink
            v-for="item in commons"
            :key="item.id"
            :to="`/users/${item.id}`"
            class="flex items-center gap-2 rounded-full border border-default px-3 py-2"
          >
            <UAvatar
              :src="resolveImgUrl(item.icon) || '/imgs/icons/default-icon.png'"
              size="xs"
            />
            <span class="text-sm">{{ item.nickName }}</span>
          </NuxtLink>
        </div>
        <p
          v-else
          class="text-sm text-muted"
        >
          暂时没有共同关注
        </p>
      </UCard>
    </div>

    <section class="space-y-4">
      <div class="flex items-center justify-between">
        <h2 class="text-xl font-semibold text-highlighted">
          {{ isMe ? '我的笔记' : 'TA 的笔记' }}
        </h2>
        <UButton
          v-if="isMe"
          to="/blogs/new"
          icon="i-lucide-square-pen"
        >
          发布笔记
        </UButton>
      </div>
      <div
        v-if="blogs.length"
        class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
      >
        <BlogCardItem
          v-for="blog in blogs"
          :key="blog.id"
          :blog="blog"
        />
      </div>
      <p
        v-else
        class="text-sm text-muted"
      >
        还没有发布笔记
      </p>
      <div class="flex justify-center">
        <UButton
          v-if="!blogsDone"
          variant="outline"
          :loading="loadingBlogs"
          @click="loadBlogs(false)"
        >
          加载更多
        </UButton>
      </div>
    </section>
  </div>
</template>
