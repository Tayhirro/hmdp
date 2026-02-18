-- 清理历史重复关系：保留最小 id
DELETE t1
FROM tb_follow t1
INNER JOIN tb_follow t2
ON t1.user_id = t2.user_id
AND t1.follow_user_id = t2.follow_user_id
AND t1.id > t2.id;

SET @db_name := DATABASE();

SET @uk_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'tb_follow'
      AND index_name = 'uk_tb_follow_user_follow'
);
SET @sql := IF(
    @uk_exists = 0,
    'ALTER TABLE tb_follow ADD UNIQUE INDEX uk_tb_follow_user_follow (user_id, follow_user_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @db_name
      AND table_name = 'tb_follow'
      AND index_name = 'idx_tb_follow_follow_user_id'
);
SET @sql := IF(
    @idx_exists = 0,
    'ALTER TABLE tb_follow ADD INDEX idx_tb_follow_follow_user_id (follow_user_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
