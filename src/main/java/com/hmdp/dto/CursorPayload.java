package com.hmdp.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页游标在服务端内部使用的载荷。
 *
 * 类别：内部协议 DTO，由 {@code CursorCodec} 编码和解码，不是前端请求体。
 * 普通列表使用 {@code type + score + id} 做稳定的键集分页；个性化 Feed 使用
 * {@code snapshotId + offset} 读取同一份候选快照，并用边界字段支持快照失效后的降级续读。
 * 编码结果只是 Base64URL 封装，不具备保密或防篡改能力，因此服务端仍会校验类型和取值；
 * 客户端必须把整个 cursor 当作不透明字符串原样回传。
 */
@Data
@NoArgsConstructor
public class CursorPayload {

    /** 游标所属的接口/排序类型，用于阻止不同列表之间误用游标。 */
    private String type;

    /** 键集分页的上一页末尾排序值，例如发布时间或点赞时间的 UTC epoch 值。 */
    private Long score;

    /** 键集分页的上一页末尾记录 ID；排序值相同时用它稳定区分先后。 */
    private Long id;

    /** 个性化 Feed 候选快照 ID，用于后续页继续读取同一份排序结果。 */
    private String snapshotId;

    /** 下一页在 Feed 快照中的起始下标。 */
    private Integer offset;

    /** Feed 快照失效时，数据库降级查询使用的上一页末尾排序值。 */
    private Long boundaryScore;

    /** Feed 快照失效时，与 {@link #boundaryScore} 共同确定续读位置的博客 ID。 */
    private Long boundaryId;
}
