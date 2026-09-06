package com.hmdp.dto;

import com.hmdp.service.search.SearchScope;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 * 统一搜索的公共查询上下文。
 *
 * 关键词之外还保留检索域、城市和位置，因为“望京”“附近咖啡”等本地生活 Query
 * 不能只靠字符串匹配理解。每个垂直搜索自己的专有过滤条件以后应使用独立对象扩展，
 * 不要向这里持续添加任意 Map 或数据库字段。
 */
@Data
@Accessors(chain = true)
public class SearchQuery {

    /** 用户输入的关键词；统一搜索会建立标准化副本，不修改 Controller 创建的原对象。 */
    private String keyword;

    /** 调用方明确限定的检索域；为空时表示“综合”，召回全部已注册域。 */
    private Set<SearchScope> scopes;

    /** 页码，从 1 开始；当前 MySQL 垂直搜索只使用该浅分页字段。 */
    private Integer current;

    /** 期望页大小；各检索域仍需按自身容量设置服务端上限。 */
    private Integer pageSize;

    /** 城市或行政区编码，用于本地生活搜索的地域约束。 */
    private String cityCode;

    /** 当前搜索中心点经度；必须与纬度成对使用。 */
    private Double longitude;

    /** 当前搜索中心点纬度；必须与经度成对使用。 */
    private Double latitude;
}
