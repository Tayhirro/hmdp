package com.hmdp.service.blog;

import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.BlogDetailDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogAssemblerTest {

    @Mock private IUserService userService;
    @Mock private BlogLikeMapper blogLikeMapper;

    @InjectMocks
    private BlogAssembler assembler;

    @Test
    void should_return_detail_and_card_dtos_without_internal_fields() {
        Blog blog = new Blog()
                .setId(1L)
                .setUserId(7L)
                .setTitle("标题")
                .setContent("完整正文")
                .setLiked(3);
        when(userService.listByIds(Collections.singleton(7L))).thenReturn(Collections.singletonList(
                new User().setId(7L).setNickName("作者").setIcon("icon.png")
        ));

        BlogDetailDTO detail = assembler.toDetail(blog);
        BlogCardDTO card = assembler.toCards(Collections.singletonList(blog)).get(0);

        assertEquals("完整正文", detail.getContent());
        assertEquals("作者", detail.getName());
        assertEquals("标题", card.getTitle());
        assertFalse(hasField(BlogCardDTO.class, "content"));
        assertFalse(hasField(BlogDetailDTO.class, "clientRequestId"));
        assertFalse(hasField(BlogDetailDTO.class, "requestHash"));
    }

    private boolean hasField(Class<?> type, String fieldName) {
        try {
            type.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }
}
