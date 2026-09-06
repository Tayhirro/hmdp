package com.hmdp.service.impl;

/*
 * 现实业务背景：页面首次加载店铺分类时，需要查询 tb_shop_type 并按展示顺序返回。
 * 实际触发：ShopTypeController 通过 IShopTypeService 的通用查询能力间接使用本实现，当前没有额外业务方法。
 */

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.stereotype.Service;

/**
 * 店铺分类的通用查询实现。
 *
 * 完整流程：本类没有自定义接口方法，Controller 使用继承自 {@link ServiceImpl}（MyBatis-Plus 的通用服务基类，
 * 内置 query()/list() 等单表 CRUD）的 query() 创建查询构造器，
 * 增加 {@code sort ASC} 条件后由 {@link ShopTypeMapper}（对应 {@code tb_shop_type} 表的 Mapper）查询 {@code tb_shop_type}，最后返回列表。
 *
 * 具体例子：数据库中“美食”的 sort=1、“KTV”的 sort=2，调用 {@code GET /shop-type/list}
 * 会按“美食、KTV”返回。这里的空类不是“什么也没做”，而是复用 MyBatis-Plus 已实现的通用 CRUD。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

}
