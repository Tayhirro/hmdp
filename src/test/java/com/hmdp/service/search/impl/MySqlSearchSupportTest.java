package com.hmdp.service.search.impl;

import com.hmdp.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MySqlSearchSupportTest {

    @Test
    void should_normalize_common_search_input_once_for_all_verticals() {
        assertEquals("火锅", MySqlSearchSupport.normalizeKeyword("  火锅  "));
        assertEquals(1, MySqlSearchSupport.normalizePage(null));
        assertEquals(5, MySqlSearchSupport.normalizePageSize(null, 5));
        assertEquals("50\\%\\_店\\\\", MySqlSearchSupport.escapeLikeKeyword("50%_店\\"));
    }

    @Test
    void should_reject_invalid_page_page_size_and_oversized_keyword() {
        BusinessException invalidPage = assertThrows(
                BusinessException.class,
                () -> MySqlSearchSupport.normalizePage(0)
        );
        BusinessException invalidPageSize = assertThrows(
                BusinessException.class,
                () -> MySqlSearchSupport.normalizePageSize(11, 5)
        );
        BusinessException longKeyword = assertThrows(
                BusinessException.class,
                () -> MySqlSearchSupport.normalizeKeyword(repeat("店", 65))
        );

        assertEquals("INVALID_SEARCH_PAGE", invalidPage.getCode());
        assertEquals("INVALID_SEARCH_PAGE_SIZE", invalidPageSize.getCode());
        assertEquals("SEARCH_KEYWORD_TOO_LONG", longKeyword.getCode());
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
