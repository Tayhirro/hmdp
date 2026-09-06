package com.hmdp.service.follow;

import com.hmdp.service.feedcache.FeedCacheService;
import com.hmdp.service.feedcache.FollowCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowChangedEventListenerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private FollowCacheService followCacheService;

    @Mock
    private FeedCacheService feedCacheService;

    @InjectMocks
    private FollowChangedEventListener listener;

    @Test
    void followEvent_should_updateRedisAndInvalidateCaches() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        listener.handleFollowChanged(new FollowChangedEvent(1L, 2L, true));

        verify(setOperations).add("follow:1", "2");
        verify(followCacheService).invalidate(1L);
        verify(feedCacheService).invalidate(1L, "following");
        verify(feedCacheService).invalidate(1L, "for_you");
    }

    @Test
    void unfollowEvent_should_removeRedisMember() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        listener.handleFollowChanged(new FollowChangedEvent(1L, 2L, false));

        verify(setOperations).remove("follow:1", "2");
        verify(followCacheService).invalidate(1L);
        verify(feedCacheService).invalidate(1L, "following");
        verify(feedCacheService).invalidate(1L, "for_you");
    }
}
