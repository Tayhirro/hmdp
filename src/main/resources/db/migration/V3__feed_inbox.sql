CREATE TABLE IF NOT EXISTS `tb_feed_inbox` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `recipient_id` BIGINT UNSIGNED NOT NULL COMMENT '收件人用户ID',
  `blog_id` BIGINT UNSIGNED NOT NULL COMMENT '博客ID',
  `score` BIGINT NOT NULL COMMENT '时间戳分数',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_recipient_blog` (`recipient_id`, `blog_id`) USING BTREE,
  KEY `idx_recipient_score` (`recipient_id`, `score`) USING BTREE,
  KEY `idx_blog` (`blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户关注流收件箱';
