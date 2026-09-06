package com.hmdp.dto;

import lombok.Data;

/**
 * 当前用户与博客作者之间的历史互动统计。
 *
 * 类别：服务端内部查询投影，不是前端请求或响应模型。
 * 来源：{@code BlogLikeMapper.selectAuthorInteractions} 按作者聚合当前用户的点赞记录。
 * 用途：Feed 推荐召回根据互动次数识别用户更感兴趣的作者。
 */
@Data
public class AuthorInteractionDTO {

    /** 被互动博客的作者用户 ID。 */
    private Long authorId;

    /** 当前用户给该作者博客点过赞的累计次数。 */
    private Integer interactionCount;
}
