package com.hmdp.service;

/*
 * 现实业务背景：运营人员创建秒杀券时，需要把库存和生效时间保存到 tb_seckill_voucher。
 * 实际触发：当前只由 VoucherServiceImpl.addSeckillVoucher() 调用基础保存能力；用户秒杀下单尚未实现
 * （VoucherOrderController 的 POST /voucher-order/seckill/{id} 直接返回“功能未完成”，不经过本接口）。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.SeckillVoucher;

/**
 * 秒杀优惠券表服务。tb_seckill_voucher 与优惠券表 tb_voucher 一对一：
 * voucherId 即 tb_voucher 的主键，同一张券在 tb_voucher 存标题等基础信息、
 * 在本表存 stock 库存、begin_time/endTime 秒杀时间窗。
 * 目前接口体为空，只继承了 IService 的通用 save 等能力；秒杀扣库存、下单流程尚未实现。
 * @author 虎哥
 * @since 2022-01-04
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {

}
