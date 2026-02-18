package com.hmdp.utils;

import com.hmdp.dto.UserDTO;



// 当前thread请求的数据  持有用户  
// static 方法 持有 static 字段 ---- 存储在heap中 
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
    public static void saveUser(UserDTO user){
        tl.set(user);
    }
    public static UserDTO getUser(){
        return tl.get();
    }
    public static void removeUser(){
        tl.remove();
    }
}
