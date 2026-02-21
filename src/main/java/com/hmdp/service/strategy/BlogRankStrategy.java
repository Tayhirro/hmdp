package com.hmdp.service.strategy;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface BlogRankStrategy {
    String scene();

    Page<Long> rank(BlogQueryContext ctx);
}
