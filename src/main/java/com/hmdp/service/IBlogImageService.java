package com.hmdp.service;

/*
 * 现实业务背景：用户发布或编辑博客时，需要先上传图片、校验归属、绑定博客，并在移除后安全清理文件。
 * 实际触发：上传/删除图片接口、博客发布编辑删除命令和后台图片清理任务共同调用本接口的不同方法。
 * 状态机：每张图片在 tb_blog_image 有一行，status 走 TEMP（已上传、未绑定博客）
 * → BOUND（已绑定某篇博客，按 sortOrder 记录顺序）→ DELETING（待物理删除，由定时任务重试）。
 * 物理文件不在 MySQL 事务内：一律等事务提交成功后才真正删文件，回滚则文件保留。
 */

import com.hmdp.dto.BlogImageUploadDTO;
import com.hmdp.entity.BlogImage;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IBlogImageService {

    /**
     * 上传图片：写入磁盘并创建 TEMP 状态的图片资产记录；数据库插入失败时立即反向删除刚写入的文件。
     * 使用场景：POST /upload/blog（multipart/form-data，文件参数名 file）——登录用户在发布/编辑笔记页选择本地图片时触发；
     * 内部调用方：无其他 Service，仅 UploadController 调用。
     */
    BlogImageUploadDTO upload(MultipartFile file, Long userId);

    /**
     * 删除尚未绑定博客的 TEMP 图片：先条件更新抢占为 DELETING，再删文件和记录；失败则恢复回 TEMP 供重试。
     * 使用场景：DELETE /upload/blog/{imageId}——用户在发布/编辑页移除还没随博客发布出去的图片时触发；
     * 内部调用方：无其他 Service，仅 UploadController 调用。
     */
    void deleteTemporaryImage(Long imageId, Long userId);

    /**
     * 校验待发布图片列表：批量查一次库（selectBatchIds，1 条 SQL）确认全部存在，
     * 且每张都属于当前用户、状态为 TEMP、未绑定博客，最后按请求里的 ID 顺序返回。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogCommandService 的发布博客流程（前端 POST /blog 提交发布时校验图片）。
     */
    List<BlogImage> loadOwnedTemporaryImages(List<Long> imageIds, Long userId);

    /**
     * 把 TEMP 图片绑定到新发布的博客：按列表下标写 blogId、BOUND、sortOrder（0 起）和 bindTime。
     * 更新条件同时限制图片 ID、所有者、TEMP 状态和 blogId 为空，图片被并发抢用时整个发布事务回滚。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogCommandService 的发布博客流程（POST /blog 插入 tb_blog 成功后绑定图片）。
     */
    void bindToBlog(List<Long> imageIds, Long userId, Long blogId);

    /**
     * 编辑博客时替换完整图片列表：列表中每张图片要么是本人新上传的 TEMP 图片，要么是本博客原有的 BOUND 图片。
     * 不在新列表里的旧图片被标为 DELETING，保留的和新增的按新列表顺序重排 sortOrder。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogCommandService 的编辑博客流程（PUT /blog/{id} 全量替换图片）。
     *
     * @return 被移除且需要在事务提交后物理删除的图片资产
     */
    List<BlogImage> replaceBlogImages(List<Long> imageIds, Long userId, Long blogId);

    /** 校验编辑后的图片列表全部已 BOUND 在指定博客且属于当前用户，并按请求顺序返回（1 条批量 SQL）。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogCommandService 的编辑博客流程（PUT /blog/{id} 校验图片归属）。
     */
    List<BlogImage> loadOwnedBlogImages(List<Long> imageIds, Long userId, Long blogId);

    /**
     * 删除博客前解绑该博客下属于当前用户的全部 BOUND 图片，逐张条件更新为 DELETING；
     * 任一图片被并发改动就抛错，让外层的博客删除事务整体回滚。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogCommandService 的删除博客流程（DELETE /blog/{id}）。
     *
     * @return 解绑后需要在事务提交后物理删除的图片资产
     */
    List<BlogImage> detachAllBoundImages(Long userId, Long blogId);

    /**
     * 注册事务提交后的物理文件删除动作：有活动事务时挂 afterCommit 回调，提交成功才删文件；
     * 事务回滚则回调不执行、文件仍在。无事务时立即删除。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogCommandService 的编辑（PUT /blog/{id}）与删除（DELETE /blog/{id}）
     * 博客流程，处理 replaceBlogImages、detachAllBoundImages 返回的待删图片。
     */
    void schedulePhysicalDeletionAfterCommit(List<BlogImage> images);

    /**
     * 后台清理过期临时图片（BlogImageCleanupJob 定时触发）：删除创建时间早于保留期
     * （默认 24 小时）的 TEMP 图片，每批最多取 100 条；单张失败恢复 TEMP 并继续下一张，返回本批成功数。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogImageCleanupJob 定时任务（fixedDelay 默认 1 小时一轮）。
     */
    int cleanupExpiredTemporaryImages();

    /**
     * 后台重试 DELETING 图片（事务已提交但物理文件删除尚未完成）：每批最多取 100 条
     * 已到 nextRetryTime 的记录，删除成功则记录消失，失败则累加 retryCount 并默认延后 5 分钟再试。
     * 使用场景：无 HTTP 入口；内部调用方为 BlogImageCleanupJob 定时任务（fixedDelay 默认 1 小时一轮）。
     */
    int cleanupDeletingImages();
}
