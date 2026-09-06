package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券实体，对应数据库表 tb_voucher。
 *
 * 秒杀券的库存和起止时间存放在 tb_seckill_voucher（一对一，主键相同），
 * 通过 stock、beginTime、endTime 三个非本表字段承接联表查询结果。
 * 主要使用方：VoucherMapper、VoucherServiceImpl（查询店铺优惠券、新增普通券与秒杀券）、
 * VoucherController（GET /voucher/list/{shopId}、POST /voucher/seckill）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商铺id
     */
    private Long shopId;

    /**
     * 代金券标题
     */
    private String title;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 使用规则
     */
    private String rules;

    /**
     * 支付金额，单位是分。例如200代表2元
     */
    private Long payValue;

    /**
     * 抵扣金额，单位是分。例如200代表2元
     */
    private Long actualValue;

    /**
     * 优惠券类型，0：普通券，1：秒杀券
     */
    private Integer type;

    /**
     * 上架状态，1：上架；2：下架；3：过期。查询店铺优惠券时仅返回 status=1 的记录
     */
    private Integer status;
    /**
     * 库存。非本表列（TableField exist = false），由 tb_seckill_voucher 联表查询填充
     */
    @TableField(exist = false)
    private Integer stock;

    /**
     * 生效时间。非本表列（TableField exist = false），由 tb_seckill_voucher 联表查询填充
     */
    @TableField(exist = false)
    private LocalDateTime beginTime;

    /**
     * 失效时间。非本表列（TableField exist = false），由 tb_seckill_voucher 联表查询填充
     */
    @TableField(exist = false)
    private LocalDateTime endTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;


    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
