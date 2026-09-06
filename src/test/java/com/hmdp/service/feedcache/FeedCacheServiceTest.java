package com.hmdp.service.feedcache;

import com.hmdp.entity.Blog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FeedCacheService feedCacheService;

    @Test
    void getPage_should_read_requested_snapshot_by_offset() {
        String key = "feed:cache:1:following:v2:snapshot:abc";
        when(stringRedisTemplate.hasKey(key)).thenReturn(true);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(key, 3, 5))
                .thenReturn(Arrays.asList("3|300", "2|200", "1|100"));

        FeedCachePage page = feedCacheService.getPage(1L, "following", "abc", 2, 3);

        assertTrue(page.isAvailable());
        assertEquals("abc", page.getSnapshotId());
        assertEquals(Arrays.asList(3L, 2L, 1L), page.getEntries().stream()
                .map(FeedCacheEntry::getBlogId).collect(java.util.stream.Collectors.toList()));
        verify(stringRedisTemplate).expire(eq(key), anyLong(), any());
    }

    @Test
    void getPage_should_report_unavailable_when_pointer_is_missing() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("feed:cache:1:following:v2:current")).thenReturn(null);

        FeedCachePage page = feedCacheService.getPage(1L, "following", null, 0, 3);

        assertFalse(page.isAvailable());
    }

    @Test
    void cacheFeed_should_create_unique_snapshot() {
        List<Blog> rankedBlogs = Arrays.asList(
                new Blog().setId(9L),
                new Blog().setId(10L),
                new Blog().setId(2L));

        String snapshotId = feedCacheService.cacheFeed(1L, "following", rankedBlogs);

        assertNotNull(snapshotId);
        verify(stringRedisTemplate).execute(
                any(), any(), eq("300"), eq(snapshotId), eq("__snapshot__"),
                eq("9|0"), eq("10|0"), eq("2|0"));
    }
}
