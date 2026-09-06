package com.hmdp.service.impl;

/*
 * 现实业务背景：运营人员发布秒杀券时，需要保存该券的库存和有效时间；消费者抢券逻辑尚未完成。
 * 实际触发：VoucherServiceImpl.addSeckillVoucher() 在同一事务中调用本类继承的 save()。
 */

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;

/**
 * 秒杀券扩展数据的通用持久化实现。
 *
 * 完整流程：本类没有自定义接口方法，调用方使用继承自 {@link ServiceImpl}（MyBatis-Plus 的通用服务基类，
 * 内置 save/getById/update 等单表 CRUD）的能力，
 * 由 {@link SeckillVoucherMapper}（对应 {@code tb_seckill_voucher} 表的 Mapper）读写 {@code tb_seckill_voucher}。当前真实入口是新增秒杀券：
 * {@link VoucherServiceImpl#addSeckillVoucher(com.hmdp.entity.Voucher)}（普通优惠券服务，负责 {@code tb_voucher} 表）先保存基础券，再调用本服务的 save 保存同 ID 的库存和时间窗。
 *
 * 具体例子：基础优惠券 ID 为 300、库存 100，本服务保存 {@code voucherId=300,stock=100}。
 * 它只完成数据持久化，不代表“校验时间 → 扣库存 → 防止重复下单 → 创建订单”的消费者秒杀流程已经实现。
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
