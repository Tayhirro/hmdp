package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.UserInfo;

/**
 * 用户扩展资料表 tb_user_info 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。使用方：
 * 1. UserInfoServiceImpl（继承 ServiceImpl，本接口的直接使用方）：UserController.info（GET /user/info/{id}）
 *    经其 getById 查询资料。
 * 2. UserServiceImpl 经注入的 IUserInfoService 间接调用（不直接注入本接口）：
 *    登录初始化时经 initUserInfoIfAbsent 调用继承的 save 保存默认资料，
 *    changeInfo（PUT /user/info）调用继承的 updateById 按 userId 更新 city、introduce、gender、birthday。
 *
 * @author 虎哥
 * @since 2021-12-24
 */
public interface UserInfoMapper extends BaseMapper<UserInfo> {

}
