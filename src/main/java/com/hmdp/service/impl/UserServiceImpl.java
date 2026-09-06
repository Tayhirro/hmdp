package com.hmdp.service.impl;

/*
 * 现实业务背景：用户从注册到登录、绑定手机、每日签到、修改资料和退出登录的完整身份流程集中在这里。
 * 实际触发：UserController 的 code/login/signup/bind-phone/sign/sign-count/logout/info 修改接口触发对应方法。
 */

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
 * 
 * 服务实现类
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
    
    /**
     * 发送登录/绑定验证码的完整流程：校验手机号格式，用 Redis SETNX 建立同一手机号的 60 秒发送锁
     * 使用场景：登录、注册或绑定手机号前获取验证码时，前端发送 POST /user/code?phone=，由 UserController.sendCode() 调用。
     * （key 为 {@code login:code:send:lock:<手机号>}，TTL 60 秒，由常量 LOGIN_CODE_SEND_LOCK_TTL 决定）；
     * 抢锁成功后生成 6 位数字并调用 {@link SmsSender}（短信发送接口，开发环境默认打印到日志），
     * 发送失败就删除发送锁允许重试，成功则把验证码保存到 {@code login:code:<手机号>}（TTL 2 分钟，由常量 LOGIN_CODE_TTL 决定）。
     * 具体例子：手机号 13800000000 首次请求收到 6 位码；20 秒后重复请求会提示还需等待约 40 秒，
     * 验证码过期后登录或绑定会返回“验证码已过期”。当前 token 认证不使用传入的 HttpSession。
     */
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
    /**
     * 登录的完整流程：先看请求是否带密码；带密码时按 account 或 phone 查询用户并用 BCrypt 校验，
     * 使用场景：用户提交登录表单时，前端发送 POST /user/login，由 UserController.login() 调用；
     * 三个登录分支分别由本类私有方法 signUpByAccountPassword/signUpByPhonePassword/signUpByPhoneCode 完成。
     * 不带密码时校验手机号和验证码、确认用户已经注册，并删除已使用验证码；三条分支成功后都会初始化缺失的用户资料，
     * 生成随机 token，把 UserDTO 逐字段写入 Redis Hash（key 为 {@code login:token:<token>}，
     * TTL 36000 秒即 10 小时，由常量 LOGIN_USER_TTL 决定），最后返回 token。
     * 具体例子：{@code {account:"tom",password:"***"}} 走账号密码分支；
     * {@code {phone:"13800000000",code:"123456"}} 走验证码分支。验证码登录不会自动注册，不存在的用户会被引导先注册。
     */
    @Override
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


    /**
     * 注册的完整流程：
     * 使用场景：用户提交注册表单时，前端发送 POST /user/signup，由 UserController.signUp() 调用。
     * 1. 有 account 时检查账号唯一并要求密码，使用 BCrypt 保存新用户；若同时提交 phone+code，则校验并绑定手机后直接签发 token，
     *    否则返回 requiresPhoneBinding=true，等待第二阶段绑定。
     * 2. 没有 account 时按手机号注册：校验手机号/验证码以及 phone、account 均未占用，创建用户（手机号同时作为账号），删除验证码并签发 token。
     * 具体例子：{@code {account:"tom",password:"***"}} 创建账号但暂不登录；随后绑定手机号后才拿 token。
     * {@code {phone:"13800000000",code:"123456"}} 则一次完成手机号注册和登录。
     */
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
    /**
     * 两阶段注册绑定手机的完整流程：校验 phone 和 code，从 account 找到刚创建但尚未登录的用户，
     * 使用场景：账号注册后第二步绑定手机号时，前端发送 POST /user/bind-phone，由 UserController.bindPhone() 调用。
     * 比对 Redis 验证码并确认该手机号未被其他用户占用，更新用户 phone，删除验证码，最后初始化资料并签发登录 token。
     * 具体例子：账号 tom 注册后提交 {@code {account:"tom",phone:"13800000000",code:"123456"}}，
     * 用户行绑定该手机号并得到 token；若手机号已经属于用户 9，则返回“手机号已存在”。当前 token 认证不使用 HttpSession。
     */
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

    /**
     * 修改个人资料的完整流程：userId 由 Controller 从登录上下文传入；先确保该用户有 tb_user_info 记录，
     * 使用场景：用户在个人主页编辑资料时，前端发送 PUT /user/info，由 UserController.changeInfo() 解析登录态得到 userId 后调用。
     * 再新建更新对象，只复制 city、introduce、gender、birthday 四个允许修改的字段并按 userId 更新，其他计数和等级不会被请求覆盖。
     * 具体例子：用户 7 请求把 city 改成“上海”并提交伪造 fans=999，本方法只保存“上海”等白名单资料字段，fans 保持服务端原值。
     */
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

    /**
     * 手机号验证码登录分支：校验验证码格式并与 Redis 中 {@code login:code:<手机号>} 比对，
     * 按 phone 查用户（不存在则提示先注册），删除已用验证码后签发 token。
     * 使用场景：仅被本类 login 在“不带密码”分支调用。
     * 实现要点：1 条 GET（验证码）、1 条 SELECT（tb_user 按 phone）、1 条 DEL（验证码），最后走 finalHandleSign 写登录 Hash。
     */
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

    /**
     * 账号密码登录分支：按 account 查用户，账号不存在或未设置密码、BCrypt 校验失败都返回统一的“账号或密码错误”，成功后签发 token。
     * 使用场景：仅被本类 login 在“带密码且填了 account”分支调用。
     * 实现要点：1 条 SELECT（tb_user 按 account），密码校验用 BCrypt.checkpw，无写操作。
     */
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

    /**
     * 手机号密码登录分支：按 phone 查用户，不存在提示先注册，BCrypt 校验失败返回“账号或密码错误”，成功后签发 token。
     * 使用场景：仅被本类 login 在“带密码且填了 phone”分支调用。
     * 实现要点：1 条 SELECT（tb_user 按 phone），无写操作。
     */
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
    /**
     * 登录/注册成功后的统一收尾：初始化缺失的用户资料，生成随机 token（UUID 去横线），
     * 把 UserDTO 各字段转成字符串后写入 Redis Hash（key 为 {@code login:token:<token>}，
     * TTL 由常量 LOGIN_USER_TTL 决定，36000 秒即 10 小时），最后返回 token 给前端。
     * 使用场景：被本类 login 的三个私有分支、signUp（手机号注册和绑定手机后）以及 bindPhone 调用，是项目里签发登录态的唯一出口。
     * 实现要点：1 条 HSET 多字段写入（忽略 null 字段）加 1 条 EXPIRE。
     */
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

    /**
     * 确保用户在 tb_user_info 有一行资料：不存在时保存默认资料（city/introduce 为空串，
     * fans/followee/credits/level 为 0，gender 为 2）。
     * 使用场景：被本类 finalHandleSign（每次签发登录态前）和 changeInfo（修改资料前）调用。
     * 实现要点：1 条 SELECT（userInfoService.getById）加命中缺失时 1 条 INSERT；插入异常仅记 warn 日志，不中断主流程。
     */
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


    /**
     * 统计本月连续签到天数的完整流程：取得当前用户和当前年月，从 Redis Bitmap 一次读出本月 1 日到今天的全部位；
     * 使用场景：用户进入签到页查看连续签到天数时，前端发送 GET /user/sign/count，由 UserController.signCount() 调用。
     * 然后从最低位（今天）向前逐位检查，遇到第一个 0 停止，返回连续的 1 的个数；无记录返回 0。
     * 具体例子：今天是 5 号，BITFIELD 一次读出本月 1～5 日共 5 个位（最低位对应 1 日）；若 3～5 日已签到，
     * 位图从高位到低位是 11100（十进制 28），从最低位（今天）向前逐位检查得到 3；
     * 即使 1 日签过，也不会跨过 2 日的断签继续累计。
     */
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

    /**
     * 当日签到的完整流程：取得当前用户和当前年月，构造每用户每月一个 Redis key（{@code sign:<userId>:<yyyyMM>}），
     * 使用场景：用户点击“签到”按钮时，前端发送 POST /user/sign，由 UserController.sign() 调用。
     * 用今天的日号减一作为 offset 执行 SETBIT true；相同用户同一天重复签到只是把同一位再次设为 1，结果天然幂等。
     * 具体例子：用户 7 在 2026-08-20 签到，会把 {@code sign:7:202608} 的第 19 位设为 1，不会创建 20 条独立记录。
     */
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

    /**
     * 退出登录的完整流程：允许 token 为空，去掉首尾空白和可选的 {@code Bearer } 前缀，
     * 使用场景：用户退出登录时，前端发送 POST /user/logout 并携带 Authorization 头，由 UserController.logout() 把 token 原样传入。
     * 再删除 Redis 中对应的登录 Hash；删除不存在的 key 也返回成功，所以重复退出是幂等的。
     * 具体例子：请求头 {@code Authorization: Bearer abc123} 最终删除 {@code login:token:abc123}，之后该 token 无法再恢复用户上下文。
     */
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


    /**
     * 创建“手机号即账号”的新用户：account 和 phone 都填手机号，昵称为 user_ 前缀加 10 位随机串（常量 USER_NICK_NAME_PREFIX），
     * 密码为默认初始密码（手机号后 6 位，见 defaultInitialPassword）的 BCrypt 哈希。
     * 使用场景：仅被本类 signUp 在“手机号注册且未填密码”分支调用。
     * 实现要点：1 条 INSERT（save 写 tb_user）。
     */
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

    /**
     * 创建带自定义密码的手机号用户：account 和 phone 都填手机号，昵称同 createUserWithPhone，密码为用户输入的 BCrypt 哈希。
     * 使用场景：仅被本类 signUp 在“手机号注册且填了密码”分支调用。
     * 实现要点：1 条 INSERT（tb_user）。
     */
    private User createUserWithPhoneAndPassword(String phone, String rawPassword) {
        User user = new User();
        user.setAccount(phone);
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPassword(BCrypt.hashpw(rawPassword));
        save(user);
        return user;
    }

    /**
     * 创建纯账号用户：只填 account 和 BCrypt 密码，phone 留空等待后续绑定，昵称同 createUserWithPhone。
     * 使用场景：仅被本类 signUp 在“账号注册”分支调用。
     * 实现要点：1 条 INSERT（tb_user）。
     */
    private User createUserWithAccount(String account, String rawPassword) {
        User user = new User();
        user.setAccount(account);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPassword(BCrypt.hashpw(rawPassword));
        save(user);
        return user;
    }

    /**
     * 绑定手机的共用实现：校验验证码格式、比对 Redis 验证码，确认该手机号未被其他用户占用
     * （查询结果存在且 ID 不同于当前用户则拒绝），按主键更新 tb_user.phone，最后删除已用验证码。
     * 使用场景：被本类 signUp（账号注册时顺带绑定）和 bindPhone（两阶段注册第二步）调用；
     * 校验失败抛 IllegalArgumentException，由调用方转成失败 Result。
     * 实现要点：1 条 GET（验证码）、1 条 SELECT（tb_user 按 phone）、1 条 UPDATE（phone）、1 条 DEL（验证码）；
     * 本方法不签发 token，登录态由调用方继续调用 finalHandleSign。
     */
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

    /**
     * 计算手机号注册用户的默认初始密码：取手机号后 6 位；手机号为空或空串时返回 123456，
     * 长度 1～5 位时原样返回。
     * 使用场景：仅被本类 createUserWithPhone 调用。
     * 实现要点：纯内存字符串处理，返回值随后被 BCrypt 加哈希后入库。
     */
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
