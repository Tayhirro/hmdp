package com.hmdp.service;

/*
 * 现实业务背景：用户打开或编辑个人主页时，需要读取和保存城市、简介、性别、生日等扩展资料。
 * 实际触发：UserController.info() 与 UserServiceImpl 的资料初始化、修改流程调用本接口。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.UserInfo;

/**
 * 用户扩展资料服务。tb_user_info 与用户表 tb_user 一对一：
 * 主键就是 userId，额外保存 city 城市、introduce 简介、fans 粉丝数、followee 关注数、
 * gender 性别（0 男、1 女、2 未知）、birthday 生日、credits 积分、level 等级等统计与资料字段。
 * 消费方：
 * 1. UserController 的 GET /user/info/{id} 用继承的 getById() 读资料（返回前抹掉 createTime/updateTime）。
 * 2. UserServiceImpl 在登录/注册成功后用 save() 初始化缺失的资料行（空简介、fans/followee/credits/level 为 0）。
 * 3. UserServiceImpl.changeInfo()（PUT /user/info）用继承的 updateById() 只更新 city/introduce/gender/birthday
 *    四个白名单字段，请求里的 fans 等统计字段不会被采纳。
 * @author 虎哥
 * @since 2021-12-24
 */
public interface IUserInfoService extends IService<UserInfo> {

}
