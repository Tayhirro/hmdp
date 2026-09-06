package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hmdp.dto.BlogCommentCreateRequest;
import com.hmdp.dto.CursorPageDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.cursor.CursorCodec;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogCommentsServiceImplTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                BlogComments.class
        );
    }

    @Mock private BlogCommentsMapper commentsMapper;
    @Mock private BlogMapper blogMapper;
    @Mock private IUserService userService;
    @Mock private CursorCodec cursorCodec;

    @InjectMocks
    private BlogCommentsServiceImpl commentsService;

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void createComment_should_use_login_user_and_increment_blog_count() {
        login(7L);
        when(blogMapper.selectById(3L)).thenReturn(new Blog().setId(3L));
        when(commentsMapper.insert(any(BlogComments.class))).thenAnswer(invocation -> {
            invocation.<BlogComments>getArgument(0).setId(11L);
            return 1;
        });
        when(blogMapper.incrementComments(3L)).thenReturn(1);

        BlogCommentCreateRequest request = new BlogCommentCreateRequest();
        request.setBlogId(3L);
        request.setContent("  真不错  ");
        Result result = commentsService.createComment(request);

        assertTrue(result.getSuccess());
        assertEquals(11L, result.getData());
        ArgumentCaptor<BlogComments> captor = ArgumentCaptor.forClass(BlogComments.class);
        verify(commentsMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("真不错", captor.getValue().getContent());
        assertEquals(0L, captor.getValue().getParentId());
        verify(blogMapper).incrementComments(3L);
    }

    @Test
    void queryComments_should_return_standard_empty_cursor_page() {
        when(blogMapper.selectById(3L)).thenReturn(new Blog().setId(3L));
        when(cursorCodec.decode(null, "blog-comment-v1")).thenReturn(null);
        when(commentsMapper.selectList(any())).thenReturn(Collections.emptyList());

        Result result = commentsService.queryComments(3L, null, 20);

        CursorPageDTO<?> page = (CursorPageDTO<?>) result.getData();
        assertTrue(result.getSuccess());
        assertEquals(Collections.emptyList(), page.getList());
        assertEquals(false, page.getHasMore());
    }

    @Test
    void deleteTopLevelComment_should_delete_replies_and_decrement_actual_count() {
        login(7L);
        BlogComments existing = new BlogComments()
                .setId(11L)
                .setBlogId(3L)
                .setUserId(7L)
                .setParentId(0L)
                .setStatus(0);
        when(commentsMapper.selectById(11L)).thenReturn(existing);
        when(commentsMapper.delete(any())).thenReturn(2, 1);
        when(blogMapper.decrementComments(3L, 3)).thenReturn(1);

        Result result = commentsService.deleteComment(11L);

        assertTrue(result.getSuccess());
        verify(commentsMapper, times(2)).delete(any());
        verify(blogMapper).decrementComments(3L, 3);
    }

    private void login(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        UserHolder.saveUser(user);
    }
}
