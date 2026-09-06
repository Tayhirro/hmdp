package com.hmdp.service.search.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.blog.BlogAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MySqlBlogSearchServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "search-test"),
                Blog.class
        );
    }

    @Mock
    private BlogMapper blogMapper;

    @Mock
    private BlogAssembler blogAssembler;

    @Test
    void search_should_return_empty_page_without_scanning_blogs_when_keyword_is_blank() {
        MySqlBlogSearchService service = new MySqlBlogSearchService(blogMapper, blogAssembler);

        PageResultDTO<BlogCardDTO> result = service.search(new SearchQuery()
                .setKeyword("  ")
                .setCurrent(1)
                .setPageSize(5));

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        verify(blogMapper, never()).selectPage(any(IPage.class), any(Wrapper.class));
        verify(blogAssembler, never()).toCards(any());
    }

    @Test
    void search_should_match_blog_document_and_batch_assemble_cards() {
        MySqlBlogSearchService service = new MySqlBlogSearchService(blogMapper, blogAssembler);
        Blog blog = new Blog()
                .setId(20L)
                .setUserId(2L)
                .setTitle("周末火锅探店")
                .setCreateTime(LocalDateTime.of(2026, 8, 9, 12, 0));
        BlogCardDTO card = new BlogCardDTO().setId(20L).setTitle("周末火锅探店");
        Page<Blog> databasePage = new Page<>(1, 5, 8);
        databasePage.setRecords(Collections.singletonList(blog));
        when(blogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(databasePage);
        when(blogAssembler.toCards(Collections.singletonList(blog))).thenReturn(Collections.singletonList(card));
        ArgumentCaptor<IPage<Blog>> pageCaptor = pageCaptor();

        PageResultDTO<BlogCardDTO> result = service.search(new SearchQuery()
                .setKeyword("  火锅  ")
                .setCurrent(1)
                .setPageSize(5));

        verify(blogMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        assertEquals(Collections.singletonList(card), result.getList());
        assertEquals(8L, result.getTotal());
        assertTrue(result.isHasMore());
        assertEquals(1L, pageCaptor.getValue().getCurrent());
        assertEquals(5L, pageCaptor.getValue().getSize());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<IPage<Blog>> pageCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(IPage.class);
    }
}
