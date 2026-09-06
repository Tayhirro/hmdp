package com.hmdp.controller;

import com.hmdp.dto.BlogImageUploadDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IBlogImageService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadControllerTest {

    @Mock
    private IBlogImageService blogImageService;

    @InjectMocks
    private UploadController uploadController;

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void uploadImage_should_register_image_for_current_user() {
        saveCurrentUser(7L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                new byte[]{1}
        );
        BlogImageUploadDTO uploaded = new BlogImageUploadDTO(11L, "/imgs/blogs/photo.png");
        when(blogImageService.upload(file, 7L)).thenReturn(uploaded);

        Result result = uploadController.uploadImage(file);

        assertTrue(result.getSuccess());
        assertEquals(uploaded, result.getData());
        verify(blogImageService).upload(file, 7L);
    }

    @Test
    void deleteBlogImage_should_delete_by_asset_id_for_current_user() {
        saveCurrentUser(7L);

        Result result = uploadController.deleteBlogImage(11L);

        assertTrue(result.getSuccess());
        verify(blogImageService).deleteTemporaryImage(11L, 7L);
    }

    @Test
    void deleteBlogImage_should_only_accept_delete_requests() throws NoSuchMethodException {
        Method method = UploadController.class.getMethod("deleteBlogImage", Long.class);

        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        assertNotNull(mapping);
        assertEquals("/{imageId}", mapping.value()[0]);
        assertNull(method.getAnnotation(GetMapping.class));
    }

    private void saveCurrentUser(Long userId) {
        UserDTO user = new UserDTO();
        user.setId(userId);
        UserHolder.saveUser(user);
    }
}
