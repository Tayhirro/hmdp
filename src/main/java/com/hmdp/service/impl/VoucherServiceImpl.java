package com.hmdp.service.impl;

/*
 * 现实业务背景：店铺详情页需要展示优惠券，运营后台需要新增带库存和时间窗的秒杀券。
 * 实际触发：GET /voucher/list/{shopId} 与 POST /voucher/seckill 进入本类；普通券新增当前直接调用继承的 save()。
 */

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 
 *  服务实现类
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 查询店铺优惠券的完整流程：把 shopId 交给 VoucherMapper 的联表查询
     * 使用场景：用户打开店铺详情页查看优惠券列表时，前端发送 GET /voucher/list/{shopId}，由 VoucherController.queryVoucherOfShop() 调用。
     * （{@code tb_voucher v LEFT JOIN tb_seckill_voucher sv ON v.id = sv.voucher_id WHERE v.shop_id = ? AND v.status = 1}），
     * 一次读取普通券字段及秒杀券的库存、开始和结束时间，然后原样放入 Result 返回；本方法只读，不修改库存。
     * 具体例子：店铺 100 同时有一张代金券和一张 20:00 开抢的秒杀券，调用后返回两张券，秒杀券额外带 stock/beginTime/endTime。
     */
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    /**
     * 新增秒杀券的完整流程：先把券的基础信息保存到 {@code tb_voucher} 并取得主键，
     * 使用场景：运营端新增秒杀券时，前端发送 POST /voucher/seckill，由 VoucherController.addSeckillVoucher() 调用。
     * （普通券的 POST /voucher 直接调用继承的 save()，不经过本方法。）
     * 再把同一主键、库存和有效时间组装成 SeckillVoucher 保存到 {@code tb_seckill_voucher}；两步处于同一事务，任一步失败都会回滚。
     * 具体例子：新增“50 元券”、库存 100、20:00～22:00，若基础券生成 ID 300，则秒杀表也保存 {@code voucherId=300,stock=100}。
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }
}
