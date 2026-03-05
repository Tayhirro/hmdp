package com.hmdp.service.strategy.follow;

import com.hmdp.service.strategy.BlogQueryContext;
import lombok.Data;

@Data
public class FollowFeedQueryRequest {
    private Long userId;
    private Long maxScore;
    private Integer offset;
    private Integer pageSize;
    private BlogQueryContext context;
}
