<script setup lang="ts">
import type { BlogDetail, BlogImageUpload, PageResult, ShopSearchItem } from '~/types/api'
import { getApiErrorMessage } from '~/utils/api-error'
import { resolveImgUrl } from '~/utils/format'

type ImageAsset = {
  id?: number
  url: string
  temporary: boolean
}

const props = defineProps<{ blog?: BlogDetail }>()
const emit = defineEmits<{ saved: [blogId: number] }>()

const { $apiData } = useNuxtApp()
const toast = useToast()

const title = ref(props.blog?.title || '')
const content = ref(props.blog?.content || '')
const shopId = ref<number | undefined>(props.blog?.shopId)
const shopKeyword = ref('')
const shopResults = ref<ShopSearchItem[]>([])
const searchingShops = ref(false)
const uploading = ref(false)
const saving = ref(false)

const existingUrls = (props.blog?.images || '').split(',').map(value => value.trim()).filter(Boolean)
const existingIds = props.blog?.imageIds || []
const images = ref<ImageAsset[]>(existingUrls.map((url, index) => ({
  id: existingIds[index],
  url,
  temporary: false
})))

function newRequestId() {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const clientRequestId = ref(newRequestId())

async function searchShops() {
  const keyword = shopKeyword.value.trim()
  if (!keyword) return
  searchingShops.value = true
  try {
    const page = await $apiData<PageResult<ShopSearchItem>>(`/search/shops?keyword=${encodeURIComponent(keyword)}&current=1`)
    shopResults.value = page?.list ?? []
  } finally {
    searchingShops.value = false
  }
}

function selectShop(shop: ShopSearchItem) {
  shopId.value = shop.id
  shopKeyword.value = shop.name
  shopResults.value = []
}

async function uploadFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  if (images.value.length + files.length > 9) {
    toast.add({ title: '一篇笔记最多 9 张图片', color: 'warning' })
    return
  }
  uploading.value = true
  try {
    for (const file of files) {
      const body = new FormData()
      body.append('file', file)
      const uploaded = await $apiData<BlogImageUpload>('/upload/blog', { method: 'POST', body })
      if (uploaded) images.value.push({ id: uploaded.id, url: uploaded.url, temporary: true })
    }
  } catch (error) {
    toast.add({
      title: '图片上传失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    uploading.value = false
  }
}

async function removeImage(index: number) {
  const image = images.value[index]
  if (!image) return
  if (image.temporary && image.id) {
    try {
      await $apiData<unknown>(`/upload/blog/${image.id}`, { method: 'DELETE' })
    } catch (error) {
      toast.add({
        title: '删除临时图片失败',
        description: getApiErrorMessage(error),
        color: 'error'
      })
      return
    }
  }
  images.value.splice(index, 1)
}

async function save() {
  const imageIds = images.value.map(image => image.id).filter((id): id is number => typeof id === 'number')
  if (!shopId.value || !title.value.trim() || !content.value.trim()) {
    toast.add({ title: '请选择店铺并填写标题和正文', color: 'warning' })
    return
  }
  if (imageIds.length !== images.value.length || imageIds.length === 0) {
    toast.add({
      title: '请补充可编辑图片',
      description: '历史笔记中未建立资产记录的图片需要移除并重新上传。',
      color: 'warning'
    })
    return
  }

  saving.value = true
  try {
    const body = {
      shopId: shopId.value,
      title: title.value,
      content: content.value,
      imageIds,
      ...(props.blog ? {} : { clientRequestId: clientRequestId.value })
    }
    const id = await $apiData<number>(props.blog ? `/blog/${props.blog.id}` : '/blog', {
      method: props.blog ? 'PUT' : 'POST',
      body
    })
    emit('saved', id || props.blog!.id)
  } catch (error) {
    toast.add({
      title: props.blog ? '保存修改失败' : '发布失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <UCard>
    <div class="space-y-5">
      <UFormField
        label="关联店铺"
        required
      >
        <div class="flex gap-2">
          <UInput
            v-model="shopKeyword"
            placeholder="输入店名搜索"
            class="flex-1"
            @keyup.enter="searchShops"
          />
          <UButton
            variant="outline"
            :loading="searchingShops"
            @click="searchShops"
          >
            查找
          </UButton>
        </div>
        <div
          v-if="shopResults.length"
          class="mt-2 divide-y divide-default rounded-lg border border-default"
        >
          <button
            v-for="shop in shopResults"
            :key="shop.id"
            type="button"
            class="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-muted"
            @click="selectShop(shop)"
          >
            <span>{{ shop.name }}</span><span class="text-muted">{{ shop.area }}</span>
          </button>
        </div>
        <p
          v-if="shopId"
          class="mt-1 text-xs text-primary"
        >
          已选择店铺 ID：{{ shopId }}
        </p>
      </UFormField>

      <UFormField
        label="标题"
        required
      >
        <UInput
          v-model="title"
          maxlength="255"
          class="w-full"
        />
      </UFormField>
      <UFormField
        label="正文"
        required
      >
        <UTextarea
          v-model="content"
          :rows="8"
          maxlength="2048"
          class="w-full"
        />
      </UFormField>

      <UFormField
        label="图片（1～9 张）"
        required
      >
        <input
          type="file"
          accept="image/*"
          multiple
          :disabled="uploading || images.length >= 9"
          @change="uploadFiles"
        >
        <div
          v-if="images.length"
          class="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3"
        >
          <div
            v-for="(image, index) in images"
            :key="`${image.id || 'legacy'}-${index}`"
            class="relative aspect-square overflow-hidden rounded-lg bg-muted"
          >
            <img
              :src="resolveImgUrl(image.url) || image.url"
              alt="笔记图片"
              class="h-full w-full object-cover"
            >
            <UButton
              class="absolute right-1 top-1"
              color="error"
              variant="solid"
              size="xs"
              icon="i-lucide-x"
              @click="removeImage(index)"
            />
            <UBadge
              v-if="!image.id"
              class="absolute bottom-1 left-1"
              color="warning"
            >
              历史图片
            </UBadge>
          </div>
        </div>
      </UFormField>

      <div class="flex justify-end gap-2">
        <UButton
          color="neutral"
          variant="ghost"
          @click="$router.back()"
        >
          取消
        </UButton>
        <UButton
          :loading="saving || uploading"
          @click="save"
        >
          {{ blog ? '保存修改' : '发布笔记' }}
        </UButton>
      </div>
    </div>
  </UCard>
</template>
