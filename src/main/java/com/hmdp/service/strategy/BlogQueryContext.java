package com.hmdp.service.strategy;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;


@Data
public class BlogQueryContext {
    private Long userId;
    private Integer current;
    private Integer pageSize;
    private String scene; // hot/recommend/follow
    private Map<String, Object> features = new HashMap<>(); // 未来模型特征
}
