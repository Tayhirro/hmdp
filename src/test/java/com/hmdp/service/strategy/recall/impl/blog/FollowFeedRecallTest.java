package com.hmdp.service.strategy.recall.impl.blog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.feedcache.FollowCacheService;
import com.hmdp.service.strategy.recall.RecallContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowFeedRecallTest {

    @Mock
    private IFollowService followService;

    @Mock
    private BlogMapper blogMapper;

    @Mock
    private FollowCacheService followCacheService;

    @InjectMocks
    private FollowFeedRecall followFeedRecall;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void recall_should_use_id_tiebreaker_when_cursor_has_same_create_time() {
        LocalDateTime cursorTime = LocalDateTime.of(2026, 6, 13, 10, 0);
        long maxTime = cursorTime.toInstant(ZoneOffset.UTC).toEpochMilli();
        RecallContext ctx = RecallContext.builder()
                .userId(1L)
                .maxTime(maxTime)
                .limit(10)
                .extra(Collections.singletonMap("lastId", 20L))
                .build();

        when(followCacheService.getFollowedIds(eq(1L), any())).thenReturn(Collections.singletonList(2L));
        Blog blog = new Blog().setId(19L);
        when(blogMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(blog));

        List<Long> ids = followFeedRecall.recall(ctx);

        assertEquals(Collections.singletonList(19L), ids);
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(blogMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("create_time <"));
        assertTrue(sql.contains("create_time ="));
        assertTrue(sql.contains("id <"));
        assertTrue(sql.contains("order by create_time desc,id desc"));
    }
}
