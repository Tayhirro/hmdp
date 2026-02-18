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
 * <p>
 * 前端控制器
 * </p>
 *
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
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
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
     */
    @PostMapping("/signup")
    public Result signUp(@RequestBody LoginFormDTO signUpForm, HttpSession session) {
        return userService.signUp(signUpForm, session);
    }
    
    /**
     * 绑定手机号接口
     */
    @PostMapping("/bind-phone")
    public Result bindPhone(@RequestBody LoginFormDTO bindPhoneForm, HttpSession session) {
        return userService.bindPhone(bindPhoneForm, session);
    }

    /**
     * 用户签到
     */
    @PostMapping("/sign")
    public Result sign() {
        return userService.sign();
    }
    /**
     * 统计用户签到,最近的连续签到天数
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token){
        return userService.logOut(token);
    }

    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }


    /**
     * 查询指定用户的详情信息，用于共同关注功能进入其他人主页时获取信息，非重点
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

    @PutMapping("/info")
    public Result changeInfo(@RequestBody UserInfo info){
        Long userId = UserHolder.getUser().getId();
        return userService.changeInfo(userId, info);
    }

}
