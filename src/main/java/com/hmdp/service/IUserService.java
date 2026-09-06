package com.hmdp.service;

/*
 * 现实业务背景：用户注册、登录、绑定手机、签到、登出、查看用户摘要或修改资料时，需要身份与用户服务。
 * 实际触发：UserController 的用户域接口调用本契约；通用用户查询还被博客和关注的批量装配使用。
 * 认证方式：登录成功后生成随机 token，用户摘要写入 Redis Hash（key 为 login:token:{token}，TTL 36000 秒即 10 小时），
 * 后续请求凭 token 恢复用户上下文；方法签名里的 HttpSession 参数只是历史遗留，当前认证链路不使用它。
 * 通用查询：BlogCommentsServiceImpl、FollowServiceImpl、BlogAssembler、BlogLikeService 等都通过
 * 继承的 listByIds() 批量读取用户昵称和头像，用于装配博客卡片、评论作者等场景。
 */

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import javax.servlet.http.HttpSession;


/**
 * 用户身份与签到服务。所有方法返回 {@link Result}（本项目统一的 HTTP 响应包装：
 * {@code success/data/errorCode/errorMsg/traceId}）。
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码（POST /user/code）：校验手机号格式后，用 Redis SETNX 给同一手机号加 60 秒发送锁，
     * 生成 6 位数字验证码发送出去，并以 2 分钟 TTL 存入 Redis（key 为 login:code:{手机号}）供登录/绑定时比对。
     * 使用场景：用户在登录/注册/绑定手机页点击“获取验证码”时触发；内部调用方仅 UserController。
     */
    Result sendCode(String phone, HttpSession session);
    
    /**
     * 登录（POST /user/login），按请求内容走三条分支：
     * 账号(account)+密码、手机号(phone)+密码（BCrypt 校验），或手机号+验证码（校验后删除已用验证码）；
     * 成功后初始化缺失的用户资料并签发 token。验证码登录不会自动注册，不存在的用户会被引导先注册。
     * 使用场景：用户在登录页提交表单时触发；内部调用方仅 UserController。
     */
    Result login(LoginFormDTO loginForm,HttpSession session);

    /**
     * 注册（POST /user/signup）：account+密码 走账号注册（BCrypt 保存密码，可能返回 requiresPhoneBinding
     * 要求第二步绑手机后才发 token）；phone+验证码 走手机号注册（手机号同时作为 account），完成后直接签发 token。
     * 使用场景：用户在注册页提交表单时触发；内部调用方仅 UserController。
     */
    Result signUp(LoginFormDTO signUpForm, HttpSession session);

    /**
     * 绑定手机号（POST /user/bind-phone）：两阶段注册的第二步——校验手机号和 Redis 验证码，
     * 确认手机号未被其他用户占用后写入 tb_user.phone，成功即签发登录 token。
     * 使用场景：账号注册成功后页面引导绑定手机时触发；内部调用方仅 UserController。
     */
    Result bindPhone(LoginFormDTO bindPhoneForm, HttpSession session);

    /**
     * 今日签到（POST /user/sign）：每个用户每月一个 Redis Bitmap（key 为 sign:{用户 ID}:{yyyyMM}），
     * 用“今天几号减一”作偏移量 SETBIT 置 1；同一天重复签到只是把同一位再置 1，天然幂等。
     * 使用场景：登录用户在签到面板点击“签到”时触发；内部调用方仅 UserController。
     */
    Result sign();

    /**
     * 查询本月连续签到天数（GET /user/sign/count）：用 BITFIELD 一次读出本月 1 日到今天的所有位，
     * 从今天（最低位）往前数连续的 1，遇到 0 停止；中断过的月份不跨断签累计。
     * 使用场景：前端打开签到面板展示“连续签到 N 天”时触发；内部调用方仅 UserController。
     */
    Result signCount();

    /**
     * 修改个人资料（PUT /user/info）：userId 由 Controller 从登录上下文取得，不能伪造；
     * 只复制 city、introduce、gender、birthday 四个白名单字段更新 tb_user_info，其余统计字段不受请求影响。
     * 使用场景：用户在个人资料页编辑并保存时触发；内部调用方仅 UserController。
     */
    //info 修改
    Result changeInfo(Long userId, UserInfo info);
    

    /**
     * 退出登录（POST /user/logout）：入参是请求头里未解析的原始 token（可带 Bearer 前缀），
     * 去掉前缀后删除 Redis 中的登录 Hash（login:token:{token}）；token 为空或不存在都返回成功，重复退出幂等。
     * 使用场景：用户点击“退出登录”时触发；内部调用方仅 UserController。
     */
    // 未解析的原始token
    Result logOut(String token); 

}   
