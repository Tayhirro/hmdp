export type ApiResult<T = unknown> = {
  success: boolean
  errorMsg?: string
  errorCode?: string
  traceId?: string
  data?: T
  total?: number
}

export type CursorPage<T> = {
  list: T[]
  nextCursor?: string
  hasMore: boolean
}

export type PageResult<T> = {
  list: T[]
  current: number
  pageSize: number
  total: number
  hasMore: boolean
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

export type UserInfo = {
  userId: number
  city: string
  introduce: string
  fans: number
  followee: number
  gender: number
  birthday?: string
  credits: number
  level: number
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

export type BlogCard = {
  id: number
  shopId: number
  userId: number
  icon?: string
  name?: string
  isLike?: boolean
  title: string
  images: string
  liked: number
  comments: number
  createTime: string
}

export type ShopSearchItem = Omit<Shop, 'x' | 'y'>

export type BlogDetail = BlogCard & {
  content: string
  updateTime: string
  imageIds: number[]
}

export type BlogLikeState = {
  liked: boolean
  likeCount: number
}

export type BlogImageUpload = {
  id: number
  url: string
}

export type BlogComment = {
  id: number
  blogId: number
  userId: number
  parentId: number
  answerId: number
  content: string
  liked: number
  createTime: string
  author?: UserDTO
  answerUser?: UserDTO
  replies: BlogComment[]
}

export type SearchScope = 'SHOP' | 'BLOG' | 'USER'

export type SearchResultItem = ShopSearchItem | BlogCard | UserDTO

export type SearchSection = {
  scope: SearchScope
  items: SearchResultItem[]
  total: number
  hasMore: boolean
}

export type UnifiedSearchResult = {
  normalizedKeyword: string
  sections: SearchSection[]
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
