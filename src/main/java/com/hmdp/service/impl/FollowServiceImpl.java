package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;

import cn.hutool.core.bean.BeanUtil;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow){
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();
        if (followUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        if (userId.equals(followUserId)) {
            return Result.fail("不能关注自己");
        }
        if (isFollow == null) {
            return Result.fail("关注状态不能为空");
        }

        boolean existed = query().eq("user_id", userId).eq("follow_user_id", followUserId).one() != null;
        boolean needFollow = isFollow;
        if (needFollow == existed) {
            return Result.ok();
        }

        String key = FOLLOW_KEY + userId;
        String member = followUserId.toString();
        if (needFollow) {
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            try{
                boolean saved = save(follow);
                if (!saved) {
                    return Result.fail("关注失败");
                }
            }catch (DuplicateKeyException e){
                stringRedisTemplate.opsForSet().add(key, member);
                return Result.ok();
            }
            stringRedisTemplate.opsForSet().add(key, member);
        } else {
            boolean removed = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (!removed) {
                // 并发下可能已被其他请求删除，此时按幂等成功处理
                boolean existedNow = query().eq("user_id", userId).eq("follow_user_id", followUserId).one() != null;
                if (existedNow) {
                    return Result.fail("取消关注失败");
                }
            }
            stringRedisTemplate.opsForSet().remove(key, member);
        }
        return Result.ok();
    }

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

    @Override
    public Result followCommons(Long userId){
        UserDTO current = UserHolder.getUser();
        if (current == null || current.getId() == null) {
            return Result.fail("用户未登录");
        }
        if (userId == null) {
            return Result.fail("目标用户不能为空");
        }
        Long myId = current.getId();
        Set<Object> followIds = stringRedisTemplate.opsForSet().intersect(FOLLOW_KEY + myId, FOLLOW_KEY + userId);
        if (followIds == null || followIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = followIds.stream().map(id -> Long.valueOf(id.toString())).collect(Collectors.toList());
        //follow-id  --->  user ---> userDTO
        List<User> followUsers = userService.listByIds(ids); 
        List<UserDTO> userDTOs = followUsers.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    @Override 
    public Result isFollow(Long followUserId){
        UserDTO current = UserHolder.getUser();
        if (current == null || current.getId() == null) {
            return Result.fail("用户未登录");
        }
        if (followUserId == null) {
            return Result.fail("目标用户不能为空");
        }
        Long selfId = current.getId();
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
