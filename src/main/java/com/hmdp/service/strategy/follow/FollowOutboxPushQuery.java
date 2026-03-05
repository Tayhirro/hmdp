package com.hmdp.service.strategy.follow;

import com.hmdp.dto.FollowFeedQueryResult;

public interface FollowOutboxPushQuery {
    FollowFeedQueryResult query(FollowFeedQueryRequest request);
}
