package com.hmdp.service.search.impl;

import com.hmdp.dto.BlogCardDTO;
import com.hmdp.dto.PageResultDTO;
import com.hmdp.dto.SearchQuery;
import com.hmdp.dto.ShopSearchItemDTO;
import com.hmdp.dto.UnifiedSearchResultDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.search.BlogSearchService;
import com.hmdp.service.search.SearchScope;
import com.hmdp.service.search.ShopSearchService;
import com.hmdp.service.search.UserSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultUnifiedSearchServiceTest {

    @Mock
    private ShopSearchService shopSearchService;

    @Mock
    private BlogSearchService blogSearchService;

    @Mock
    private UserSearchService userSearchService;

    private DefaultUnifiedSearchService unifiedSearchService;

    @BeforeEach
    void setUp() {
        when(shopSearchService.scope()).thenReturn(SearchScope.SHOP);
        when(blogSearchService.scope()).thenReturn(SearchScope.BLOG);
        when(userSearchService.scope()).thenReturn(SearchScope.USER);
        unifiedSearchService = new DefaultUnifiedSearchService(
                Arrays.asList(shopSearchService, blogSearchService, userSearchService)
        );
        clearInvocations(shopSearchService, blogSearchService, userSearchService);
    }

    @Test
    void search_should_fan_out_to_all_registered_verticals_for_aggregate_view() {
        when(shopSearchService.search(any(SearchQuery.class))).thenReturn(new PageResultDTO<>(
                Collections.singletonList(new ShopSearchItemDTO().setId(1L)), 1, 5, 1, false
        ));
        when(blogSearchService.search(any(SearchQuery.class))).thenReturn(new PageResultDTO<>(
                Collections.singletonList(new BlogCardDTO().setId(2L)), 1, 5, 1, false
        ));
        when(userSearchService.search(any(SearchQuery.class))).thenReturn(new PageResultDTO<>(
                Collections.singletonList(user(3L)), 1, 5, 1, false
        ));

        UnifiedSearchResultDTO result = unifiedSearchService.search(
                new SearchQuery().setKeyword("  火锅  ")
        );

        assertEquals("火锅", result.getNormalizedKeyword());
        assertEquals(Arrays.asList(SearchScope.SHOP, SearchScope.BLOG, SearchScope.USER), Arrays.asList(
                result.getSections().get(0).getScope(),
                result.getSections().get(1).getScope(),
                result.getSections().get(2).getScope()
        ));
        verify(shopSearchService).search(any(SearchQuery.class));
        verify(blogSearchService).search(any(SearchQuery.class));
        verify(userSearchService).search(any(SearchQuery.class));
    }

    @Test
    void search_should_call_only_selected_tab_without_automatic_intent_guessing() {
        when(blogSearchService.search(any(SearchQuery.class))).thenReturn(new PageResultDTO<>(
                Collections.singletonList(new BlogCardDTO().setId(2L)), 1, 10, 1, false
        ));

        UnifiedSearchResultDTO result = unifiedSearchService.search(new SearchQuery()
                .setKeyword("火锅")
                .setScopes(EnumSet.of(SearchScope.BLOG))
                .setPageSize(10));

        assertEquals(1, result.getSections().size());
        assertEquals(SearchScope.BLOG, result.getSections().get(0).getScope());
        verify(shopSearchService, never()).search(any(SearchQuery.class));
        verify(blogSearchService).search(any(SearchQuery.class));
        verify(userSearchService, never()).search(any(SearchQuery.class));
    }

    @Test
    void constructor_should_reject_two_implementations_claiming_same_scope() {
        ShopSearchService anotherShop = org.mockito.Mockito.mock(ShopSearchService.class);
        when(anotherShop.scope()).thenReturn(SearchScope.SHOP);

        assertThrows(
                IllegalStateException.class,
                () -> new DefaultUnifiedSearchService(Arrays.asList(shopSearchService, anotherShop))
        );
    }

    private UserDTO user(Long id) {
        UserDTO user = new UserDTO();
        user.setId(id);
        return user;
    }
}
