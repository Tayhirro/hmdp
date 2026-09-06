package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import javax.annotation.Resource;

/**
 * 
 *  关注关系前端控制器（根路径 {@code /follow}），提供关注/取关、关注状态查询、关注列表与共同关注；
 *  关系真相在 tb_follow，Redis 侧为每个用户维护一个关注 Set（key 为 follow:{用户ID}）。
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注/取关（按目标状态，兼容原前端）。
     * 使用场景：登录用户在其他用户主页点击“关注/取消关注”时，前端发送 PUT /follow/{目标用户ID}/{isFollow}
     * （isFollow 为 true/false 布尔路径参数）。
     * 数据库/Redis：isFollow=true 时按 user_id + follow_user_id 以 insertIfAbsent 写 tb_follow
     * （唯一约束保证重复请求不多插一行），false 时按双方 ID 删除关系；事务提交后由监听器
     * 根据 FollowChangedEvent 同步 Redis 关注 Set（follow:{用户ID}）。
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 查询当前用户是否关注目标用户。
     * 使用场景：前端渲染他人主页、决定“关注/已关注”按钮状态时，发送 GET /follow/or/not/{目标用户ID}。
     * 数据库/Redis：先查 Redis 关注 Set 成员（SISMEMBER follow:{我的ID}），未命中再按
     * user_id + follow_user_id 查 tb_follow，命中数据库则把目标 ID 回填 Redis。
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 查询指定用户的关注列表。
     * 使用场景：用户查看某人主页的“关注列表”时，前端发送 GET /follow/list/{用户ID}。
     * 数据库：按 user_id 查 tb_follow 全部关注关系，再按目标用户 ID 批量查 tb_user 装配为公开摘要。
     */
    @GetMapping("/list/{id}")
    public Result getFollows(@PathVariable("id") Long userId) {
        return followService.getFollows(userId);
    }

    /**
     * 查询当前用户与目标用户的共同关注。
     * 使用场景：用户进入他人主页查看“共同关注”时，前端发送 GET /follow/common/{对方用户ID}。
     * Redis/数据库：对 follow:{我的ID} 与 follow:{对方ID} 两个关注 Set 求交集（SINTER），
     * 交集用户 ID 再批量查 tb_user 装配；交集为空直接返回空列表。
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long otherUserId) {
        return followService.followCommons(otherUserId);
    }
}
