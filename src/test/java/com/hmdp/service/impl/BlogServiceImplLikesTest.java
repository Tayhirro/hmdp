package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hmdp.dto.BlogLikeStateDTO;
import com.hmdp.dto.CursorPageDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogLike;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.blog.BlogLikeService;
import com.hmdp.service.cursor.CursorCodec;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogServiceImplLikesTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                BlogLike.class
        );
    }

    @Mock private BlogMapper blogMapper;
    @Mock private BlogLikeMapper blogLikeMapper;
    @Mock private IUserService userService;
    @Mock private CursorCodec cursorCodec;

    @InjectMocks
    private BlogLikeService blogService;

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void queryBlogLikes_should_use_database_as_authoritative_source() {
        when(blogMapper.selectById(1L)).thenReturn(new Blog().setId(1L));
        when(blogLikeMapper.selectList(any())).thenReturn(Collections.emptyList());

        Result result = blogService.queryUsers(1L, null, null);

        assertTrue(result.getSuccess());
        CursorPageDTO<?> data = (CursorPageDTO<?>) result.getData();
        assertEquals(Collections.emptyList(), data.getList());
        verify(blogLikeMapper).selectList(any());
    }

    @Test
    void like_should_treat_existing_relation_as_idempotent_success() {
        login(2L);
        when(blogMapper.selectById(1L)).thenReturn(new Blog().setId(1L).setLiked(7));
        when(blogLikeMapper.insertRelation(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(2L),
                any(LocalDateTime.class)
        )).thenThrow(new DuplicateKeyException("duplicate relation"));
        when(blogLikeMapper.selectOne(any())).thenReturn(new BlogLike().setId(9L));

        Result result = blogService.like(1L);

        BlogLikeStateDTO state = (BlogLikeStateDTO) result.getData();
        assertTrue(state.getLiked());
        assertEquals(7, state.getLikeCount());
        verify(blogLikeMapper).insertRelation(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(2L),
                any(LocalDateTime.class));
    }

    @Test
    void unlike_should_treat_missing_relation_as_idempotent_success() {
        login(2L);
        when(blogMapper.selectById(1L)).thenReturn(new Blog().setId(1L).setLiked(6));
        when(blogLikeMapper.deleteRelation(1L, 2L)).thenReturn(0);

        Result result = blogService.unlike(1L);

        BlogLikeStateDTO state = (BlogLikeStateDTO) result.getData();
        assertEquals(false, state.getLiked());
        assertEquals(6, state.getLikeCount());
        verify(blogLikeMapper).deleteRelation(1L, 2L);
    }

    private void login(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        UserHolder.saveUser(user);
    }
}
