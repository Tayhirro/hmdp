<script setup lang="ts">
import type { BlogCard, BlogDetail, BlogLikeState } from '~/types/api'
import { getApiErrorMessage } from '~/utils/api-error'
import { firstCsvItem, resolveImgUrl } from '~/utils/format'

const props = defineProps<{ blog: BlogCard }>()

const { $apiData } = useNuxtApp()
const toast = useToast()
const pending = ref(false)
const state = reactive<BlogCard>({ ...props.blog, isLike: Boolean(props.blog.isLike) })

watch(() => props.blog, value => Object.assign(state, value, { isLike: Boolean(value.isLike) }), { deep: true })

function cover(images: string) {
  return resolveImgUrl(firstCsvItem(images)) || '/imgs/blogs/blog1.jpg'
}

async function toggleLike() {
  if (pending.value) return
  pending.value = true
  const shouldLike = !state.isLike
  try {
    const result = await $apiData<BlogLikeState>(`/blog/${state.id}/like`, {
      method: shouldLike ? 'PUT' : 'DELETE'
    })
    if (result) {
      state.isLike = result.liked
      state.liked = result.likeCount
    }
  } catch (error) {
    try {
      const refreshed = await $apiData<BlogDetail>(`/blog/${state.id}`)
      if (refreshed) {
        state.isLike = Boolean(refreshed.isLike)
        state.liked = refreshed.liked
      }
    } catch {
      // 状态不明时保留旧值，不在客户端猜测服务端写入结果。
    }
    toast.add({
      title: '点赞操作失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <UCard class="h-full overflow-hidden">
    <div class="flex flex-col gap-3">
      <NuxtLink
        :to="`/blogs/${state.id}`"
        class="aspect-video rounded-lg overflow-hidden bg-muted block"
      >
        <img
          :src="cover(state.images)"
          :alt="state.title"
          class="h-full w-full object-cover"
        >
      </NuxtLink>

      <NuxtLink
        :to="`/blogs/${state.id}`"
        class="font-semibold text-highlighted line-clamp-2 hover:text-primary"
      >
        {{ state.title }}
      </NuxtLink>

      <div class="flex items-center justify-between gap-3">
        <NuxtLink
          :to="`/users/${state.userId}`"
          class="flex min-w-0 items-center gap-2"
        >
          <UAvatar
            :src="resolveImgUrl(state.icon) || '/imgs/icons/default-icon.png'"
            size="xs"
          />
          <span class="truncate text-sm text-muted">{{ state.name || '匿名用户' }}</span>
        </NuxtLink>

        <div class="flex items-center gap-1">
          <UButton
            color="neutral"
            variant="ghost"
            size="xs"
            :to="`/blogs/${state.id}#comments`"
          >
            <UIcon
              name="i-lucide-message-circle"
              class="size-4"
            />
            {{ state.comments }}
          </UButton>
          <UButton
            :color="state.isLike ? 'primary' : 'neutral'"
            variant="ghost"
            size="xs"
            :loading="pending"
            :disabled="pending"
            @click="toggleLike"
          >
            <UIcon
              name="i-lucide-thumbs-up"
              class="size-4"
            />
            {{ state.liked }}
          </UButton>
        </div>
      </div>
    </div>
  </UCard>
</template>
