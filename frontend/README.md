# 黑马点评前端（Nuxt UI）

这个目录是为 `hmdp` 后端（Spring Boot，默认 `8081`）配套的新前端，使用 **Nuxt 4 + @nuxt/ui v4**。

## 功能

- 登录：验证码 / 密码 / 注册（对接 `/user/*`）
- 首页：分类 + 热门笔记（对接 `/shop-type/list`、`/blog/hot`）
- 商户：分类筛选 + 名称搜索（对接 `/shop/of/type`、`/shop/of/name`）
- 商户详情：商户信息 + 优惠券列表（对接 `/shop/{id}`、`/voucher/list/{shopId}`）
- 我的：个人信息 + 签到（对接 `/user/me`、`/user/sign`、`/user/sign/count`）

> 图片资源已从旧前端 `src/main/resources/nginx-1.18.0/html/hmdp/imgs` 复制到 `public/imgs`，因此数据库里类似 `/imgs/blogs/...` 的图片路径可直接显示。

## 本地开发

1) 启动后端（默认端口 `8081`）

2) 安装依赖（推荐 corepack + pnpm）

```bash
cd hmdp/frontend
pnpm install
```

如果你没有 pnpm，也可以用 npm：

```bash
cd hmdp/frontend
npm install
```

3) 启动前端

```bash
pnpm dev
```

前端默认在 `http://localhost:3000`。

## 代理与环境变量

前端默认使用 `/api` 作为后端前缀，并通过 Nuxt 的代理转发到后端：

- `NUXT_PUBLIC_API_BASE`：默认 `/api`
- `NUXT_DEV_PROXY_TARGET`：默认 `http://localhost:8081`

复制示例文件并按需修改：

```bash
cd hmdp/frontend
copy .env.example .env
```
