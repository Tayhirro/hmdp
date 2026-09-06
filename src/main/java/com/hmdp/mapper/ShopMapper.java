package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Shop;

/**
 * 商铺表 tb_shop 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。使用方：
 * 1. ShopServiceImpl（继承 ServiceImpl）：queryById 按主键查询、update 按主键更新并清缓存、
 *    queryShopByType 按 type_id 分页或按 Redis GEO 命中的 ID 批量查询、delete 按主键删除。
 * 2. MySqlShopSearchService.search：按 name 字段 LIKE 匹配关键词分页查询店铺搜索结果。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopMapper extends BaseMapper<Shop> {

}
