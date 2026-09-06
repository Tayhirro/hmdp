package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.SeckillVoucher;

/**
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper（对应 tb_seckill_voucher 表）。
 * 使用方：SeckillVoucherServiceImpl（继承 ServiceImpl），由 VoucherServiceImpl.addSeckillVoucher
 * 在同一事务内调用其继承的 save 写入 voucher_id、stock、begin_time、end_time；
 * 库存和起止时间的读取走 VoucherMapper.queryVoucherOfShop 的联表查询。
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

}
