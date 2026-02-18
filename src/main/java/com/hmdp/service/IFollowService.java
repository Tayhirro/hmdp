package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.Follow;
import com.hmdp.dto.Result;
/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {
    Result follow(Long followUserId, Boolean isFollow); // 按目标状态关注/取关
    Result isFollow(Long followUserId); // 是否已关注
    Result getFollows(Long userId);
    Result followCommons(Long otherUserId); // 获取共同关注
}
