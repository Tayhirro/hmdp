package com.hmdp.service.strategy.ranking.impl;

import com.hmdp.entity.Blog;
import com.hmdp.service.strategy.ranking.RankingContext;
import com.hmdp.service.strategy.ranking.RankingStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingStrategyOrderTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
    private final RankingContext context = RankingContext.builder()
            .now(now)
            .authorAffinity(Collections.emptyMap())
            .build();

    @Test
    void simpleRanking_should_sort_by_score_descending() {
        Blog lowScore = blog(1L, 0);
        Blog highScore = blog(2L, 100);

        List<Blog> ranked = new SimpleRankingStrategy()
                .rank(new ArrayList<>(Arrays.asList(lowScore, highScore)), context);

        assertEquals(Arrays.asList(2L, 1L), ids(ranked));
    }

    @Test
    void allRankingStrategies_should_use_numeric_id_as_tiebreaker() {
        List<RankingStrategy<Blog>> strategies = Arrays.asList(
                new SimpleTimeRankingStrategy(),
                new SimpleRankingStrategy(),
                new WeightedRankingStrategy());

        for (RankingStrategy<Blog> strategy : strategies) {
            List<Blog> blogs = new ArrayList<>(Arrays.asList(blog(9L, 10), blog(10L, 10)));

            assertEquals(Arrays.asList(10L, 9L), ids(strategy.rank(blogs, context)),
                    strategy.getStrategyName());
        }
    }

    private Blog blog(Long id, int liked) {
        return new Blog()
                .setId(id)
                .setUserId(1L)
                .setLiked(liked)
                .setComments(0)
                .setCreateTime(now.minusHours(1));
    }

    private List<Long> ids(List<Blog> blogs) {
        return blogs.stream().map(Blog::getId).collect(Collectors.toList());
    }
}
