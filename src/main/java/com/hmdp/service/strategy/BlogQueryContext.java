package com.hmdp.service.strategy;

import java.util.Map;

public class BlogQueryContext {
    Long userId;
    Integer current;
    Integer pageSize;
    String scene; // hot/recommend/follow
    Map<String, Object> features; // 未来模型特征
}
