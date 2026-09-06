package com.hmdp.service.search.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.ShopSearchItemDTO;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.ShopMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MySqlShopSearchServiceTest {

    @Mock
    private ShopMapper shopMapper;

    @Test
    void search_should_return_empty_page_without_querying_database_when_keyword_is_blank() {
        MySqlShopSearchService service = new MySqlShopSearchService(shopMapper);

        PageResultDTO<ShopSearchItemDTO> result = service.search("  ", 1);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        assertFalse(result.isHasMore());
        verify(shopMapper, never()).selectPage(any(IPage.class), any(Wrapper.class));
    }

    @Test
    void search_should_map_entity_to_dto_and_return_explicit_page_metadata() {
        MySqlShopSearchService service = new MySqlShopSearchService(shopMapper);
        Shop shop = new Shop()
                .setId(8L)
                .setName("示例火锅店")
                .setTypeId(1L)
                .setAddress("示例路 1 号")
                .setX(120.1)
                .setY(30.2)
                .setScore(46);
        Page<Shop> databasePage = new Page<>(1, 10, 12);
        databasePage.setRecords(Collections.singletonList(shop));
        when(shopMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(databasePage);

        PageResultDTO<ShopSearchItemDTO> result = service.search("  火锅  ", 1);

        assertEquals(1, result.getList().size());
        assertEquals("示例火锅店", result.getList().get(0).getName());
        assertEquals(12L, result.getTotal());
        assertTrue(result.isHasMore());
        assertFalse(hasField(ShopSearchItemDTO.class, "x"));
        assertFalse(hasField(ShopSearchItemDTO.class, "createTime"));
    }

    @Test
    void search_should_escape_like_wildcards_and_add_stable_order() {
        MySqlShopSearchService service = new MySqlShopSearchService(shopMapper);
        when(shopMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>(1, 10, 0));
        ArgumentCaptor<Wrapper<Shop>> wrapperCaptor = wrapperCaptor();

        service.search("50%_店", 1);

        verify(shopMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        @SuppressWarnings("unchecked")
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Shop> wrapper =
                (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Shop>) wrapperCaptor.getValue();
        assertFalse(wrapper.getExpression().getOrderBy().isEmpty());
        String escaped = ReflectionTestUtils.invokeMethod(service, "escapeLikeKeyword", "50%_店");
        assertEquals("50\\%\\_店", escaped);
    }

    @Test
    void search_should_reject_invalid_page_and_oversized_keyword() {
        MySqlShopSearchService service = new MySqlShopSearchService(shopMapper);

        BusinessException invalidPage = assertThrows(
                BusinessException.class,
                () -> service.search("火锅", 0)
        );
        BusinessException longKeyword = assertThrows(
                BusinessException.class,
                () -> service.search(repeat("店", 65), 1)
        );

        assertEquals("INVALID_SEARCH_PAGE", invalidPage.getCode());
        assertEquals("SEARCH_KEYWORD_TOO_LONG", longKeyword.getCode());
        verify(shopMapper, never()).selectPage(any(IPage.class), any(Wrapper.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<Wrapper<Shop>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private boolean hasField(Class<?> type, String fieldName) {
        try {
            type.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException ignored) {
            return false;
        }
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
