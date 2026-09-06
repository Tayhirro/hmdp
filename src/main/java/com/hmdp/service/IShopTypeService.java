package com.hmdp.service;

/*
 * 现实业务背景：首页和店铺列表页打开时，需要读取“美食、KTV”等店铺分类。
 * 实际触发：ShopTypeController.queryTypeList() 直接使用本接口继承的查询构造器按 sort 排序。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.ShopType;

/**
 * 店铺分类服务。数据存于 tb_shop_type（name 分类名、icon 图标、sort 排序号）。
 * 接口体为空：唯一的消费方 ShopTypeController 用继承自 IService 的 query() 构造器
 * 执行 ORDER BY sort ASC 的全表查询（1 条 SQL）返回全部分类，本服务自身没有额外业务方法。
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopTypeService extends IService<ShopType> {

}
