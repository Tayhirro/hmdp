package com.hmdp.utils;

/**
 * 业务侧通用常量（分页上限、昵称前缀、Feed 容量等）。
 */
public class SystemConstants {
    /** 新用户默认昵称前缀。使用处：UserServiceImpl 三处注册/创建用户昵称 = "user_" + 10 位随机串。 */
    public static final String USER_NICK_NAME_PREFIX = "user_";
    /** 默认分页大小。使用处：ShopServiceImpl 附近店铺分页每页 5 条、DefaultUnifiedSearchService 的默认每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 5;
    /** 单页最大条数上限。使用处：BlogQueryService/BlogLikeService 限制评论与点赞列表每页最多 10 条、MySqlSearchSupport 校验 size 必须在 1~10、各搜索服务固定取 10。 */
    public static final int MAX_PAGE_SIZE = 10;
    /** Feed 收件箱缓存容量上限（预留）。当前工程代码无直接引用，供 Feed 收件箱截断/淘汰策略使用。 */
    public static final int FEED_INBOX_CACHE_MAX_SIZE = 1000;
}
