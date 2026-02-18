export type ApiResult<T = unknown> = {
  success: boolean
  errorMsg?: string
  data?: T
  total?: number
}

export type LoginFormDTO = {
  account?: string
  phone?: string
  code?: string
  password?: string
}

export type UserDTO = {
  id: number
  nickName: string
  icon: string
}

export type ShopType = {
  id: number
  name: string
  icon: string
  sort: number
}

export type Shop = {
  id: number
  name: string
  typeId: number
  images: string
  area: string
  address: string
  x: number
  y: number
  avgPrice: number
  sold: number
  comments: number
  score: number
  openHours: string
  distance?: number
}

export type Blog = {
  id: number
  shopId: number
  userId: number
  icon?: string
  name?: string
  isLike?: boolean
  title: string
  images: string
  content: string
  liked: number
  comments: number
  createTime: string
  updateTime: string
}

export type Voucher = {
  id: number
  shopId: number
  title: string
  subTitle: string
  rules: string
  payValue: number
  actualValue: number
  type: number
  status: number
  stock?: number
  beginTime?: string
  endTime?: string
}

