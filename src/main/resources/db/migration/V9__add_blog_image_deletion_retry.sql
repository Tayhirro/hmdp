ALTER TABLE `tb_blog_image`
  ADD COLUMN `retry_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '物理删除失败次数' AFTER `bind_time`,
  ADD COLUMN `last_error` varchar(1000) NULL DEFAULT NULL COMMENT '最后一次删除错误摘要' AFTER `retry_count`,
  ADD COLUMN `next_retry_time` timestamp NULL DEFAULT NULL COMMENT '下次允许重试时间' AFTER `last_error`,
  ADD KEY `idx_blog_image_deleting_retry` (`status`, `next_retry_time`, `id`) USING BTREE;

UPDATE `tb_blog_image`
SET `next_retry_time` = CURRENT_TIMESTAMP
WHERE `status` = 'DELETING' AND `next_retry_time` IS NULL;
