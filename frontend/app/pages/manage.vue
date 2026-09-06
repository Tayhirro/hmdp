<script setup lang="ts">
import type { Shop, ShopType, Voucher } from '~/types/api'
import { getApiErrorMessage } from '~/utils/api-error'

definePageMeta({ middleware: 'auth' })

const { $apiData } = useNuxtApp()
const toast = useToast()

const { data: shopTypes } = await useAsyncData(
  'manage-shop-types',
  () => $apiData<ShopType[]>('/shop-type/list')
)

const loadingShop = ref(false)
const savingShop = ref(false)
const editShopId = ref('')
const shopForm = reactive<Partial<Shop>>({
  name: '',
  typeId: 1,
  images: '',
  area: '',
  address: '',
  x: 0,
  y: 0,
  avgPrice: 0,
  sold: 0,
  comments: 0,
  score: 50,
  openHours: ''
})

function resetShop() {
  editShopId.value = ''
  Object.assign(shopForm, {
    id: undefined,
    name: '',
    typeId: shopTypes.value?.[0]?.id ?? 1,
    images: '',
    area: '',
    address: '',
    x: 0,
    y: 0,
    avgPrice: 0,
    sold: 0,
    comments: 0,
    score: 50,
    openHours: ''
  })
}

async function loadShop() {
  const id = Number(editShopId.value)
  if (!Number.isFinite(id) || id < 1) {
    toast.add({ title: '请输入有效店铺 ID', color: 'warning' })
    return
  }
  loadingShop.value = true
  try {
    const shop = await $apiData<Shop>(`/shop/${id}`)
    if (shop) Object.assign(shopForm, shop)
  } catch (error) {
    toast.add({
      title: '读取店铺失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    loadingShop.value = false
  }
}

async function saveShop() {
  if (!shopForm.name?.trim() || !shopForm.typeId || !shopForm.address?.trim()) {
    toast.add({ title: '请填写店名、分类和地址', color: 'warning' })
    return
  }
  savingShop.value = true
  try {
    const isUpdate = Boolean(shopForm.id)
    const id = await $apiData<number | undefined>('/shop', {
      method: isUpdate ? 'PUT' : 'POST',
      body: shopForm
    })
    toast.add({
      title: isUpdate ? '店铺已更新' : '店铺已创建',
      description: id ? `店铺 ID：${id}` : undefined,
      color: 'success'
    })
    if (!isUpdate) resetShop()
  } catch (error) {
    toast.add({
      title: '保存店铺失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    savingShop.value = false
  }
}

const voucherKind = ref<'normal' | 'seckill'>('normal')
const savingVoucher = ref(false)
const voucherForm = reactive<Partial<Voucher>>({
  shopId: undefined,
  title: '',
  subTitle: '',
  rules: '',
  payValue: 0,
  actualValue: 0,
  type: 0,
  status: 1,
  stock: 0,
  beginTime: '',
  endTime: ''
})

async function saveVoucher() {
  if (!voucherForm.shopId || !voucherForm.title?.trim()) {
    toast.add({ title: '请填写店铺 ID 和券标题', color: 'warning' })
    return
  }
  savingVoucher.value = true
  try {
    const isSeckill = voucherKind.value === 'seckill'
    const body = {
      ...voucherForm,
      type: isSeckill ? 1 : 0,
      stock: isSeckill ? voucherForm.stock : undefined,
      beginTime: isSeckill ? voucherForm.beginTime : undefined,
      endTime: isSeckill ? voucherForm.endTime : undefined
    }
    const id = await $apiData<number>(isSeckill ? '/voucher/seckill' : '/voucher', {
      method: 'POST',
      body
    })
    toast.add({ title: '优惠券已创建', description: `优惠券 ID：${id}`, color: 'success' })
  } catch (error) {
    toast.add({
      title: '创建优惠券失败',
      description: getApiErrorMessage(error),
      color: 'error'
    })
  } finally {
    savingVoucher.value = false
  }
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <p class="text-sm text-muted">
        基础版运营入口
      </p>
      <h1 class="text-2xl font-semibold text-highlighted">
        店铺与优惠券管理
      </h1>
    </div>

    <UCard>
      <template #header>
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div class="font-semibold text-highlighted">
            新增或更新店铺
          </div>
          <div class="flex gap-2">
            <UInput
              v-model="editShopId"
              placeholder="输入店铺 ID 后加载"
            />
            <UButton
              variant="outline"
              :loading="loadingShop"
              @click="loadShop"
            >
              加载
            </UButton>
            <UButton
              color="neutral"
              variant="ghost"
              @click="resetShop"
            >
              新建
            </UButton>
          </div>
        </div>
      </template>

      <div class="grid gap-4 md:grid-cols-2">
        <UFormField label="店铺名称">
          <UInput
            v-model="shopForm.name"
            class="w-full"
          />
        </UFormField>
        <UFormField label="店铺分类">
          <select
            v-model.number="shopForm.typeId"
            class="w-full rounded-md border border-default bg-default px-3 py-2 text-sm"
          >
            <option
              v-for="type in (shopTypes || [])"
              :key="type.id"
              :value="type.id"
            >
              {{ type.name }}
            </option>
          </select>
        </UFormField>
        <UFormField label="商圈">
          <UInput
            v-model="shopForm.area"
            class="w-full"
          />
        </UFormField>
        <UFormField label="地址">
          <UInput
            v-model="shopForm.address"
            class="w-full"
          />
        </UFormField>
        <UFormField label="经度">
          <UInput
            v-model.number="shopForm.x"
            type="number"
            step="0.000001"
            class="w-full"
          />
        </UFormField>
        <UFormField label="纬度">
          <UInput
            v-model.number="shopForm.y"
            type="number"
            step="0.000001"
            class="w-full"
          />
        </UFormField>
        <UFormField label="人均价格">
          <UInput
            v-model.number="shopForm.avgPrice"
            type="number"
            class="w-full"
          />
        </UFormField>
        <UFormField label="评分（例如 46 表示 4.6）">
          <UInput
            v-model.number="shopForm.score"
            type="number"
            class="w-full"
          />
        </UFormField>
        <UFormField label="营业时间">
          <UInput
            v-model="shopForm.openHours"
            placeholder="10:00-22:00"
            class="w-full"
          />
        </UFormField>
        <UFormField
          label="图片 URL（多张用逗号分隔）"
          class="md:col-span-2"
        >
          <UTextarea
            v-model="shopForm.images"
            :rows="3"
            class="w-full"
          />
        </UFormField>
      </div>
      <div class="flex justify-end mt-4">
        <UButton
          :loading="savingShop"
          @click="saveShop"
        >
          {{ shopForm.id ? '更新店铺' : '创建店铺' }}
        </UButton>
      </div>
    </UCard>

    <UCard>
      <template #header>
        <div class="font-semibold text-highlighted">
          新增优惠券
        </div>
      </template>

      <div class="flex gap-2 mb-4">
        <UButton
          :variant="voucherKind === 'normal' ? 'solid' : 'outline'"
          @click="voucherKind = 'normal'"
        >
          普通券
        </UButton>
        <UButton
          :variant="voucherKind === 'seckill' ? 'solid' : 'outline'"
          @click="voucherKind = 'seckill'"
        >
          秒杀券配置
        </UButton>
      </div>
      <div class="grid gap-4 md:grid-cols-2">
        <UFormField label="所属店铺 ID">
          <UInput
            v-model.number="voucherForm.shopId"
            type="number"
            class="w-full"
          />
        </UFormField>
        <UFormField label="标题">
          <UInput
            v-model="voucherForm.title"
            class="w-full"
          />
        </UFormField>
        <UFormField label="副标题">
          <UInput
            v-model="voucherForm.subTitle"
            class="w-full"
          />
        </UFormField>
        <UFormField label="支付金额（分）">
          <UInput
            v-model.number="voucherForm.payValue"
            type="number"
            class="w-full"
          />
        </UFormField>
        <UFormField label="抵扣金额（分）">
          <UInput
            v-model.number="voucherForm.actualValue"
            type="number"
            class="w-full"
          />
        </UFormField>
        <template v-if="voucherKind === 'seckill'">
          <UFormField label="库存">
            <UInput
              v-model.number="voucherForm.stock"
              type="number"
              class="w-full"
            />
          </UFormField>
          <UFormField label="开始时间">
            <UInput
              v-model="voucherForm.beginTime"
              type="datetime-local"
              class="w-full"
            />
          </UFormField>
          <UFormField label="结束时间">
            <UInput
              v-model="voucherForm.endTime"
              type="datetime-local"
              class="w-full"
            />
          </UFormField>
        </template>
        <UFormField
          label="使用规则"
          class="md:col-span-2"
        >
          <UTextarea
            v-model="voucherForm.rules"
            :rows="4"
            class="w-full"
          />
        </UFormField>
      </div>
      <div class="flex justify-end mt-4">
        <UButton
          :loading="savingVoucher"
          @click="saveVoucher"
        >
          创建优惠券
        </UButton>
      </div>
      <p class="text-xs text-muted mt-3">
        这里只配置和展示券；按你的要求，本轮不实现抢购下单与订单管理。
      </p>
    </UCard>
  </div>
</template>
