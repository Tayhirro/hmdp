package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 幂等记录的数据访问接口。
 *
 * 这里的 SQL 不负责创建博客，只负责保证多个相同请求中只有一个能取得创建资格，
 * 并保存第一次请求的最终结果。
 *
 * 全部方法的调用方是 BlogIdempotencyService（发布博客的幂等控制）；
 * 其中批量清理方法由 IdempotencyCleanupJob 定时任务间接触发。
 */
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

    /**
     * 尝试插入请求记录。
     *
     * 第一次请求会插入新行；相同“用户 ID + requestKey”已经存在时，SQL 不修改原内容，
     * 只通过 {@code LAST_INSERT_ID(id)} 把原记录 ID 放回 {@code record.id}。
     * 因此调用结束后，无论谁先到达，都能继续查询同一条记录。
     *
     * 使用场景：BlogIdempotencyService.begin，发布博客前取得创建资格。
     */
    @Insert("INSERT INTO tb_idempotency_record " +
            "(user_id, request_key, request_hash, resource_type, status, owner_token, expire_time) " +
            "VALUES (#{userId}, #{requestKey}, #{requestHash}, #{resourceType}, #{status}, " +
            "#{ownerToken}, #{expireTime}) " +
            "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertOrGetId(IdempotencyRecord record);

    /**
     * 查询并锁住请求记录，直到当前事务结束；相同请求会按顺序判断，不能同时创建博客。
     * SQL：SELECT tb_idempotency_record 整行，WHERE id 等于参数，并附带 FOR UPDATE 行锁。
     *
     * 使用场景：BlogIdempotencyService.begin，插入后回读记录并按 requestHash、status、ownerToken 判定本次走向。
     */
    @Select("SELECT * FROM tb_idempotency_record WHERE id = #{id} FOR UPDATE")
    IdempotencyRecord selectByIdForUpdate(@Param("id") Long id);

    /**
     * 保存第一次创建的博客 ID 并标记成功。
     * 只有记录仍在处理中且 ownerToken 属于当前请求时才能更新一行。
     * SQL：SET resource_id、response_data 为参数值并把 status 更新为 SUCCEEDED，
     * WHERE id 等于参数且 owner_token 等于参数且 status 为 PROCESSING。
     *
     * 使用场景：BlogIdempotencyService.complete，博客创建成功后完成幂等记录。
     */
    @Update("UPDATE tb_idempotency_record " +
            "SET resource_id = #{resourceId}, response_data = #{responseData}, status = 'SUCCEEDED' " +
            "WHERE id = #{id} AND owner_token = #{ownerToken} AND status = 'PROCESSING'")
    int markSucceeded(
            @Param("id") Long id,
            @Param("ownerToken") String ownerToken,
            @Param("resourceId") Long resourceId,
            @Param("responseData") String responseData
    );

    /** 当前 key 已超过保留期时先删除它，使客户端以后可以重新使用该 key。
     * SQL：条件为 user_id、request_key 等于参数且 expire_time 不晚于参数 now。
     *
     * 使用场景：BlogIdempotencyService.begin，每次取创建资格前先清理同 key 的过期记录。
     */
    @Delete("DELETE FROM tb_idempotency_record " +
            "WHERE user_id = #{userId} AND request_key = #{requestKey} " +
            "AND expire_time <= #{now}")
    int deleteExpiredKey(
            @Param("userId") Long userId,
            @Param("requestKey") String requestKey,
            @Param("now") LocalDateTime now
    );

    /** 定时任务按批次清理所有过期记录，防止一次删除太多行。
     * SQL：条件为 expire_time 不晚于参数 now，并按 LIMIT 参数限制单次删除行数。
     *
     * 使用场景：BlogIdempotencyService.cleanupExpired，由定时任务 IdempotencyCleanupJob
     * 按 fixedDelay 默认每 24 小时触发一轮，每轮只调用一次（每批 500 条）；
     * 本轮未删完的过期记录留给下一轮继续清理。
     */
    @Delete("DELETE FROM tb_idempotency_record WHERE expire_time <= #{now} LIMIT #{limit}")
    int deleteExpiredBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
