CREATE TABLE IF NOT EXISTS `tb_blog_like` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` BIGINT UNSIGNED NOT NULL COMMENT '博客ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '点赞用户ID',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_blog_user` (`blog_id`, `user_id`) USING BTREE,
  KEY `idx_blog_time` (`blog_id`, `create_time`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='博客点赞关系表';
