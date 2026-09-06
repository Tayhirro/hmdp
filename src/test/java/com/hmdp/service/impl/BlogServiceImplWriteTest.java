package com.hmdp.service.impl;

import com.hmdp.dto.BlogUpdateRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogImage;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogImageService;
import com.hmdp.service.IShopService;
import com.hmdp.service.blog.BlogCommandService;
import com.hmdp.service.blog.BlogIdempotencyService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogServiceImplWriteTest {

    @Mock private BlogMapper blogMapper;
    @Mock private BlogLikeMapper blogLikeMapper;
    @Mock private BlogCommentsMapper blogCommentsMapper;
    @Mock private IShopService shopService;
    @Mock private IBlogImageService blogImageService;
    @Mock private BlogIdempotencyService idempotencyService;

    @InjectMocks
    private BlogCommandService blogService;

    @BeforeEach
    void setUpUser() {
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void update_should_validate_shop_before_lock_and_use_field_whitelist() {
        Blog existing = new Blog().setId(9L).setUserId(7L);
        BlogUpdateRequest request = request();
        BlogImage retained = new BlogImage()
                .setId(11L)
                .setUserId(7L)
                .setBlogId(9L)
                .setStatus(BlogImage.STATUS_BOUND)
                .setPublicUrl("/imgs/11.jpg");

        when(shopService.getById(10L)).thenReturn(new Shop().setId(10L));
        when(blogMapper.selectByIdForUpdate(9L)).thenReturn(existing);
        when(blogImageService.replaceBlogImages(request.getImageIds(), 7L, 9L))
                .thenReturn(Collections.emptyList());
        when(blogImageService.loadOwnedBlogImages(request.getImageIds(), 7L, 9L))
                .thenReturn(Collections.singletonList(retained));
        when(blogMapper.updateEditableFields(
                9L, 7L, 10L, "更新标题", "更新正文", "/imgs/11.jpg"))
                .thenReturn(1);

        Result result = blogService.update(9L, request);

        assertTrue(result.getSuccess());
        assertEquals(9L, result.getData());
        InOrder order = inOrder(shopService, blogMapper, blogImageService);
        order.verify(shopService).getById(10L);
        order.verify(blogMapper).selectByIdForUpdate(9L);
        order.verify(blogImageService).replaceBlogImages(request.getImageIds(), 7L, 9L);
        verify(blogImageService).schedulePhysicalDeletionAfterCommit(Collections.emptyList());
        verify(blogMapper).updateEditableFields(
                9L, 7L, 10L, "更新标题", "更新正文", "/imgs/11.jpg");
    }

    @Test
    void delete_should_preserve_idempotency_record_and_delete_blog_relations() {
        Blog existing = new Blog().setId(9L).setUserId(7L);
        BlogImage image = new BlogImage().setId(11L).setStatus(BlogImage.STATUS_DELETING);
        when(blogMapper.selectByIdForUpdate(9L)).thenReturn(existing);
        when(blogImageService.detachAllBoundImages(7L, 9L))
                .thenReturn(Collections.singletonList(image));
        when(blogMapper.deleteById(9L)).thenReturn(1);

        Result result = blogService.delete(9L);

        assertTrue(result.getSuccess());
        InOrder order = inOrder(blogMapper, blogImageService, blogLikeMapper, blogCommentsMapper);
        order.verify(blogMapper).selectByIdForUpdate(9L);
        order.verify(blogImageService).detachAllBoundImages(7L, 9L);
        order.verify(blogLikeMapper).delete(any());
        order.verify(blogCommentsMapper).delete(any());
        order.verify(blogMapper).deleteById(9L);
        order.verify(blogImageService).schedulePhysicalDeletionAfterCommit(Collections.singletonList(image));
    }

    private BlogUpdateRequest request() {
        BlogUpdateRequest request = new BlogUpdateRequest();
        request.setShopId(10L);
        request.setTitle("更新标题");
        request.setContent("更新正文");
        request.setImageIds(Collections.singletonList(11L));
        return request;
    }
}
