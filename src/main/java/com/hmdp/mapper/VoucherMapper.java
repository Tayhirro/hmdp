package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券表 tb_voucher 的数据访问接口。
 *
 * 自定义 SQL 方法 queryVoucherOfShop 定义在 resources/mapper/VoucherMapper.xml；
 * 通用 CRUD 的使用方是 VoucherServiceImpl（新增普通券走继承的 save，新增秒杀券走 addSeckillVoucher）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * 查询店铺的上架优惠券（SQL 见 resources/mapper/VoucherMapper.xml）。
     * SQL：SELECT tb_voucher 的 id、shop_id、title、sub_title、rules、pay_value、actual_value、type，
     * LEFT JOIN tb_seckill_voucher（ON v.id = sv.voucher_id）带出 stock、begin_time、end_time，
     * WHERE shop_id 等于参数且 status 等于 1（仅上架）。
     *
     * 使用场景：VoucherServiceImpl.queryVoucherOfShop，对应 GET /voucher/list/{shopId} 的店铺优惠券列表。
     */
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
