ALTER TABLE `tb_blog`
  ADD COLUMN `client_request_id` varchar(64) NULL DEFAULT NULL COMMENT '客户端发布幂等键' AFTER `user_id`,
  ADD COLUMN `request_hash` char(64) NULL DEFAULT NULL COMMENT '发布请求内容摘要' AFTER `client_request_id`,
  ADD UNIQUE KEY `uk_blog_user_request` (`user_id`, `client_request_id`) USING BTREE,
  ADD KEY `idx_blog_hot_cursor` (`liked`, `id`) USING BTREE,
  DROP INDEX `idx_blog_user_time`,
  ADD KEY `idx_blog_user_cursor` (`user_id`, `create_time`, `id`) USING BTREE;

ALTER TABLE `tb_blog_like`
  ADD KEY `idx_blog_like_cursor_cover` (`blog_id`, `create_time`, `id`, `user_id`) USING BTREE;
