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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
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
    private RedisTemplate<String, Object> redisTemplate;

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
        if (userService.getById(followUserId) == null) {
            return Result.fail("目标用户不存在");
        }

        boolean existed = query().eq("user_id", userId).eq("follow_user_id", followUserId).one() != null;
        boolean needFollow = isFollow;
        if (needFollow == existed) {
            return Result.ok();
        }

        String key = FOLLOW_KEY + userId;
        String member = followUserId.toString();
        ensureFollowSetCached(userId);
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
                redisTemplate.opsForSet().add(key, member);
                return Result.ok();
            }
            redisTemplate.opsForSet().add(key, member);
        } else {
            boolean removed = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            boolean stillExist = query().eq("user_id", userId).eq("follow_user_id", followUserId).one() != null;
            if (!removed && stillExist) {
                return Result.fail("取消关注失败");
            }
            redisTemplate.opsForSet().remove(key, member);
        }
        return Result.ok();
    }

    @Override
    public Result getFollows(Long userId){
        if (userId == null) {
            return Result.fail("用户ID不能为空");
        }
        List<Long> followUserIds = listFollowUserIds(userId);
        if (followUserIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(toOrderedUserDTOs(followUserIds));
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
        List<Long> ids = getCommonFollowIds(myId, userId);
        if (ids.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(toOrderedUserDTOs(ids));
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
        if (selfId.equals(followUserId)) {
            return Result.ok(false);
        }
        ensureFollowSetCached(selfId);
        String key = FOLLOW_KEY + selfId;
        // 先查询redis
        Boolean isFollow = redisTemplate.opsForSet().isMember(key, followUserId.toString());
        if (isFollow != null) {
            return Result.ok(isFollow);
        }
        // redis 异常兜底数据库
        return Result.ok(query().eq("user_id", selfId).eq("follow_user_id", followUserId).one() != null);
    }

    private List<Long> getCommonFollowIds(Long myId, Long otherUserId) {
        ensureFollowSetCached(myId);
        ensureFollowSetCached(otherUserId);
        Set<Object> redisIds = redisTemplate.opsForSet().intersect(FOLLOW_KEY + myId, FOLLOW_KEY + otherUserId);
        if (redisIds != null && !redisIds.isEmpty()) {
            return redisIds.stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toList());
        }

        // Redis 为空时兜底数据库，避免缓存冷启动直接返回空
        List<Long> myFollowIds = listFollowUserIds(myId);
        if (myFollowIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> otherFollowIdSet = new HashSet<>(listFollowUserIds(otherUserId));
        if (otherFollowIdSet.isEmpty()) {
            return Collections.emptyList();
        }
        return myFollowIds.stream()
                .filter(otherFollowIdSet::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> listFollowUserIds(Long userId) {
        return query()
                .select("follow_user_id", "create_time")
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void ensureFollowSetCached(Long userId) {
        String key = FOLLOW_KEY + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        List<Long> followUserIds = listFollowUserIds(userId);
        if (followUserIds.isEmpty()) {
            return;
        }
        String[] members = followUserIds.stream()
                .map(String::valueOf)
                .toArray(String[]::new);
        redisTemplate.opsForSet().add(key, (Object[]) members);
    }

    private List<UserDTO> toOrderedUserDTOs(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<User> users = userService.listByIds(userIds);
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, UserDTO> userDTOMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> BeanUtil.copyProperties(user, UserDTO.class), (a, b) -> a));

        List<UserDTO> orderedUserDTOs = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            UserDTO userDTO = userDTOMap.get(userId);
            if (userDTO != null) {
                orderedUserDTOs.add(userDTO);
            }
        }
        return orderedUserDTOs;
    }
}
