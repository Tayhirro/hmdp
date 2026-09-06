package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.ShopType;

/**
 * 商铺类型表 tb_shop_type 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。
 * 使用方：ShopTypeServiceImpl（继承 ServiceImpl），ShopTypeController.queryTypeList
 * 通过其继承的 query() 附加 sort 升序条件查询全部分类（GET /shop-type/list）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopTypeMapper extends BaseMapper<ShopType> {

}
