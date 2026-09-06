package com.hmdp.service.impl;

/*
 * 现实业务背景：个人主页展示和编辑需要持久化用户扩展资料。
 * 实际触发：UserController.info() 查询资料，UserServiceImpl 在登录初始化或保存资料时调用本实现的通用 CRUD。
 */

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.service.IUserInfoService;
import org.springframework.stereotype.Service;

/**
 * 用户扩展资料的通用持久化实现。
 *
 * 完整流程：本类没有自定义接口方法，查询个人资料时 Controller 调用继承自 {@link ServiceImpl}（MyBatis-Plus 的通用服务基类，
 * 内置 getById/save/updateById 等单表 CRUD）的 getById；
 * 用户首次登录或修改资料时 {@link UserServiceImpl}（负责注册、登录、签到等用户主流程的服务）调用继承的 save/updateById，最终由 {@link UserInfoMapper}
 * （对应 {@code tb_user_info} 表的 Mapper）读写 {@code tb_user_info}。
 *
 * 具体例子：查看用户 7 调用 {@code getById(7)} 返回城市和简介；若资料尚不存在，登录流程会先保存
 * {@code userId=7} 的默认资料，之后修改城市时再调用 {@code updateById}。空实现类的作用是把通用 CRUD 注册为 Spring Service。
 *
 * @author 虎哥
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {
    


}
