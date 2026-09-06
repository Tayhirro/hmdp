package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.follow.FollowChangedEvent;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private IUserService userService;

    @Mock
    private FollowMapper followMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FollowServiceImpl followService;

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void follow_should_use_idempotent_insert_and_publish_event() {
        saveCurrentUser(1L);
        when(followMapper.insertIfAbsent(1L, 2L)).thenReturn(1);

        Result result = followService.follow(2L, true);

        assertTrue(result.getSuccess());
        verify(followMapper).insertIfAbsent(1L, 2L);
        FollowChangedEvent event = capturePublishedEvent();
        assertEquals(1L, event.getUserId());
        assertEquals(2L, event.getFollowUserId());
        assertTrue(event.isFollowed());
    }

    @Test
    void repeatedFollow_should_remain_success_when_relation_already_exists() {
        saveCurrentUser(1L);
        when(followMapper.insertIfAbsent(1L, 2L)).thenReturn(0);

        Result result = followService.follow(2L, true);

        assertTrue(result.getSuccess());
        verify(followMapper).insertIfAbsent(1L, 2L);
        assertTrue(capturePublishedEvent().isFollowed());
    }

    @Test
    void repeatedUnfollow_should_remain_success_when_relation_doesNotExist() {
        saveCurrentUser(1L);
        when(followMapper.deleteRelation(1L, 2L)).thenReturn(0);

        Result result = followService.follow(2L, false);

        assertTrue(result.getSuccess());
        verify(followMapper).deleteRelation(1L, 2L);
        assertFalse(capturePublishedEvent().isFollowed());
    }

    @Test
    void follow_should_reject_selfFollow_without_writing_database() {
        saveCurrentUser(1L);

        Result result = followService.follow(1L, true);

        assertFalse(result.getSuccess());
        assertEquals("不能关注自己", result.getErrorMsg());
        verify(followMapper, never()).insertIfAbsent(1L, 1L);
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private void saveCurrentUser(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        UserHolder.saveUser(user);
    }

    private FollowChangedEvent capturePublishedEvent() {
        ArgumentCaptor<FollowChangedEvent> captor = ArgumentCaptor.forClass(FollowChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
