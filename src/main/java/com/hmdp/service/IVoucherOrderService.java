package com.hmdp.service;

/*
 * 现实业务背景：用户点击“立即抢购”后本应创建优惠券订单并维护状态，但这条业务链当前尚未实现。
 * 实际触发：目前没有 Controller 调用本接口——VoucherOrderController 的 POST /voucher-order/seckill/{id}
 * 直接返回 fail(“功能未完成”)，不经过本服务；只存在 MyBatis-Plus 空壳，不能视为可用秒杀功能。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.VoucherOrder;

/**
 * 优惠券订单服务。tb_voucher_order 表保存秒杀成交记录（userId 下单用户、voucherId 券、
 * payType 支付方式、status 订单状态及下单/支付/核销/退款时间等，见实体 {@link VoucherOrder}）。
 * 接口体为空，只继承了 IService 的通用 CRUD 能力；
 * 秒杀扣库存、创建订单、防超卖等核心流程均未实现，属于预留占位。
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

}
