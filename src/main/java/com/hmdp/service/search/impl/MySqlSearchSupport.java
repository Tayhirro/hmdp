package com.hmdp.service.search.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.exception.BusinessException;
import com.hmdp.utils.SystemConstants;

/** 第一阶段 MySQL 垂直搜索共用的输入边界，避免三个实现各自解释分页和 LIKE 规则。 */
final class MySqlSearchSupport {

    private static final int MAX_KEYWORD_LENGTH = 64;

    private MySqlSearchSupport() {
    }

    static String normalizeKeyword(String keyword) {
        String normalized = StrUtil.trim(keyword);
        if (StrUtil.isNotBlank(normalized) && normalized.length() > MAX_KEYWORD_LENGTH) {
            throw BusinessException.badRequest("SEARCH_KEYWORD_TOO_LONG", "搜索关键词不能超过 64 个字符");
        }
        return normalized;
    }

    static int normalizePage(Integer current) {
        int pageNumber = current == null ? 1 : current;
        if (pageNumber < 1) {
            throw BusinessException.badRequest("INVALID_SEARCH_PAGE", "搜索页码必须从 1 开始");
        }
        return pageNumber;
    }

    static int normalizePageSize(Integer pageSize, int defaultPageSize) {
        int normalized = pageSize == null ? defaultPageSize : pageSize;
        if (normalized < 1 || normalized > SystemConstants.MAX_PAGE_SIZE) {
            throw BusinessException.badRequest(
                    "INVALID_SEARCH_PAGE_SIZE",
                    "搜索每页数量必须在 1 到 " + SystemConstants.MAX_PAGE_SIZE + " 之间"
            );
        }
        return normalized;
    }

    static String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
