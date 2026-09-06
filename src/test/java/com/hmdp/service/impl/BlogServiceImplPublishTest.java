package com.hmdp.service.impl;

import com.hmdp.dto.BlogPublishRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogImage;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogImageService;
import com.hmdp.service.IShopService;
import com.hmdp.service.blog.BlogCommandService;
import com.hmdp.service.blog.BlogIdempotencyService;
import com.hmdp.service.blog.IdempotencyDecision;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogServiceImplPublishTest {

    @Mock private BlogMapper blogMapper;
    @Mock private BlogLikeMapper blogLikeMapper;
    @Mock private BlogCommentsMapper blogCommentsMapper;
    @Mock private IShopService shopService;
    @Mock private IBlogImageService blogImageService;
    @Mock private BlogIdempotencyService idempotencyService;

    @InjectMocks
    private BlogCommandService blogService;

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void publish_should_store_plainText_bind_images_and_complete_idempotency() {
        login(7L);
        BlogPublishRequest request = request("publish_123", "  周末探店  ",
                "好吃<script>alert(1)</script>\n环境很好");
        IdempotencyDecision createDecision = IdempotencyDecision.createBlog(5L, "owner");

        when(idempotencyService.begin(eq(7L), eq("publish_123"), anyString()))
                .thenReturn(createDecision);
        when(shopService.getById(10L)).thenReturn(new Shop().setId(10L));
        when(blogImageService.loadOwnedTemporaryImages(request.getImageIds(), 7L)).thenReturn(Arrays.asList(
                temporaryImage(2L, "/imgs/second.png"),
                temporaryImage(1L, "/imgs/first.png")
        ));
        doAnswer(invocation -> {
            Blog blog = invocation.getArgument(0);
            blog.setId(99L);
            return 1;
        }).when(blogMapper).insert(any(Blog.class));

        Result result = blogService.publish(request);

        assertTrue(result.getSuccess());
        assertEquals(99L, result.getData());
        ArgumentCaptor<Blog> captor = ArgumentCaptor.forClass(Blog.class);
        verify(blogMapper).insert(captor.capture());
        assertEquals("周末探店", captor.getValue().getTitle());
        assertEquals("好吃<script>alert(1)</script>\n环境很好", captor.getValue().getContent());
        assertEquals("/imgs/second.png,/imgs/first.png", captor.getValue().getImages());
        verify(blogImageService).bindToBlog(request.getImageIds(), 7L, 99L);
        verify(idempotencyService).complete(createDecision, 99L);
    }

    @Test
    void publish_repeated_request_should_return_first_id_before_shop_and_image_validation() {
        login(7L);
        BlogPublishRequest request = request("publish_repeated", "版本 A", "首次正文");
        when(idempotencyService.begin(eq(7L), eq("publish_repeated"), anyString()))
                .thenReturn(IdempotencyDecision.returnPreviousResult(5L, 99L));

        Result result = blogService.publish(request);

        assertEquals(99L, result.getData());
        verify(shopService, never()).getById(any());
        verify(blogImageService, never()).loadOwnedTemporaryImages(any(), any());
        verify(blogMapper, never()).insert(any(Blog.class));
    }

    @Test
    void publish_should_return_401_when_user_is_missing() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> blogService.publish(request("key", "标题", "正文")));

        assertEquals(401, error.getStatus().value());
        assertEquals("AUTH_REQUIRED", error.getCode());
    }

    private BlogPublishRequest request(String key, String title, String content) {
        BlogPublishRequest request = new BlogPublishRequest();
        request.setClientRequestId(key);
        request.setShopId(10L);
        request.setTitle(title);
        request.setContent(content);
        request.setImageIds(Arrays.asList(2L, 1L));
        return request;
    }

    private BlogImage temporaryImage(Long id, String url) {
        return new BlogImage()
                .setId(id)
                .setUserId(7L)
                .setPublicUrl(url)
                .setStatus(BlogImage.STATUS_TEMP);
    }

    private void login(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        UserHolder.saveUser(user);
    }
}
