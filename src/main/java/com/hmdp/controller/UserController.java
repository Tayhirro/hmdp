package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * 
 * 用户前端控制器（根路径 {@code /user}），覆盖验证码、登录、注册、绑定手机、签到、登出与个人资料；
 * 登录态以 Redis 中的 token Hash（login:token:{token}）为准，签到使用 Redis Bitmap。
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码。
     * 使用场景：用户在登录/注册/绑定手机页点击“获取验证码”时，前端发送 POST /user/code?phone=手机号。
     * Redis：先用 SETNX 建立 60 秒发送锁（key 为 login:code:send:lock:{手机号}）防止短信轰炸，
     * 生成 6 位验证码经短信服务发出后写入 login:code:{手机号}（TTL 2 分钟）；发送失败会删锁允许重试。
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能。
     * 使用场景：用户在登录页提交表单时，前端发送 POST /user/login，请求体为 {@link LoginFormDTO}（登录表单参数）。
     * 数据库/Redis：带密码时按 account 或 phone 查 tb_user 并用 BCrypt 校验；
     * 不带密码时校验 Redis 验证码 login:code:{手机号} 并删除已用验证码；
     * 成功后初始化缺失的 tb_user_info 资料，生成随机 token，把用户字段写入 Redis Hash login:token:{token}
     * （TTL 36000 秒即 10 小时）并返回 token，后续请求凭该 token 认证。
     * @param loginForm 登录参数：手机号+验证码；或 账号(account)+密码；或 手机号+密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 注册功能：
     * 1) 账号注册：account + password
     * 2) 手机号注册：phone + code（手机号会作为 account）
     * 使用场景：用户在注册页提交表单时，前端发送 POST /user/signup。
     * 数据库/Redis：账号注册先按 account 查 tb_user 保证唯一（同时带 phone+code 则顺便绑定并直接签发 token，
     * 否则返回 requiresPhoneBinding=true 等待下一步绑定）；手机号注册要求 phone 与 account 均未被占用、
     * Redis 验证码 login:code:{手机号} 一致，创建用户后删除已用验证码并签发登录 token。
     */
    @PostMapping("/signup")
    public Result signUp(@RequestBody LoginFormDTO signUpForm, HttpSession session) {
        return userService.signUp(signUpForm, session);
    }
    
    /**
     * 绑定手机号接口（两阶段账号注册的第二步）。
     * 使用场景：账号注册成功后页面引导绑定手机时，前端发送 POST /user/bind-phone
     * （account + phone + code，此时用户可能尚未登录，靠账号定位用户）。
     * 数据库/Redis：按 account 查 tb_user，比对 Redis 验证码 login:code:{手机号}，确认手机号未被其他用户占用后
     * 更新 tb_user.phone 并删除验证码，最后初始化资料并签发登录 token。
     */
    @PostMapping("/bind-phone")
    public Result bindPhone(@RequestBody LoginFormDTO bindPhoneForm, HttpSession session) {
        return userService.bindPhone(bindPhoneForm, session);
    }

    /**
     * 用户当日签到。
     * 使用场景：登录用户在签到面板点击“签到”时，前端发送 POST /user/sign（用户 ID 取自登录上下文）。
     * Redis：对每用户每月一个 Bitmap（key 为 sign:{userId}:{yyyyMM}），以“今天是本月第几天 - 1”为偏移执行 SETBIT true；
     * 同一天重复签到只是重设同一位，天然幂等。
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }
    /**
     * 统计用户签到,最近的连续签到天数。
     * 使用场景：前端打开签到面板展示“连续签到 N 天”时，发送 GET /user/sign/count。
     * Redis：用 BITFIELD 一次读出 sign:{userId}:{yyyyMM} 本月 1 日至今天的全部位，
     * 从最低位（今天）向前逐位统计连续 1，遇到 0 停止；无签到记录返回 0，不会跨过断签累计。
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    /**
     * 退出登录。
     * 使用场景：用户点击“退出登录”时，前端发送 POST /user/logout，token 取自请求头 authorization
     * （可带 Bearer 前缀，也可缺省）。
     * Redis：删除登录 Hash login:token:{token}；token 为空或 key 不存在也返回成功，重复退出幂等。
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token){
        return userService.logOut(token);
    }

    /**
     * 查询当前登录用户信息。
     * 使用场景：前端页面初始化时发送 GET /user/me 恢复登录态、展示头像昵称。
     * 数据来源：仅从 {@link UserHolder}（基于 ThreadLocal 的当前登录用户上下文）读取 UserDTO，不查数据库；
     * 未登录时该值由拦截器/解析器决定是否为 null。
     */
    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }


    /**
     * 查询指定用户的详情信息，用于共同关注功能进入其他人主页时获取信息，非重点。
     * 使用场景：博客卡片、评论区等跳转到作者主页时，前端发送 GET /user/{id}。
     * 数据库：按主键查 tb_user 并裁剪为 {@link UserDTO}（用户脱敏信息 DTO）返回；用户不存在时返回空 data 而不是报错。
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 查询指定用户的详情资料（城市、简介、性别、生日、粉丝数等）。
     * 使用场景：进入他人主页展示个人资料面板时，前端发送 GET /user/info/{id}。
     * 数据库：按主键查 tb_user_info；没有记录（首次查看）时返回空 data，返回前会置空 createTime/updateTime。
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 修改当前登录用户的个人资料。
     * 使用场景：用户在个人资料页编辑并保存时，前端发送 PUT /user/info，请求体为 UserInfo JSON；
     * 用户 ID 从登录上下文取得，不能由前端伪造。
     * 数据库：先确保 tb_user_info 记录存在，再只复制 city、introduce、gender、birthday 四个白名单字段按 userId 更新，
     * 请求中的 fans 等计数、等级字段不会被采纳。
     */
    @PutMapping("/info")
    public Result changeInfo(@RequestBody UserInfo info){
        Long userId = UserHolder.getUser().getId();
        return userService.changeInfo(userId, info);
    }

}
