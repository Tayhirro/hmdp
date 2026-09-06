package com.hmdp.service.search.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MySqlUserSearchServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "search-test"),
                User.class
        );
    }

    @Mock
    private UserMapper userMapper;

    @Test
    void search_should_return_empty_page_without_exposing_user_directory_for_blank_keyword() {
        MySqlUserSearchService service = new MySqlUserSearchService(userMapper);

        PageResultDTO<UserDTO> result = service.search(new SearchQuery().setKeyword(" "));

        assertTrue(result.getList().isEmpty());
        verify(userMapper, never()).selectPage(any(IPage.class), any(Wrapper.class));
    }

    @Test
    void search_should_select_and_return_only_public_user_fields() {
        MySqlUserSearchService service = new MySqlUserSearchService(userMapper);
        User user = new User()
                .setId(7L)
                .setNickName("火锅研究员")
                .setIcon("/icons/7.png")
                .setAccount("private-account")
                .setPhone("13800000000")
                .setPassword("secret");
        Page<User> databasePage = new Page<>(1, 10, 1);
        databasePage.setRecords(Collections.singletonList(user));
        when(userMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(databasePage);
        ArgumentCaptor<IPage<User>> pageCaptor = pageCaptor();

        PageResultDTO<UserDTO> result = service.search(new SearchQuery()
                .setKeyword("火锅")
                .setCurrent(1)
                .setPageSize(10));

        verify(userMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        UserDTO item = result.getList().get(0);
        assertEquals(7L, item.getId());
        assertEquals("火锅研究员", item.getNickName());
        assertFalse(hasField(UserDTO.class, "account"));
        assertFalse(hasField(UserDTO.class, "phone"));
        assertFalse(hasField(UserDTO.class, "password"));
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(10L, pageCaptor.getValue().getSize());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<IPage<User>> pageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(IPage.class);
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
