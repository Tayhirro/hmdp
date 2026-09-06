package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 
 *  优惠券前端控制器（根路径 {@code /voucher}），提供普通券/秒杀券新增与店铺券列表查询；
 *  券基础信息在 tb_voucher，秒杀扩展信息（库存、起止时间）在 tb_seckill_voucher。
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券。
     * 使用场景：管理端为店铺新增不带秒杀属性的优惠券时，前端发送 POST /voucher，请求体为券 JSON。
     * 数据库：直接向 tb_voucher 插入一行并返回券 id，不写 tb_seckill_voucher。
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券。
     * 使用场景：管理端新增带库存和起售/止售时间的秒杀券时，前端发送 POST /voucher/seckill。
     * 数据库：同一事务先向 tb_voucher 插入基础信息，再按同一主键向 tb_seckill_voucher 写入 stock、begin_time、end_time；
     * 任一步失败一起回滚。
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表。
     * 使用场景：用户打开店铺详情页加载“优惠券”区域时，前端发送 GET /voucher/list/{shopId}。
     * 数据库：tb_voucher LEFT JOIN tb_seckill_voucher（条件 shop_id 等于参数且 status=1），
     * 一次带出普通券字段和秒杀券的库存、起止时间；只读查询，不扣减库存。
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}
