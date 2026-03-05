package com.hmdp.service.strategy.follow;

import com.hmdp.dto.FollowFeedQueryResult;

public interface FollowInboxPullQuery {
    FollowFeedQueryResult query(FollowFeedQueryRequest request);
}
