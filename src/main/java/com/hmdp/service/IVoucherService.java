package com.hmdp.service;

/*
 * 现实业务背景：用户打开店铺详情需要查看可用券，运营人员则需要创建普通券或带库存时段的秒杀券。
 * 实际触发：VoucherController 的查询和新增接口调用本服务；秒杀订单不属于该接口的已实现范围。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;

/**
 * 优惠券服务。普通券信息存于 tb_voucher；秒杀券的库存与时间窗另存于 tb_seckill_voucher（与 tb_voucher 一对一）。
 * 注意：新增普通券（POST /voucher）走的是继承自 IService 的 save()，不经过本接口声明的方法。
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    /**
     * 查询店铺的优惠券列表（GET /voucher/list/{shopId}）：Mapper 用一条 LEFT JOIN SQL
     * 把 tb_voucher 的基础字段和对应 tb_seckill_voucher 的 stock 库存、beginTime/endTime 秒杀时间窗一起查回
     * （WHERE 条件含 status=1，即只返回上架中的券）；只读操作，不修改库存，秒杀券与普通券按 shopId 一起返回。
     * 使用场景：用户打开店铺详情页加载“优惠券”区域时触发；内部调用方仅 VoucherController。
     */
    Result queryVoucherOfShop(Long shopId);

    /**
     * 新增秒杀券（POST /voucher/seckill）：先保存 tb_voucher 拿到券主键，
     * 再用同一主键写入 tb_seckill_voucher（voucherId、stock、beginTime、endTime），
     * 两步在同一事务中，任一步失败一起回滚；只创建券，不处理抢购下单。
     * 使用场景：管理端新增带库存和起售/止售时间的秒杀券时触发；内部调用方仅 VoucherController。
     */
    void addSeckillVoucher(Voucher voucher);
}
