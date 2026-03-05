package com.hmdp.dto;

import com.hmdp.service.strategy.BlogQueryContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class FollowFeedQueryResult extends ScrollResult {
    private BlogQueryContext context;
    private String route;
    private List<Long> orderedBlogIds = Collections.emptyList();
    private List<Long> sortScores = Collections.emptyList();
}
