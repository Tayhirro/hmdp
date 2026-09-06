package com.hmdp.controller;

import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.ShopSearchItemDTO;
import com.hmdp.dto.UnifiedSearchResultDTO;
import com.hmdp.service.search.BlogSearchService;
import com.hmdp.service.search.SearchScope;
import com.hmdp.service.search.ShopSearchService;
import com.hmdp.service.search.UnifiedSearchService;
import com.hmdp.service.search.UserSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private ShopSearchService shopSearchService;

    @Mock
    private BlogSearchService blogSearchService;

    @Mock
    private UserSearchService userSearchService;

    @Mock
    private UnifiedSearchService unifiedSearchService;

    @InjectMocks
    private SearchController searchController;

    @Test
    void search_should_delegate_aggregate_query_to_unified_service() {
        UnifiedSearchResultDTO response = new UnifiedSearchResultDTO()
                .setNormalizedKeyword("火锅")
                .setSections(Collections.emptyList());
        when(unifiedSearchService.search(org.mockito.ArgumentMatchers.any(SearchQuery.class))).thenReturn(response);

        Result result = searchController.search("火锅", Collections.singleton(SearchScope.BLOG), 1, 5);

        assertSame(response, result.getData());
        verify(unifiedSearchService).search(org.mockito.ArgumentMatchers.argThat(query ->
                "火锅".equals(query.getKeyword())
                        && query.getScopes().equals(Collections.singleton(SearchScope.BLOG))
                        && query.getCurrent() == 1
                        && query.getPageSize() == 5
        ));
    }

    @Test
    void searchShops_should_expose_page_response_from_search_domain() {
        PageResultDTO<ShopSearchItemDTO> page = new PageResultDTO<>(
                Collections.singletonList(new ShopSearchItemDTO().setId(8L).setName("示例火锅店")),
                1,
                10,
                1,
                false
        );
        when(shopSearchService.search("火锅", 1)).thenReturn(page);

        Result result = searchController.searchShops("火锅", 1);

        assertTrue(result.getSuccess());
        assertSame(page, result.getData());
        verify(shopSearchService).search("火锅", 1);
    }

    @Test
    void legacyEndpoint_should_keep_array_shape_and_total() {
        ShopSearchItemDTO item = new ShopSearchItemDTO().setId(8L).setName("示例火锅店");
        PageResultDTO<ShopSearchItemDTO> page = new PageResultDTO<>(
                Collections.singletonList(item), 1, 10, 1, false
        );
        when(shopSearchService.search("火锅", 1)).thenReturn(page);

        Result result = searchController.searchShopsLegacy("火锅", 1);

        assertEquals(Collections.singletonList(item), result.getData());
        assertEquals(1L, result.getTotal());
    }

    @Test
    void mappings_should_live_on_searchController() throws NoSuchMethodException {
        Method aggregate = SearchController.class.getMethod(
                "search", String.class, Set.class, Integer.class, Integer.class
        );
        Method current = SearchController.class.getMethod("searchShops", String.class, Integer.class);
        Method blogs = SearchController.class.getMethod(
                "searchBlogs", String.class, Integer.class, Integer.class
        );
        Method users = SearchController.class.getMethod(
                "searchUsers", String.class, Integer.class, Integer.class
        );
        Method legacy = SearchController.class.getMethod("searchShopsLegacy", String.class, Integer.class);

        assertEquals("/search", aggregate.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/search/shops", current.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/search/blogs", blogs.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/search/users", users.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/shop/of/name", legacy.getAnnotation(GetMapping.class).value()[0]);
    }
}
