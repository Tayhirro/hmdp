# 黑马点评前端（Nuxt UI）

这个目录是 `hmdp` Spring Boot 后端配套的当前 Nuxt 前端，使用 **Nuxt 4 + @nuxt/ui v4**。后端默认端口为 `9090`。

## 已完成的基础功能

- 用户：验证码、账号或手机号登录，注册、绑定手机号、退出登录、资料展示与编辑、签到。
- 店铺：分类列表、名称搜索、店铺详情、浏览器定位与 Redis GEO 附近店铺查询。
- 店铺管理：新增和更新店铺；当前学习版尚未建立管理员角色边界。
- 博客：发布、编辑、删除、图片上传与临时图片删除、热门列表、详情、作者博客列表。
- 博客互动：点赞、取消点赞、点赞用户列表、一级评论、回复、评论删除。
- 社交关系：关注、取关、关注列表、共同关注。
- 内容分发：Following Feed 与基础规则版 For You Feed，支持刷新和游标翻页。
- 搜索：综合、店铺、笔记、用户四个 Tab；垂直域分别分页。
- 优惠券：店铺券展示，以及普通券、秒杀券的管理录入。

## 当前明确不开放

- 秒杀下单和订单查询尚未实现。店铺详情页只展示优惠券，抢购按钮保持禁用，不会调用未完成的订单接口。
- 早期博客若只有旧 `images` 路径、没有 `tb_blog_image` 资产 ID，编辑器无法可靠判断图片所有权；编辑这类博客时需要重新上传图片。新发布博客不受影响。

> 图片资源已从旧前端 `src/main/resources/nginx-1.18.0/html/hmdp/imgs` 复制到 `public/imgs`，数据库中的 `/imgs/blogs/...` 路径可以直接显示。

## 本地开发

1. 启动后端，默认地址为 `http://localhost:9090`。
2. 安装依赖并启动前端：

```bash
cd hmdp/frontend
pnpm install
pnpm dev
```

没有 pnpm 时也可以使用 `npm install` 和 `npm run dev`。前端默认地址为 `http://localhost:3000`。

## 代理与环境变量

前端默认使用 `/api` 作为后端前缀，由 Nuxt 代理转发：

- `NUXT_PUBLIC_API_BASE`：默认 `/api`
- `NUXT_DEV_PROXY_TARGET`：默认 `http://localhost:9090`

需要覆盖默认值时，复制示例文件：

```bash
cd hmdp/frontend
copy .env.example .env
```
