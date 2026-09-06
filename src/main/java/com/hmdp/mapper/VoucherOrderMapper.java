package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.VoucherOrder;

/**
 * 优惠券订单表 tb_voucher_order 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。
 * 当前无调用方：仅被空壳实现 VoucherOrderServiceImpl 继承注册，没有任何 Controller 或业务代码
 * 调用其读写方法，消费者抢券下单链路尚未实现。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

}
