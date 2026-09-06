<script setup lang="ts">
import type { BlogComment, BlogDetail, BlogLikeState, CursorPage, Shop, UserDTO } from '~/types/api'
import { getApiErrorMessage } from '~/utils/api-error'
import { resolveImgUrl } from '~/utils/format'

definePageMeta({ middleware: 'auth' })

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const { $apiData } = useNuxtApp()
const toast = useToast()
const id = computed(() => Number(route.params.id))

await auth.fetchMe().catch(() => null)
const { data: blog } = await useAsyncData(
  () => `blog-detail-${id.value}`,
  () => $apiData<BlogDetail>(`/blog/${id.value}`),
  { watch: [id] }
)

const { data: shop } = await useAsyncData(
  () => `blog-shop-${blog.value?.shopId || 0}`,
  () => blog.value?.shopId ? $apiData<Shop>(`/shop/${blog.value.shopId}`) : Promise.resolve(undefined),
  { watch: [() => blog.value?.shopId] }
)

const isOwner = computed(() => Boolean(blog.value && auth.user.value?.id === blog.value.userId))
const pendingLike = ref(false)
const deletingBlog = ref(false)

const likeUsers = ref<UserDTO[]>([])
const likeCursor = ref<string>()
const likesDone = ref(false)
const loadingLikes = ref(false)

const comments = ref<BlogComment[]>([])
const commentCursor = ref<string>()
const commentsDone = ref(false)
const loadingComments = ref(false)
const sendingComment = ref(false)
const commentContent = ref('')
const replyTarget = ref<BlogComment | null>(null)

const images = computed(() => (blog.value?.images || '')
  .split(',')
  .map(value => value.trim())
  .filter(Boolean)
  .map(value => resolveImgUrl(value) || value))

async function toggleLike() {
  if (!blog.value || pendingLike.value) return
  pendingLike.value = true
  try {
    const state = await $apiData<BlogLikeState>(`/blog/${blog.value.id}/like`, {
      method: blog.value.isLike ? 'DELETE' : 'PUT'
    })
    if (state) {
      blog.value.isLike = state.liked
      blog.value.liked = state.likeCount
      await loadLikes(true)
    }
  } catch (error) {
    toast.add({
      title: '点赞操作失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    pendingLike.value = false
  }
}

async function loadLikes(reset = false) {
  if (!blog.value || loadingLikes.value) return
  if (reset) {
    likeUsers.value = []
    likeCursor.value = undefined
    likesDone.value = false
  }
  loadingLikes.value = true
  try {
    const params = new URLSearchParams({ limit: '20' })
    if (likeCursor.value) params.set('cursor', likeCursor.value)
    const page = await $apiData<CursorPage<UserDTO>>(`/blog/likes/${blog.value.id}?${params}`)
    likeUsers.value.push(...(page?.list ?? []))
    likeCursor.value = page?.nextCursor
    likesDone.value = !page?.hasMore
  } finally {
    loadingLikes.value = false
  }
}

async function loadComments(reset = false) {
  if (!blog.value || loadingComments.value) return
  if (reset) {
    comments.value = []
    commentCursor.value = undefined
    commentsDone.value = false
  }
  loadingComments.value = true
  try {
    const params = new URLSearchParams({ blogId: String(blog.value.id), limit: '20' })
    if (commentCursor.value) params.set('cursor', commentCursor.value)
    const page = await $apiData<CursorPage<BlogComment>>(`/blog-comments?${params}`)
    comments.value.push(...(page?.list ?? []))
    commentCursor.value = page?.nextCursor
    commentsDone.value = !page?.hasMore
  } finally {
    loadingComments.value = false
  }
}

function startReply(comment: BlogComment) {
  replyTarget.value = comment
  commentContent.value = ''
}

async function submitComment() {
  if (!blog.value || !commentContent.value.trim()) return
  sendingComment.value = true
  try {
    const target = replyTarget.value
    const parentId = target ? (target.parentId > 0 ? target.parentId : target.id) : undefined
    await $apiData<number>('/blog-comments', {
      method: 'POST',
      body: {
        blogId: blog.value.id,
        content: commentContent.value,
        parentId,
        answerId: target?.id
      }
    })
    commentContent.value = ''
    replyTarget.value = null
    blog.value.comments += 1
    await loadComments(true)
    toast.add({ title: '评论已发布', color: 'success' })
  } catch (error) {
    toast.add({
      title: '发布评论失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    sendingComment.value = false
  }
}

async function deleteComment(comment: BlogComment) {
  if (!confirm('确定删除这条评论吗？')) return
  try {
    await $apiData<unknown>(`/blog-comments/${comment.id}`, { method: 'DELETE' })
    const refreshed = await $apiData<BlogDetail>(`/blog/${id.value}`)
    if (refreshed) blog.value = refreshed
    await loadComments(true)
    toast.add({ title: '评论已删除', color: 'success' })
  } catch (error) {
    toast.add({
      title: '删除评论失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  }
}

async function deleteBlog() {
  if (!blog.value || !confirm('确定删除这篇笔记吗？该操作会同时删除评论和图片。')) return
  deletingBlog.value = true
  try {
    await $apiData<unknown>(`/blog/${blog.value.id}`, { method: 'DELETE' })
    toast.add({ title: '笔记已删除', color: 'success' })
    await navigateTo('/me')
  } catch (error) {
    toast.add({
      title: '删除笔记失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    deletingBlog.value = false
  }
}

await Promise.all([loadLikes(true), loadComments(true)])
</script>

<template>
  <div
    v-if="blog"
    class="mx-auto max-w-4xl space-y-6"
  >
    <div class="flex items-center justify-between gap-3">
      <UButton
        color="neutral"
        variant="ghost"
        icon="i-lucide-arrow-left"
        @click="router.back()"
      >
        返回
      </UButton>
      <div
        v-if="isOwner"
        class="flex gap-2"
      >
        <UButton
          :to="`/blogs/${blog.id}/edit`"
          color="neutral"
          variant="outline"
          icon="i-lucide-pencil"
        >
          编辑
        </UButton>
        <UButton
          color="error"
          variant="outline"
          icon="i-lucide-trash-2"
          :loading="deletingBlog"
          @click="deleteBlog"
        >
          删除
        </UButton>
      </div>
    </div>

    <UCard>
      <div class="space-y-5">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <NuxtLink
            :to="`/users/${blog.userId}`"
            class="flex items-center gap-3"
          >
            <UAvatar
              :src="resolveImgUrl(blog.icon) || '/imgs/icons/default-icon.png'"
              size="lg"
            />
            <div>
              <div class="font-semibold text-highlighted">{{ blog.name || '匿名用户' }}</div>
              <div class="text-xs text-muted">{{ new Date(blog.createTime).toLocaleString() }}</div>
            </div>
          </NuxtLink>
          <UButton
            v-if="shop"
            :to="`/shops/${shop.id}`"
            color="neutral"
            variant="subtle"
            icon="i-lucide-store"
          >
            {{ shop.name }}
          </UButton>
        </div>

        <div>
          <h1 class="text-2xl font-semibold text-highlighted">
            {{ blog.title }}
          </h1>
          <p class="mt-3 whitespace-pre-wrap leading-7 text-default">
            {{ blog.content }}
          </p>
        </div>

        <div
          v-if="images.length"
          class="grid gap-3 sm:grid-cols-2"
        >
          <img
            v-for="(image, index) in images"
            :key="index"
            :src="image"
            :alt="`${blog.title}-${index + 1}`"
            class="w-full rounded-xl object-cover"
          >
        </div>

        <div class="flex items-center gap-2 border-t border-default pt-4">
          <UButton
            :color="blog.isLike ? 'primary' : 'neutral'"
            variant="subtle"
            :loading="pendingLike"
            @click="toggleLike"
          >
            <UIcon
              name="i-lucide-thumbs-up"
              class="size-4"
            /> {{ blog.liked }}
          </UButton>
          <UBadge
            color="neutral"
            variant="subtle"
          >
            <UIcon
              name="i-lucide-message-circle"
              class="mr-1 size-4"
            />{{ blog.comments }}
          </UBadge>
        </div>
      </div>
    </UCard>

    <UCard>
      <template #header>
        <div class="font-semibold text-highlighted">
          最近点赞
        </div>
      </template>
      <div
        v-if="likeUsers.length"
        class="flex flex-wrap gap-3"
      >
        <NuxtLink
          v-for="user in likeUsers"
          :key="user.id"
          :to="`/users/${user.id}`"
          class="flex items-center gap-2 rounded-full border border-default px-3 py-2"
        >
          <UAvatar
            :src="resolveImgUrl(user.icon) || '/imgs/icons/default-icon.png'"
            size="xs"
          />
          <span class="text-sm">{{ user.nickName }}</span>
        </NuxtLink>
      </div>
      <p
        v-else
        class="text-sm text-muted"
      >
        还没有人点赞
      </p>
      <UButton
        v-if="!likesDone"
        class="mt-3"
        variant="ghost"
        :loading="loadingLikes"
        @click="loadLikes(false)"
      >
        加载更多
      </UButton>
    </UCard>

    <UCard id="comments">
      <template #header>
        <div class="font-semibold text-highlighted">
          评论
        </div>
      </template>

      <div class="mb-5 space-y-2">
        <div
          v-if="replyTarget"
          class="flex items-center justify-between rounded-lg bg-muted px-3 py-2 text-sm"
        >
          <span>回复 {{ replyTarget.author?.nickName || '用户' }}</span>
          <UButton
            size="xs"
            color="neutral"
            variant="ghost"
            @click="replyTarget = null"
          >
            取消回复
          </UButton>
        </div>
        <UTextarea
          v-model="commentContent"
          :rows="3"
          maxlength="255"
          placeholder="说说你的真实体验"
          class="w-full"
        />
        <div class="flex justify-end">
          <UButton
            :loading="sendingComment"
            @click="submitComment"
          >
            发布评论
          </UButton>
        </div>
      </div>

      <div
        v-if="comments.length"
        class="space-y-5"
      >
        <div
          v-for="comment in comments"
          :key="comment.id"
          class="border-t border-default pt-4 first:border-0 first:pt-0"
        >
          <div class="flex gap-3">
            <UAvatar
              :src="resolveImgUrl(comment.author?.icon) || '/imgs/icons/default-icon.png'"
              size="sm"
            />
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-3">
                <NuxtLink
                  :to="`/users/${comment.userId}`"
                  class="text-sm font-medium text-highlighted"
                >{{ comment.author?.nickName || '用户' }}</NuxtLink>
                <span class="text-xs text-muted">{{ new Date(comment.createTime).toLocaleString() }}</span>
              </div>
              <p class="mt-1 whitespace-pre-wrap text-sm">
                {{ comment.content }}
              </p>
              <div class="mt-1 flex gap-2">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  @click="startReply(comment)"
                >
                  回复
                </UButton>
                <UButton
                  v-if="auth.user.value?.id === comment.userId"
                  size="xs"
                  color="error"
                  variant="ghost"
                  @click="deleteComment(comment)"
                >
                  删除
                </UButton>
              </div>

              <div
                v-if="comment.replies?.length"
                class="mt-3 space-y-3 rounded-lg bg-muted/50 p-3"
              >
                <div
                  v-for="reply in comment.replies"
                  :key="reply.id"
                  class="text-sm"
                >
                  <div class="flex flex-wrap items-center gap-1">
                    <NuxtLink
                      :to="`/users/${reply.userId}`"
                      class="font-medium text-highlighted"
                    >{{ reply.author?.nickName || '用户' }}</NuxtLink>
                    <span
                      v-if="reply.answerUser"
                      class="text-muted"
                    >回复 {{ reply.answerUser.nickName }}</span>
                    <span class="ml-auto text-xs text-muted">{{ new Date(reply.createTime).toLocaleString() }}</span>
                  </div>
                  <p class="mt-1 whitespace-pre-wrap">
                    {{ reply.content }}
                  </p>
                  <div class="mt-1 flex gap-2">
                    <UButton
                      size="xs"
                      color="neutral"
                      variant="ghost"
                      @click="startReply(reply)"
                    >
                      回复
                    </UButton>
                    <UButton
                      v-if="auth.user.value?.id === reply.userId"
                      size="xs"
                      color="error"
                      variant="ghost"
                      @click="deleteComment(reply)"
                    >
                      删除
                    </UButton>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <p
        v-else
        class="text-sm text-muted"
      >
        还没有评论，来写第一条吧。
      </p>
      <div class="mt-4 flex justify-center">
        <UButton
          v-if="!commentsDone"
          variant="outline"
          :loading="loadingComments"
          @click="loadComments(false)"
        >
          加载更多评论
        </UButton>
      </div>
    </UCard>
  </div>
</template>
