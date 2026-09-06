package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.BlogImage;

/**
 * 博客图片资产表 tb_blog_image 的数据访问接口。
 *
 * 无自定义方法，单表 CRUD 继承自 MyBatis-Plus 的 BaseMapper。使用方：
 * 1. BlogImageServiceImpl（继承 ServiceImpl 并注入本接口）：upload 插入图片资产、
 *    deleteTemporaryImage 查询并删除临时图片、loadOwnedTemporaryImages、loadOwnedBlogImages、
 *    replaceBlogImages 按 ID 批量查询、bindToBlog、detachAllBoundImages 按条件更新绑定状态、
 *    cleanupExpiredTemporaryImages、cleanupDeletingImages 定时查询并清理过期或待删除图片。
 * 2. BlogQueryService.detail：按 blog_id 且 status 为 BOUND 查询博客已绑定图片的 ID 列表。
 */
public interface BlogImageMapper extends BaseMapper<BlogImage> {
}
