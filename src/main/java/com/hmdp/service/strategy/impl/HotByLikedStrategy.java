package com.hmdp.service.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.strategy.BlogQueryContext;
import com.hmdp.service.strategy.BlogRankStrategy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class HotByLikedStrategy implements BlogRankStrategy {
    @Resource
    private BlogMapper blogMapper;
    @Override
    public String scene() {
        return "hot";
    }

    @Override
    public Page<Long> rank(BlogQueryContext ctx) {
        long current = (ctx == null || ctx.getCurrent() == null || ctx.getCurrent() < 1) ? 1L : ctx.getCurrent();
        long size = (ctx == null || ctx.getPageSize() == null || ctx.getPageSize() < 1) ? 10L : ctx.getPageSize();

        QueryWrapper<Blog> wrapper = new QueryWrapper<>();
        wrapper.select("id").orderByDesc("liked");
        Page<Blog> blogPage = blogMapper.selectPage(new Page<>(current, size), wrapper);

        Page<Long> idPage = new Page<>(blogPage.getCurrent(), blogPage.getSize(), blogPage.getTotal());
        List<Long> ids = blogPage.getRecords() == null
                ? Collections.emptyList()
                : blogPage.getRecords().stream().map(Blog::getId).filter(Objects::nonNull).collect(Collectors.toList());
        idPage.setRecords(ids);
        return idPage;
    }
}
