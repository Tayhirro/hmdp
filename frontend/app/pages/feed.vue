<script setup lang="ts">
import type { BlogCard, CursorPage } from '~/types/api'
import { getApiErrorMessage } from '~/utils/api-error'

definePageMeta({ middleware: 'auth' })

const route = useRoute()
const router = useRouter()
const { $apiData } = useNuxtApp()
const toast = useToast()

type FeedMode = 'following' | 'for_you'

const mode = computed<FeedMode>(() => route.query.mode === 'for_you' ? 'for_you' : 'following')
const blogs = ref<BlogCard[]>([])
const cursor = ref<string>()
const done = ref(false)
const loading = ref(false)

async function loadFeed(reset = false, refresh = false) {
  if (loading.value) return
  if (reset) {
    blogs.value = []
    cursor.value = undefined
    done.value = false
  }
  loading.value = true
  try {
    const params = new URLSearchParams({ mode: mode.value })
    if (cursor.value) params.set('cursor', cursor.value)
    if (refresh) params.set('refresh', 'true')
    const page = await $apiData<CursorPage<BlogCard>>(`/blog/feed?${params}`)
    blogs.value.push(...(page?.list ?? []))
    cursor.value = page?.nextCursor
    done.value = !page?.hasMore
  } catch (error) {
    toast.add({
      title: 'Feed 加载失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    loading.value = false
  }
}

function changeMode(value: FeedMode) {
  router.push({ path: '/feed', query: { mode: value } })
}

await loadFeed(true)
watch(mode, () => loadFeed(true))
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-sm text-muted">
          稳定产品模式对应固定召回与排序策略
        </p>
        <h1 class="text-2xl font-semibold text-highlighted">
          内容 Feed
        </h1>
      </div>
      <UButton
        color="neutral"
        variant="outline"
        icon="i-lucide-refresh-cw"
        :loading="loading"
        @click="loadFeed(true, true)"
      >
        刷新
      </UButton>
    </div>

    <div class="flex gap-2">
      <UButton
        :variant="mode === 'following' ? 'solid' : 'outline'"
        @click="changeMode('following')"
      >
        关注
      </UButton>
      <UButton
        :variant="mode === 'for_you' ? 'solid' : 'outline'"
        @click="changeMode('for_you')"
      >
        为你推荐
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
    <UCard v-else-if="!loading">
      <div class="py-8 text-center text-sm text-muted">
        {{ mode === 'following' ? '关注的用户还没有发布内容，先去搜索并关注感兴趣的人吧。' : '暂时没有可推荐内容。' }}
      </div>
    </UCard>

    <div class="flex justify-center">
      <UButton
        v-if="!done"
        variant="outline"
        :loading="loading"
        @click="loadFeed(false)"
      >
        加载更多
      </UButton>
      <span
        v-else-if="blogs.length"
        class="text-sm text-muted"
      >没有更多了</span>
    </div>
  </div>
</template>
