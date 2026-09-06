package com.hmdp.service.feedcache;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FollowCacheServiceTest {

    @Test
    void invalidate_should_reload_follow_ids() {
        FollowCacheService followCacheService = new FollowCacheService();
        AtomicInteger loadCount = new AtomicInteger();

        assertEquals(Collections.singletonList(11L),
                followCacheService.getFollowedIds(10L, id -> Collections.singletonList(id + loadCount.incrementAndGet())));
        assertEquals(Collections.singletonList(11L),
                followCacheService.getFollowedIds(10L, id -> Collections.singletonList(id + loadCount.incrementAndGet())));

        followCacheService.invalidate(10L);

        assertEquals(Collections.singletonList(12L),
                followCacheService.getFollowedIds(10L, id -> Collections.singletonList(id + loadCount.incrementAndGet())));
        assertEquals(2, loadCount.get());
    }
}
