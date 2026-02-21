package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.sms.SmsSender;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.core.util.RandomUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SmsSender smsSender;

    @Resource
    private IUserInfoService userInfoService;
    
    @Override
    public Result sendCode(String phone, HttpSession session){
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 发送频率限制：同一手机号 60s 内只能发送一次
        String sendLockKey = LOGIN_CODE_SEND_LOCK_KEY + phone;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(sendLockKey, "1", LOGIN_CODE_SEND_LOCK_TTL, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            Long ttl = stringRedisTemplate.getExpire(sendLockKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                return Result.fail("发送太频繁，请" + ttl + "秒后再试");
            }
            return Result.fail("发送太频繁，请稍后再试");
        }
        // 2.生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.发送验证码（开发环境默认打印到日志；生产环境替换 SmsSender 实现接入短信平台）
        try {
            smsSender.sendLoginCode(phone, code);
        } catch (RuntimeException e) {
            log.warn("Failed to send SMS login code, phone={}", phone, e);
            stringRedisTemplate.delete(sendLockKey);
            return Result.fail("验证码发送失败，请稍后再试");
        }
        // 4.保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }
    @Override

    // 登录
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String password = loginForm.getPassword();
        if (StrUtil.isNotBlank(password)) { // 账号密码登录
            String account = StrUtil.trimToNull(loginForm.getAccount());
            if (account != null) {
                return signUpByAccountPassword(account, password);
            }
            String phone = StrUtil.trimToNull(loginForm.getPhone());
            if (phone != null) {
                if (RegexUtils.isPhoneInvalid(phone)) {
                    return Result.fail("手机号格式错误！");
                }
                return signUpByPhonePassword(phone, password);
            }
            return Result.fail("请输入账号或手机号！");
        }else{
            // 手机号验证码登录
            String phone = StrUtil.trimToNull(loginForm.getPhone());
            if (phone == null || RegexUtils.isPhoneInvalid(phone)) {
                return Result.fail("手机号格式错误！");
            }
            String code = StrUtil.trimToNull(loginForm.getCode());
            if (code == null) {
                return Result.fail("请输入验证码！");
            }
            return signUpByPhoneCode(phone, code);
        }
    }


    // 注册手机号
    @Override
    public Result signUp(LoginFormDTO signUpForm, HttpSession session) {
        String account = StrUtil.trimToNull(signUpForm.getAccount());
        if (account != null) {
            // 账号注册（用户名/邮箱风格）：只创建账号 + 密码，手机号后续可绑定
            if (query().eq("account", account).one() != null) {
                return Result.fail("账号已存在");
            }
            String password = StrUtil.trimToNull(signUpForm.getPassword());
            if (password == null) {
                return Result.fail("请输入密码！");
            }
            User user = createUserWithAccount(account, password);

            // 如果注册时同时带了手机号+验证码，则顺便绑定
            String phone = StrUtil.trimToNull(signUpForm.getPhone());
            String code = StrUtil.trimToNull(signUpForm.getCode());
            if (phone != null || code != null) {
                if (phone == null || RegexUtils.isPhoneInvalid(phone)) {
                    return Result.fail("手机号格式错误！");
                }
                if (code == null) {
                    return Result.fail("请输入验证码！");
                }
                try {
                    bindPhoneInternal(user.getId(), phone, code);
                } catch (IllegalArgumentException e) {
                    return Result.fail(e.getMessage());
                }
                // 如果提供了手机号和验证码，完成绑定后才返回 token
                return finalHandleSign(user);
             }
             // 如果只提供了账号密码，暂不登录，返回成功状态
             Map<String, Object> data = new HashMap<>();
             data.put("requiresPhoneBinding", true);
             data.put("message", "账号注册成功，请绑定手机号");
             return Result.ok(data);
         }else{
             // 手机号注册：把手机号作为 account
             String phone = StrUtil.trimToNull(signUpForm.getPhone());
             if (phone == null || RegexUtils.isPhoneInvalid(phone)) {
                return Result.fail("手机号格式错误！");
            }
            if (query().eq("phone", phone).one() != null) {
                return Result.fail("手机号已存在");
            }
            if (query().eq("account", phone).one() != null) {
                return Result.fail("账号已存在");
            }
            String code = StrUtil.trimToNull(signUpForm.getCode());
            if (code == null) {
                return Result.fail("请输入验证码！");
            }
            if (RegexUtils.isCodeInvalid(code)) {
                return Result.fail("验证码格式错误！");
            }
            String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
            if (cacheCode == null) {
                return Result.fail("验证码已过期！");
            }
            if (!cacheCode.equals(code)) {
                return Result.fail("验证码错误！");
            }
            // 如果 密码 + 手机号  |  手机号  -- 默认手机号注册密码都是 null
            String password = StrUtil.trimToNull(signUpForm.getPassword());
            User user = password == null ? createUserWithPhone(phone) : createUserWithPhoneAndPassword(phone, password);
            stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
            return finalHandleSign(user);
        }
        
    }
    // 绑定手机
    @Override
    public Result bindPhone(LoginFormDTO bindPhoneForm, HttpSession session) {
        // 对于两阶段注册，用户可能还未登录，需要通过账号查找用户
        String account = StrUtil.trimToNull(bindPhoneForm.getAccount());
        String phone = StrUtil.trimToNull(bindPhoneForm.getPhone());
        String code = StrUtil.trimToNull(bindPhoneForm.getCode());
        if (phone == null || RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        
        if (code == null) {
            return Result.fail("请输入验证码！");
        }
        // 查找用户
        User user = query().eq("account", account).one();
        
        // 验证验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            return Result.fail("验证码已过期！");
        }
        if (!cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }
        
        try {
            bindPhoneInternal(user.getId(), phone, code);
            stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
            // 绑定成功后返回 token
            return finalHandleSign(user);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @Override
    public Result changeInfo(Long userId, UserInfo info) {
        if (info == null) {
            return Result.fail("请求参数不能为空");
        }
        // 确保用户资料存在，再进行更新
        User user = new User();
        user.setId(userId);
        initUserInfoIfAbsent(user);

        UserInfo update = new UserInfo();
        update.setUserId(userId);
        update.setCity(info.getCity());
        update.setIntroduce(info.getIntroduce());
        update.setGender(info.getGender());
        update.setBirthday(info.getBirthday());

        boolean success = userInfoService.updateById(update);
        if (!success) {
            return Result.fail("更新个人资料失败");
        }
        return Result.ok();
    }

    private Result signUpByPhoneCode(String phone, String code) {
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误！");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            return Result.fail("验证码已过期！");
        }
        if (!cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }

        User user = query().eq("phone", phone).one();
        if (user == null) {
            return Result.fail("用户不存在，请先注册或绑定手机号");
        }

        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        return finalHandleSign(user);
    }

    private Result signUpByAccountPassword(String account, String password) {
        User user = query().eq("account", account).one();
        if (user == null || StrUtil.isBlank(user.getPassword())) {
            return Result.fail("账号或密码错误！");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            return Result.fail("账号或密码错误！");
        }
        return finalHandleSign(user);
    }

    private Result signUpByPhonePassword(String phone, String password) {
        User user = query().eq("phone", phone).one();
        if (user == null ) {
            return Result.fail("用户不存在，请先注册或绑定手机号");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            return Result.fail("账号或密码错误！");
        }
        return finalHandleSign(user);
    }


    // 生成并返回 token  -- 记录数据在 redis 中
    private Result finalHandleSign(User user) {
        initUserInfoIfAbsent(user);
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //属性转 string
        CopyOptions copyOptions = CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString());
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), copyOptions);

        String tokenKey = LOGIN_USER_KEY + token;
        //稳定序列化
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);
        log.debug("Issued login token, userId={}, tokenKey={}", user.getId(), tokenKey);
        return Result.ok(token);
    }

    private void initUserInfoIfAbsent(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        Long userId = user.getId();
        UserInfo existed = userInfoService.getById(userId);
        if (existed != null) {      //userInfo已存在
            return; 
        }
        UserInfo info = new UserInfo();
        info.setUserId(userId);
        info.setCity("");
        info.setIntroduce("");
        info.setFans(0);
        info.setFollowee(0);
        info.setGender(2);
        info.setCredits(0);
        info.setLevel(0);
        try {
            userInfoService.save(info);
        } catch (Exception e) {
            log.warn("Init tb_user_info failed, userId={}", userId, e);
        }
    }


    // 统计签到天数
    @Override
    public Result signCount() {
        // TODO Auto-generated method stub
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        // 
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );  
        if(result == null || result.isEmpty()){
            // 没有签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.统计连续签到天数：从“今天”开始往前数，遇到 0 就停止
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            }
            count++;
            num >>>= 1;
        }
        return Result.ok(count);
       
    }

    // 签到
    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key  2026-02-03 --- 202602
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset true    ---- bit(按照string byte数组 --字节来存)
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result logOut(String token){
        //如果 token为空 则直接返回
        if (StrUtil.isBlank(token)) {
            return Result.ok();
        }
        String token_normal = token.trim();
        if(StrUtil.startWithIgnoreCase(token_normal,"Bearer ")){
            token_normal = token_normal.substring("Bearer ".length()).trim();
        }
        // 如果 token 为空则直接返回
        if(StrUtil.isBlank(token_normal)){
            return Result.ok();
        }
        stringRedisTemplate.delete(LOGIN_USER_KEY + token_normal);
        return Result.ok();
    }


    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setAccount(phone);
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPassword(BCrypt.hashpw(defaultInitialPassword(phone)));
        // 2.保存用户
        save(user);
        return user;
    }

    private User createUserWithPhoneAndPassword(String phone, String rawPassword) {
        User user = new User();
        user.setAccount(phone);
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPassword(BCrypt.hashpw(rawPassword));
        save(user);
        return user;
    }

    private User createUserWithAccount(String account, String rawPassword) {
        User user = new User();
        user.setAccount(account);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPassword(BCrypt.hashpw(rawPassword));
        save(user);
        return user;
    }

    private void bindPhoneInternal(Long userId, String phone, String code) {
        if (RegexUtils.isCodeInvalid(code)) {
            throw new IllegalArgumentException("验证码格式错误！");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            throw new IllegalArgumentException("验证码已过期！");
        }
        if (!cacheCode.equals(code)) {
            throw new IllegalArgumentException("验证码错误！");
        }

        User other = query().eq("phone", phone).one();
        if (other != null && !other.getId().equals(userId)) {
            throw new IllegalArgumentException("手机号已存在");
        }

        User update = new User();
        update.setId(userId);
        update.setPhone(phone);
        updateById(update);
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
    }

    private static String defaultInitialPassword(String phone) {
        if (phone == null) {
            return "123456";
        }
        String p = phone.trim();
        if (p.length() >= 6) {
            return p.substring(p.length() - 6);
        }
        if (p.isEmpty()) {
            return "123456";
        }
        return p;
 
    }
}
