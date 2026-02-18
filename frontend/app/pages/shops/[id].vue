<script setup lang="ts">
import type { Shop, Voucher } from '~/types/api'
import { formatFen, resolveImgUrl } from '~/utils/format'

const { $apiData } = useNuxtApp()
const toast = useToast()
const route = useRoute()
const router = useRouter()

const id = computed(() => String(route.params.id))

const { data: shop, pending: shopPending } = await useAsyncData(
  () => `shop-${id.value}`,
  () => $apiData<Shop>(`/shop/${id.value}`),
  { watch: [id] }
)

const { data: vouchers } = await useAsyncData(
  () => `vouchers-${id.value}`,
  () => $apiData<Voucher[]>(`/voucher/list/${id.value}`),
  { watch: [id] }
)

const shopImages = computed(() => {
  const images = shop.value?.images
  if (!images) return []
  return images.split(',')
    .map(s => s.trim())
    .filter(Boolean)
    .map(s => resolveImgUrl(s) || s)
})

function scoreText(score?: number) {
  if (!score) return '0.0'
  return (score / 10).toFixed(1)
}

function isNotBegin(v: Voucher) {
  if (!v.beginTime) return false
  return new Date(v.beginTime).getTime() > Date.now()
}

function isEnd(v: Voucher) {
  if (!v.endTime) return false
  return new Date(v.endTime).getTime() < Date.now()
}

function discountText(v: Voucher) {
  if (!v.payValue || !v.actualValue) return ''
  const d = (v.payValue * 10) / v.actualValue
  return `${d.toFixed(1)} 折`
}

async function seckill(v: Voucher) {
  try {
    const orderId = await $apiData<number>(`/voucher-order/seckill/${v.id}`, { method: 'POST' })
    toast.add({
      title: '抢购成功',
      description: orderId ? `订单号：${orderId}` : undefined,
      color: 'success',
      icon: 'i-lucide-check-circle'
    })
  } catch (error) {
    toast.add({
      title: '抢购失败',
      description: (error as any)?.statusMessage || (error as any)?.message,
      color: 'error',
      icon: 'i-lucide-x-circle'
    })
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center gap-2">
      <UButton color="neutral" variant="ghost" icon="i-lucide-arrow-left" @click="router.back()">
        返回
      </UButton>
      <div class="min-w-0">
        <div class="text-sm text-muted">
          商户详情
        </div>
        <div class="text-lg font-semibold text-highlighted truncate">
          {{ shop?.name || '—' }}
        </div>
      </div>
    </div>

    <div v-if="shopPending" class="text-sm text-muted">
      加载中...
    </div>

    <template v-else>
      <UCard v-if="shop">
        <div class="space-y-4">
          <div class="flex flex-col gap-2">
            <div class="flex items-center justify-between gap-3">
              <div class="text-xl font-semibold text-highlighted">
                {{ shop.name }}
              </div>
              <div class="flex items-center gap-1 text-sm text-muted">
                <UIcon name="i-lucide-star" class="size-4 text-primary" />
                <span>{{ scoreText(shop.score) }}</span>
                <span>·</span>
                <span>{{ shop.comments }} 条</span>
              </div>
            </div>

            <div class="text-sm text-muted">
              {{ shop.area }} · {{ shop.address }}
            </div>

            <div class="flex flex-wrap gap-2 text-sm">
              <UBadge color="neutral" variant="subtle">
                均价 ￥{{ formatFen(shop.avgPrice) }}/人
              </UBadge>
              <UBadge color="neutral" variant="subtle">
                营业 {{ shop.openHours }}
              </UBadge>
            </div>
          </div>

          <div v-if="shopImages.length" class="grid grid-cols-2 sm:grid-cols-3 gap-2">
            <div v-for="(img, idx) in shopImages" :key="idx" class="aspect-video rounded-lg overflow-hidden bg-muted">
              <img :src="img" :alt="`${shop.name}-${idx}`" class="w-full h-full object-cover">
            </div>
          </div>
        </div>
      </UCard>

      <UCard>
        <template #header>
          <div class="flex items-center justify-between">
            <div class="font-semibold text-highlighted">
              代金券 / 秒杀券
            </div>
            <div class="text-sm text-muted">
              {{ (vouchers || []).length }} 张
            </div>
          </div>
        </template>

        <div v-if="!vouchers || vouchers.length === 0" class="text-sm text-muted">
          暂无优惠券
        </div>

        <div v-else class="space-y-3">
          <div
            v-for="v in vouchers"
            :key="v.id"
            class="flex items-start justify-between gap-3 rounded-lg border border-default p-3"
            :class="isEnd(v) ? 'opacity-60' : ''"
          >
            <div class="min-w-0">
              <div class="font-semibold text-highlighted line-clamp-1">
                {{ v.title }}
              </div>
              <div class="text-sm text-muted line-clamp-2">
                {{ v.subTitle }}
              </div>

              <div class="flex items-center gap-2 mt-2">
                <UBadge color="primary" variant="subtle">
                  ￥{{ formatFen(v.payValue) }}
                </UBadge>
                <UBadge v-if="discountText(v)" color="neutral" variant="subtle">
                  {{ discountText(v) }}
                </UBadge>
                <UBadge v-if="v.type" color="warning" variant="subtle">
                  秒杀
                </UBadge>
              </div>

              <div v-if="v.type" class="text-xs text-muted mt-2">
                <span v-if="v.stock !== undefined">库存 {{ v.stock }} · </span>
                <span v-if="v.beginTime && v.endTime">
                  {{ new Date(v.beginTime).toLocaleString() }} ~ {{ new Date(v.endTime).toLocaleString() }}
                </span>
              </div>
            </div>

            <div class="shrink-0">
              <UButton
                v-if="v.type"
                color="primary"
                size="sm"
                :disabled="isNotBegin(v) || isEnd(v) || (v.stock !== undefined && v.stock < 1)"
                @click="seckill(v)"
              >
                限时抢购
              </UButton>
              <UButton v-else color="primary" size="sm" disabled>
                抢购
              </UButton>
            </div>
          </div>
        </div>
      </UCard>
    </template>
  </div>
</template>

