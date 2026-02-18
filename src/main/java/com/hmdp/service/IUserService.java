package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import javax.servlet.http.HttpSession;


/**
 * 
 * 
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    Result sendCode(String phone, HttpSession session);
    
    Result login(LoginFormDTO loginForm,HttpSession session);

    Result signUp(LoginFormDTO signUpForm, HttpSession session);

    Result bindPhone(LoginFormDTO bindPhoneForm, HttpSession session);
    
    Result sign();

    Result signCount();

    //info 修改
    Result changeInfo(Long userId, UserInfo info);
    

    // 未解析的原始token
    Result logOut(String token); 

}   
