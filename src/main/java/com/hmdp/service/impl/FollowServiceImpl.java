package com.hmdp.service.impl;

/*
 * 现实业务背景：用户点击关注按钮、进入关注列表或查看共同关注时，需要读写 tb_follow 并协调缓存。
 * 实际触发：FollowController 的 follow、isFollow、getFollows、followCommons 四个方法进入本类。
 */

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.service.follow.FollowChangedEvent;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 
 *  服务实现类
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;
    private final FollowMapper followMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 关注或取关的完整流程：
     * 使用场景：用户在他人主页或博客卡片点击关注/取关按钮时，前端发送 PUT /follow/{id}/{isFollow}，
     * 由 FollowController.follow() 调用；项目内没有其他调用方。
     * 1. 当前用户从登录上下文取得，校验目标用户、目标状态，并禁止关注自己。
     * 2. {@code isFollow=true} 时用数据库唯一约束下的 insertIfAbsent（按 {@code user_id + follow_user_id} 插入 tb_follow，重复请求不会多插一行）
     *    新增关系；false 时按双方 ID 删除关系。
     * 3. 数据库操作成功后发布关注变更事件，由监听器在事务提交后同步/失效 Redis 关注 Set
     *    （每个用户一个 key：{@code follow:<用户ID>}，Set 成员是该用户关注的用户 ID）；方法处于事务中，异常会回滚关系写入。
     * 具体例子：用户 7 请求 {@code follow(9,true)} 后新增“7 关注 9”；重复请求不会新增第二行；
     * {@code follow(9,false)} 则删除这行，并通知 Feed/关注缓存更新。
     */
    @Override
    @Transactional
    public Result follow(Long followUserId, Boolean isFollow){
        Long userId = UserHolder.getUser().getId();
        String validationError = validateFollowRequest(userId, followUserId, isFollow);
        if (validationError != null) {
            return Result.fail(validationError);
        }

        if (Boolean.TRUE.equals(isFollow)) {
            followMapper.insertIfAbsent(userId, followUserId);
        } else {
            followMapper.deleteRelation(userId, followUserId);
        }
        eventPublisher.publishEvent(new FollowChangedEvent(userId, followUserId, isFollow));
        return Result.ok();
    }

    /**
     * 校验关注请求参数：目标用户 ID 非空、不能关注自己、关注状态非空。
     * 使用场景：仅被本类 follow 在写库前调用。
     * 实现要点：纯内存校验；返回错误文案（null 表示通过），由 follow 转成失败 Result。
     */
    private String validateFollowRequest(Long userId, Long followUserId, Boolean isFollow) {
        if (followUserId == null) {
            return "目标用户不能为空";
        }
        if (userId.equals(followUserId)) {
            return "不能关注自己";
        }
        if (isFollow == null) {
            return "关注状态不能为空";
        }
        return null;
    }

    /**
     * 查询关注列表的完整流程：校验被查看用户 ID，查询其全部关注关系，提取目标用户 ID，
     * 使用场景：查看某用户关注了哪些人时，前端发送 GET /follow/list/{id}，由 FollowController.getFollows() 调用。
     * 批量读取用户资料并转换为只包含公开摘要的 UserDTO；没有关系或用户时返回空列表。
     * 具体例子：用户 7 关注了 9、11，调用 {@code getFollows(7)} 会一次查出两条关系，再批量返回用户 9、11 的昵称和头像。
     */
    @Override
    public Result getFollows(Long userId){
        if (userId == null) {
            return Result.fail("用户ID不能为空");
        }
        List<Follow> follows = query().eq("user_id", userId).list();
        if (follows == null || follows.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> followUserIds = follows.stream().map(Follow::getFollowUserId).collect(Collectors.toList());
        List<User> users = userService.listByIds(followUserIds);
        if (users == null || users.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> userDTOs = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    /**
     * 查询共同关注的完整流程：取得当前登录用户 ID，在 Redis 中对双方的关注 Set 求交集
     * 使用场景：在他人主页查看“共同关注”时，前端发送 GET /follow/common/{id}，由 FollowController.followCommons() 调用。
     * （即 {@code SINTER follow:<我的ID> follow:<对方ID>}，key 由常量 {@code FOLLOW_KEY="follow:"} 拼用户 ID 组成），
     * 把交集 ID 批量查询为用户并转换成 DTO；交集为空时直接返回空列表。
     * 具体例子：当前用户 7 关注 {9,11}，用户 8 关注 {9,12}，{@code followCommons(8)} 返回用户 9。
     */
    @Override
    public Result followCommons(Long userId){
        if (userId == null) {
            return Result.fail("目标用户不能为空");
        }
        Long myId = UserHolder.getUser().getId();
        Set<String> followIds = stringRedisTemplate.opsForSet().intersect(FOLLOW_KEY + myId, FOLLOW_KEY + userId);
        if (followIds == null || followIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = followIds.stream().map(id -> Long.valueOf(id.toString())).collect(Collectors.toList());
        //follow-id  --->  user ---> userDTO
        List<User> followUsers = userService.listByIds(ids);
        List<UserDTO> userDTOs = followUsers.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    /**
     * 判断关注状态的完整流程：取得当前用户，先用 {@code SISMEMBER follow:<我的ID> <目标用户ID>} 检查 Redis 关注 Set；命中立即返回 true；
     * 使用场景：页面判断当前用户是否已关注某人（决定关注按钮状态）时，前端发送 GET /follow/or/not/{id}，由 FollowController.isFollow() 调用。
     * 未命中再查询 MySQL（条件 {@code user_id + follow_user_id}），数据库存在时回填 Redis，最终返回布尔值。
     * 具体例子：用户 7 是否关注用户 9，Redis 冷缓存（Set {@code follow:7} 里没有成员 9）时会查数据库关系并把 9 加回 {@code follow:7}，本次仍返回 true。
     */
    @Override
    public Result isFollow(Long followUserId){
        if (followUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        Long selfId = UserHolder.getUser().getId();
        // 先查询redis
        Boolean isFollow = stringRedisTemplate.opsForSet().isMember(FOLLOW_KEY + selfId, followUserId.toString());
        if (Boolean.TRUE.equals(isFollow)) {
            return Result.ok(true);
        }
        // redis 没命中时兜底数据库
        boolean isExist = query().eq("user_id", selfId).eq("follow_user_id", followUserId).one() != null;
        if (isExist) {
            stringRedisTemplate.opsForSet().add(FOLLOW_KEY + selfId, followUserId.toString());
        }
        return Result.ok(isExist);
    }
}
