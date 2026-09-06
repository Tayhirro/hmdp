CREATE TABLE IF NOT EXISTS `tb_idempotency_record` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '幂等记录ID',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '请求用户ID',
  `request_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '客户端幂等键',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化请求摘要',
  `resource_type` varchar(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '资源类型',
  `resource_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '首次创建的资源ID',
  `response_data` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '首次成功响应快照',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'PROCESSING/SUCCEEDED',
  `owner_token` char(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次创建事务所有者',
  `expire_time` timestamp NOT NULL COMMENT '幂等保留截止时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_idempotency_user_key` (`user_id`, `request_key`) USING BTREE,
  KEY `idx_idempotency_expire` (`expire_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '跨资源生命周期幂等记录';

INSERT INTO `tb_idempotency_record`
  (`user_id`, `request_key`, `request_hash`, `resource_type`, `resource_id`,
   `response_data`, `status`, `owner_token`, `expire_time`)
SELECT `user_id`, `client_request_id`, `request_hash`, 'BLOG', `id`,
       CAST(`id` AS CHAR), 'SUCCEEDED', 'migration', DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY)
FROM `tb_blog`
WHERE `client_request_id` IS NOT NULL AND `request_hash` IS NOT NULL;

ALTER TABLE `tb_blog`
  DROP INDEX `uk_blog_user_request`,
  DROP COLUMN `request_hash`,
  DROP COLUMN `client_request_id`;
