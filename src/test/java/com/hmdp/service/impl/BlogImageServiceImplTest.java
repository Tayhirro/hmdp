package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hmdp.config.BlogImageProperties;
import com.hmdp.dto.BlogImageUploadDTO;
import com.hmdp.entity.BlogImage;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogImageMapper;
import com.hmdp.service.storage.BlogImageStorage;
import com.hmdp.service.storage.StoredBlogImage;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogImageServiceImplTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                BlogImage.class
        );
    }

    @Mock
    private BlogImageMapper blogImageMapper;

    @Mock
    private BlogImageStorage storage;

    private BlogImageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BlogImageServiceImpl(blogImageMapper, storage, new BlogImageProperties());
    }

    @Test
    void upload_should_store_file_and_create_temporary_asset() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});
        when(storage.store(file)).thenReturn(
                new StoredBlogImage("blogs/photo.png", "/imgs/blogs/photo.png", "image/png", 1L, 10, 20)
        );
        doAnswer(invocation -> {
            BlogImage image = invocation.getArgument(0);
            image.setId(9L);
            return 1;
        }).when(blogImageMapper).insert(any(BlogImage.class));

        BlogImageUploadDTO result = service.upload(file, 7L);

        assertEquals(9L, result.getId());
        assertEquals("/imgs/blogs/photo.png", result.getUrl());
    }

    @Test
    void deleteTemporaryImage_should_reject_different_owner() {
        when(blogImageMapper.selectById(9L)).thenReturn(new BlogImage()
                .setId(9L)
                .setUserId(8L)
                .setStatus(BlogImage.STATUS_TEMP)
                .setStorageKey("blogs/photo.png"));

        assertThrows(BusinessException.class, () -> service.deleteTemporaryImage(9L, 7L));

        verify(storage, never()).delete(any());
    }

    @Test
    void loadOwnedTemporaryImages_should_preserve_client_order() {
        BlogImage first = temporaryImage(1L, 7L, "/imgs/first.png");
        BlogImage second = temporaryImage(2L, 7L, "/imgs/second.png");
        when(blogImageMapper.selectBatchIds(any())).thenReturn(Arrays.asList(first, second));

        List<BlogImage> result = service.loadOwnedTemporaryImages(Arrays.asList(2L, 1L), 7L);

        assertEquals(Arrays.asList(second, first), result);
    }

    @Test
    void bindToBlog_should_bind_every_image_in_display_order() {
        when(blogImageMapper.update(any(), any())).thenReturn(1);

        service.bindToBlog(Arrays.asList(2L, 1L), 7L, 99L);

        verify(blogImageMapper, times(2)).update(any(), any());
    }

    @Test
    void cleanupExpiredTemporaryImages_should_delete_claimed_asset_and_file() {
        BlogImage expired = temporaryImage(9L, 7L, "/imgs/expired.png")
                .setStorageKey("blogs/expired.png");
        when(blogImageMapper.selectList(any())).thenReturn(Collections.singletonList(expired));
        when(blogImageMapper.update(any(), any())).thenReturn(1);
        when(blogImageMapper.deleteById(9L)).thenReturn(1);

        int cleaned = service.cleanupExpiredTemporaryImages();

        assertEquals(1, cleaned);
        verify(storage).delete("blogs/expired.png");
        verify(blogImageMapper).deleteById(9L);
    }

    @Test
    void cleanupDeletingImages_should_retry_and_remove_metadata_when_file_is_already_gone() {
        BlogImage deleting = new BlogImage()
                .setId(9L)
                .setUserId(7L)
                .setStorageKey("blogs/deleting.png")
                .setStatus(BlogImage.STATUS_DELETING);
        when(blogImageMapper.selectList(any())).thenReturn(Collections.singletonList(deleting));
        when(blogImageMapper.update(any(), any())).thenReturn(1);
        when(blogImageMapper.delete(any())).thenReturn(1);

        int cleaned = service.cleanupDeletingImages();

        assertEquals(1, cleaned);
        verify(storage).delete("blogs/deleting.png");
        verify(blogImageMapper).delete(any());
    }

    private BlogImage temporaryImage(Long id, Long userId, String url) {
        return new BlogImage()
                .setId(id)
                .setUserId(userId)
                .setPublicUrl(url)
                .setStatus(BlogImage.STATUS_TEMP);
    }
}
