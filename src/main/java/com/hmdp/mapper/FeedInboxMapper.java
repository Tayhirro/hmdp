package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.FeedInbox;

/**
 * Feed 收件箱表 tb_feed_inbox 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。
 * 当前无调用方：全工程没有任何业务代码注入或调用本接口，实体 FeedInbox 已定义但未接入
 * Feed 读写链路（本项目 Feed 采用读时拉取加 Redis 快照，未实现推模式收件箱，见 FeedMode 说明）。
 */
public interface FeedInboxMapper extends BaseMapper<FeedInbox> {
}
