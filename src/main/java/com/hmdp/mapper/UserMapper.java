package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.User;

/**
 * 用户表 tb_user 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。使用方：
 * 1. UserServiceImpl（继承 ServiceImpl）：注册时按 account 或 phone 查重并插入用户，
 *    登录时按 account 或 phone 查询用户（query().eq 条件形式），签到、绑定手机号等主流程亦基于本接口。
 * 2. MySqlUserSearchService.search：仅按 nick_name 字段 LIKE 匹配关键词分页查询，
 *    SELECT 只读取 id、nick_name、icon 三列以避免暴露敏感字段。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface UserMapper extends BaseMapper<User> {

}
