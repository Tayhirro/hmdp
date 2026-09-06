package com.hmdp.service.search;

import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.ShopSearchItemDTO;
import com.hmdp.dto.UnifiedSearchResultDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchContractTest {

    @Test
    void shopSearch_should_be_one_vertical_instead_of_parent_for_all_searches() {
        assertTrue(VerticalSearchService.class.isAssignableFrom(ShopSearchService.class));
        assertTrue(VerticalSearchService.class.isAssignableFrom(BlogSearchService.class));
        assertTrue(VerticalSearchService.class.isAssignableFrom(UserSearchService.class));
        ShopSearchService service = new ShopSearchService() {
            @Override
            public PageResultDTO<ShopSearchItemDTO> search(String keyword, Integer current) {
                return PageResultDTO.empty(current == null ? 1 : current, 10);
            }
        };

        SearchQuery query = new SearchQuery().setKeyword("火锅").setCurrent(2);
        PageResultDTO<ShopSearchItemDTO> page = service.search(query);

        assertEquals(SearchScope.SHOP, service.scope());
        assertEquals(2L, page.getCurrent());
    }

    @Test
    void unifiedSearch_should_keep_aggregate_contract_separate_from_verticals() throws Exception {
        Method method = UnifiedSearchService.class.getMethod("search", SearchQuery.class);

        assertSame(UnifiedSearchResultDTO.class, method.getReturnType());
        assertTrue(UnifiedSearchService.class.isInterface());
        assertTrue(SearchSuggestionService.class.isInterface());
    }
}
