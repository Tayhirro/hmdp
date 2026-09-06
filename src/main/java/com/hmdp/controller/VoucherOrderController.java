package com.hmdp.controller;


import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 
 *  优惠券订单前端控制器（根路径 {@code /voucher-order}）；当前仅保留秒杀下单入口，下单逻辑尚未实现。
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    /**
     * 秒杀券下单入口（未实现）。
     * 使用场景：为前端抢券预留的接口，请求形式为 POST /voucher-order/seckill/{券id}；当前任何请求都直接失败。
     * 数据库/Redis：无任何读写，直接返回失败结果“功能未完成”，秒杀扣库存、一人一单等逻辑待后续接入订单服务实现。
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return Result.fail("功能未完成");
    }
}
