-- 清理历史重复关注记录，只保留每组关系中 id 最小的一条。
DELETE duplicate_follow
FROM `tb_follow` AS duplicate_follow
INNER JOIN `tb_follow` AS retained_follow
        ON duplicate_follow.`user_id` = retained_follow.`user_id`
       AND duplicate_follow.`follow_user_id` = retained_follow.`follow_user_id`
       AND duplicate_follow.`id` > retained_follow.`id`;

-- 从数据库层保证同一用户不能重复关注同一目标用户。
ALTER TABLE `tb_follow`
    ADD UNIQUE KEY `uk_follow_user_follow_user` (`user_id`, `follow_user_id`) USING BTREE;
