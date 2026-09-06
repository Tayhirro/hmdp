package com.hmdp.service.impl;

/*
 * 现实业务背景：用户选择、移除、发布或重新编辑博客图片时，文件与数据库资产状态必须一起受控。
 * 实际触发：UploadController、BlogCommandService 和 BlogImageCleanupJob 分别触发上传、绑定/解绑和补偿清理。
 */

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.config.BlogImageProperties;
import com.hmdp.dto.BlogImageUploadDTO;
import com.hmdp.entity.BlogImage;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogImageMapper;
import com.hmdp.service.IBlogImageService;
import com.hmdp.service.storage.BlogImageStorage;
import com.hmdp.service.storage.StoredBlogImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class BlogImageServiceImpl implements IBlogImageService {

    private static final int MAX_DELETION_ERROR_LENGTH = 1000;

    private final BlogImageMapper blogImageMapper;
    private final BlogImageStorage storage;
    private final BlogImageProperties properties;

    /**
     * 构造函数：注入图片 Mapper、存储层与上传配置（由 Spring 装配本 Service 时调用一次，仅字段赋值，无业务逻辑）。
     */
    public BlogImageServiceImpl(
            BlogImageMapper blogImageMapper,
            BlogImageStorage storage,
            BlogImageProperties properties
    ) {
        this.blogImageMapper = blogImageMapper;
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * 上传博客图片的完整流程：校验调用方已有用户 ID，把文件交给存储层校验并写入磁盘，
     * 使用场景：登录用户在“发布/编辑笔记”页上传图片时，前端以 multipart/form-data 发送 POST /upload/blog（参数名 file），
     * 由 UploadController.uploadImage() 调用；项目内没有其他调用方。
     * 再用返回的路径、URL、类型、大小和宽高创建 TEMP 图片资产记录；数据库插入失败时立即反向删除刚写入的文件。
     * 具体例子：用户 7 上传 a.jpg 后，磁盘生成唯一 storageKey，数据库保存 {@code userId=7,status=TEMP}，
     * 接口只返回 imageId 和 publicUrl；这时图片还没有属于任何博客。
     */
    @Override
    public BlogImageUploadDTO upload(MultipartFile file, Long userId) {
        requireUser(userId);
        StoredBlogImage stored = storage.store(file);

        BlogImage image = new BlogImage()
                .setUserId(userId)
                .setStorageKey(stored.getStorageKey())
                .setPublicUrl(stored.getPublicUrl())
                .setContentType(stored.getContentType())
                .setFileSize(stored.getFileSize())
                .setWidth(stored.getWidth())
                .setHeight(stored.getHeight())
                .setStatus(BlogImage.STATUS_TEMP);
        try {
            if (blogImageMapper.insert(image) != 1) {
                throw new IllegalStateException("创建图片资产记录失败");
            }
        } catch (RuntimeException e) {
            deleteStoredFileQuietly(stored.getStorageKey());
            throw e;
        }
        return new BlogImageUploadDTO(image.getId(), image.getPublicUrl());
    }

    /**
     * 删除未发布图片的完整流程：校验图片存在、属于当前用户、仍为 TEMP 且尚未绑定博客，
     * 使用场景：用户在发布/编辑页移除尚未随博客发布的图片时，前端发送 DELETE /upload/blog/{imageId}，
     * 由 UploadController.deleteBlogImage() 调用；项目内没有其他调用方。
     * 再用条件更新（{@code id + user_id + status=TEMP + blog_id IS NULL}）抢占为 DELETING，
     * 随后删除物理文件和数据库记录；文件删除失败时把状态恢复成 TEMP 供用户重试。
     * 具体例子：用户 7 可以删除自己尚未发布的图片 501；用户 8 删除 501，或 501 已绑定博客 100，都会在物理文件操作前被拒绝。
     */
    @Override
    public void deleteTemporaryImage(Long imageId, Long userId) {
        requireUser(userId);
        if (imageId == null) {
            throw new BusinessException("图片ID不能为空");
        }

        BlogImage image = blogImageMapper.selectById(imageId);
        validateTemporaryOwnership(image, userId);
        if (!claimForDeletion(image.getId(), userId)) {
            throw new BusinessException("图片状态已变化，请刷新后重试");
        }

        try {
            storage.delete(image.getStorageKey());
            blogImageMapper.deleteById(image.getId());
        } catch (RuntimeException e) {
            restoreTemporaryStatus(image.getId(), userId);
            throw e;
        }
    }

    /**
     * 校验待发布图片的完整流程：要求 ID 列表非空、无 null、无重复，批量查询后确认一张不少；
     * 使用场景：发布博客时由 BlogCommandService.publish()（POST /blog，事务内）调用，校验请求中的临时图片可用。
     * 每张图片还必须属于当前用户、状态为 TEMP 且没有 blogId，最后按请求中的 ID 顺序重新排列返回。
     * 具体例子：用户 7 提交 [502,501]，即使数据库批量查询返回 [501,502]，方法仍按 [502,501] 返回，
     * 后续发布就以 502 为首图；只要其中一张属于用户 8，整个发布请求都会失败。
     */
    @Override
    public List<BlogImage> loadOwnedTemporaryImages(List<Long> imageIds, Long userId) {
        requireUser(userId);
        if (imageIds == null || imageIds.isEmpty()) {
            throw new BusinessException("请至少上传一张图片");
        }

        Set<Long> distinctIds = new LinkedHashSet<>(imageIds);
        if (distinctIds.contains(null) || distinctIds.size() != imageIds.size()) {
            throw new BusinessException("图片ID不能为空或重复");
        }

        List<BlogImage> images = blogImageMapper.selectBatchIds(distinctIds);
        if (images == null || images.size() != distinctIds.size()) {
            throw new BusinessException("存在无效的图片");
        }

        Map<Long, BlogImage> imageById = new HashMap<>(images.size());
        for (BlogImage image : images) {
            validateTemporaryOwnership(image, userId);
            imageById.put(image.getId(), image);
        }

        List<BlogImage> orderedImages = new ArrayList<>(imageIds.size());
        for (Long imageId : imageIds) {
            orderedImages.add(imageById.get(imageId));
        }
        return orderedImages;
    }

    /**
     * 将临时图片绑定新博客的完整流程：逐张按列表下标设置 blogId、BOUND、sortOrder 和 bindTime；
     * 使用场景：发布博客时由 BlogCommandService.publish()（同一事务内，位于博客行插入之后）调用。
     * 更新条件同时限制图片 ID、所有者、TEMP 状态和未绑定状态，防止并发请求抢用同一张图；
     * 若图片已经被同一请求绑定到相同博客和顺序，则视为幂等成功，否则中止事务。
     * 具体例子：博客 100 使用 [502,501] 时，502 的 sortOrder=0、501=1；若 501 已被博客 99 占用，博客 100 的发布会整体回滚。
     */
    @Override
    public void bindToBlog(List<Long> imageIds, Long userId, Long blogId) {
        requireUser(userId);
        if (blogId == null) {
            throw new IllegalArgumentException("博客ID不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < imageIds.size(); index++) {
            Long imageId = imageIds.get(index);
            int updated = blogImageMapper.update(
                    null,
                    new LambdaUpdateWrapper<BlogImage>()
                            .eq(BlogImage::getId, imageId)
                            .eq(BlogImage::getUserId, userId)
                            .eq(BlogImage::getStatus, BlogImage.STATUS_TEMP)
                            .isNull(BlogImage::getBlogId)
                            .set(BlogImage::getBlogId, blogId)
                            .set(BlogImage::getStatus, BlogImage.STATUS_BOUND)
                            .set(BlogImage::getSortOrder, index)
                            .set(BlogImage::getBindTime, now)
            );
            if (updated != 1) {
                BlogImage current = blogImageMapper.selectById(imageId);
                boolean alreadyBoundBySameRequest = current != null
                        && userId.equals(current.getUserId())
                        && blogId.equals(current.getBlogId())
                        && BlogImage.STATUS_BOUND.equals(current.getStatus())
                        && Integer.valueOf(index).equals(current.getSortOrder());
                if (!alreadyBoundBySameRequest) {
                    throw new BusinessException("图片状态已变化，发布失败");
                }
            }
        }
    }

    /**
     * 编辑时替换完整图片列表的完整流程：先校验请求图片无空值/重复，并确认每张要么是本人新上传的 TEMP 图片，
     * 使用场景：编辑博客时由 BlogCommandService.update()（PUT /blog/{id}，事务内）调用。
     * 要么是本博客原有的 BOUND 图片；再读取当前图片，把不再保留的标为 DELETING，绑定新增图片并按新列表重排全部图片，返回待物理删除列表。
     * 具体例子：博客 100 原有 [501,502]，请求改为 [502,503]，结果是 501→DELETING、502→sortOrder 0、
     * 503 从 TEMP 绑定并设为 sortOrder 1；若请求夹带别人的 504，任何状态都不会改变。
     */
    @Override
    public List<BlogImage> replaceBlogImages(List<Long> imageIds, Long userId, Long blogId) {
        requireUser(userId);
        requireDistinctImageIds(imageIds);
        List<BlogImage> requested = blogImageMapper.selectBatchIds(imageIds);
        if (requested == null || requested.size() != imageIds.size()) {
            throw new BusinessException("存在无效的图片");
        }
        Map<Long, BlogImage> requestedById = new HashMap<>();
        for (BlogImage image : requested) {
            boolean ownedTemporary = userId.equals(image.getUserId())
                    && BlogImage.STATUS_TEMP.equals(image.getStatus())
                    && image.getBlogId() == null;
            boolean retainedBound = userId.equals(image.getUserId())
                    && BlogImage.STATUS_BOUND.equals(image.getStatus())
                    && blogId.equals(image.getBlogId());
            if (!ownedTemporary && !retainedBound) {
                throw new BusinessException("图片不属于当前博客或不可绑定");
            }
            requestedById.put(image.getId(), image);
        }

        List<BlogImage> current = blogImageMapper.selectList(new LambdaQueryWrapper<BlogImage>()
                .eq(BlogImage::getUserId, userId)
                .eq(BlogImage::getBlogId, blogId)
                .eq(BlogImage::getStatus, BlogImage.STATUS_BOUND));
        Set<Long> requestedIds = new LinkedHashSet<>(imageIds);
        List<BlogImage> removed = new ArrayList<>();
        for (BlogImage image : current) {
            if (!requestedIds.contains(image.getId())) {
                int updated = blogImageMapper.update(null, new LambdaUpdateWrapper<BlogImage>()
                        .eq(BlogImage::getId, image.getId())
                        .eq(BlogImage::getUserId, userId)
                        .eq(BlogImage::getBlogId, blogId)
                        .eq(BlogImage::getStatus, BlogImage.STATUS_BOUND)
                        .set(BlogImage::getStatus, BlogImage.STATUS_DELETING)
                        .set(BlogImage::getRetryCount, 0)
                        .set(BlogImage::getLastError, null)
                        .set(BlogImage::getNextRetryTime, nextDeletionRetryTime()));
                if (updated != 1) {
                    throw new BusinessException("图片状态已变化，请重试");
                }
                removed.add(image.setStatus(BlogImage.STATUS_DELETING));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < imageIds.size(); index++) {
            BlogImage image = requestedById.get(imageIds.get(index));
            LambdaUpdateWrapper<BlogImage> update = new LambdaUpdateWrapper<BlogImage>()
                    .eq(BlogImage::getId, image.getId())
                    .eq(BlogImage::getUserId, userId);
            if (BlogImage.STATUS_TEMP.equals(image.getStatus())) {
                update.eq(BlogImage::getStatus, BlogImage.STATUS_TEMP)
                        .isNull(BlogImage::getBlogId)
                        .set(BlogImage::getBlogId, blogId)
                        .set(BlogImage::getStatus, BlogImage.STATUS_BOUND)
                        .set(BlogImage::getBindTime, now);
            } else {
                update.eq(BlogImage::getStatus, BlogImage.STATUS_BOUND)
                        .eq(BlogImage::getBlogId, blogId);
            }
            update.set(BlogImage::getSortOrder, index);
            if (blogImageMapper.update(null, update) != 1) {
                throw new BusinessException("图片状态已变化，请重试");
            }
        }
        return removed;
    }

    /**
     * 读取博客已绑定图片的完整流程：校验列表非空且 ID 唯一，批量加载并确认数量完全一致，
     * 使用场景：编辑博客时由 BlogCommandService.update() 在 replaceBlogImages 之后调用，
     * 用校验通过的图片顺序生成博客详情的 images 字段。
     * 逐张检查“当前用户所有 + 属于指定博客 + BOUND”，最后按请求顺序返回，用于生成博客 images URL 字段。
     * 具体例子：编辑博客 100 后传 [502,503]，方法只在两张图都已正确绑定时按该顺序返回；属于博客 101 的图片会被拒绝。
     */
    @Override
    public List<BlogImage> loadOwnedBlogImages(List<Long> imageIds, Long userId, Long blogId) {
        requireUser(userId);
        requireDistinctImageIds(imageIds);
        List<BlogImage> images = blogImageMapper.selectBatchIds(imageIds);
        if (images == null || images.size() != imageIds.size()) {
            throw new BusinessException("存在无效的图片");
        }
        Map<Long, BlogImage> byId = new HashMap<>();
        for (BlogImage image : images) {
            if (!userId.equals(image.getUserId())
                    || !blogId.equals(image.getBlogId())
                    || !BlogImage.STATUS_BOUND.equals(image.getStatus())) {
                throw new BusinessException("图片不属于当前博客");
            }
            byId.put(image.getId(), image);
        }
        List<BlogImage> ordered = new ArrayList<>(imageIds.size());
        for (Long imageId : imageIds) {
            ordered.add(byId.get(imageId));
        }
        return ordered;
    }

    /**
     * 删除博客前解绑全部图片的完整流程：查询该用户、该博客下全部 BOUND 图片，逐张用条件更新改为 DELETING，
     * 使用场景：删除博客时由 BlogCommandService.delete()（DELETE /blog/{id}，事务内）调用。
     * 清零重试信息并设置下次可重试时间；任一图片并发变更都会抛错使外层博客删除事务回滚，最后返回待删资产。
     * 具体例子：用户 7 删除博客 100 时，图片 501、502 先进入 DELETING；事务成功后才能删文件，事务失败则状态更新一起回滚。
     */
    @Override
    public List<BlogImage> detachAllBoundImages(Long userId, Long blogId) {
        requireUser(userId);
        List<BlogImage> images = blogImageMapper.selectList(new LambdaQueryWrapper<BlogImage>()
                .eq(BlogImage::getUserId, userId)
                .eq(BlogImage::getBlogId, blogId)
                .eq(BlogImage::getStatus, BlogImage.STATUS_BOUND));
        for (BlogImage image : images) {
            int updated = blogImageMapper.update(null, new LambdaUpdateWrapper<BlogImage>()
                    .eq(BlogImage::getId, image.getId())
                    .eq(BlogImage::getUserId, userId)
                    .eq(BlogImage::getBlogId, blogId)
                    .eq(BlogImage::getStatus, BlogImage.STATUS_BOUND)
                    .set(BlogImage::getStatus, BlogImage.STATUS_DELETING)
                    .set(BlogImage::getRetryCount, 0)
                    .set(BlogImage::getLastError, null)
                    .set(BlogImage::getNextRetryTime, nextDeletionRetryTime()));
            if (updated != 1) {
                throw new BusinessException("图片状态已变化，请重试");
            }
            image.setStatus(BlogImage.STATUS_DELETING);
        }
        return images;
    }

    /**
     * 安排物理文件删除的完整流程：空列表直接结束；没有活动事务时立即删除；有事务时注册 afterCommit 回调，
     * 使用场景：编辑和删除博客时由 BlogCommandService.update()/delete() 调用，
     * 传入 replaceBlogImages/detachAllBoundImages 返回的待删资产。
     * 只有 MySQL 真正提交后才逐张删除文件和 DELETING 元数据，失败记录保留给定时任务重试。
     * 具体例子：编辑博客把图片 501 移除后，如果数据库事务回滚，回调不会执行、文件仍在；提交成功后才删除 501。
     */
    @Override
    public void schedulePhysicalDeletionAfterCommit(List<BlogImage> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        Runnable deletion = () -> deletePhysicalAssets(images);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletion.run();
            return;
        }
        // 外部文件不参与 MySQL 事务：必须提交后再删，避免数据库回滚却丢失图片。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 提交回调的完整流程：事务提交成功后执行上面准备好的删除任务；
             * 使用场景：Spring 事务基础设施在 MySQL 提交成功后回调本方法，不由业务代码直接调用。
             * 具体例子：事务回滚时本方法不会触发，因此不会误删仍被博客引用的文件。
             */
            @Override
            public void afterCommit() {
                deletion.run();
            }
        });
    }

    /**
     * 逐张删除一批图片的物理文件和 DELETING 元数据记录。
     * 使用场景：仅被本类 schedulePhysicalDeletionAfterCommit 注册为事务提交后的回调任务调用。
     * 实现要点：循环调用 deletePhysicalAsset；单张失败不中断整批，失败信息留在记录里等定时任务重试。
     */
    private void deletePhysicalAssets(List<BlogImage> images) {
        for (BlogImage image : images) {
            deletePhysicalAsset(image);
        }
    }

    /**
     * 校验图片 ID 列表非空、不含 null 且无重复（用 LinkedHashSet 判重）。
     * 使用场景：被本类 replaceBlogImages 和 loadOwnedBlogImages 调用。
     * 实现要点：纯内存校验，违规抛 BusinessException。
     */
    private void requireDistinctImageIds(List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            throw new BusinessException("请至少保留一张图片");
        }
        Set<Long> distinct = new LinkedHashSet<>(imageIds);
        if (distinct.contains(null) || distinct.size() != imageIds.size()) {
            throw new BusinessException("图片ID不能为空或重复");
        }
    }

    /**
     * 清理过期临时图片的完整流程：按配置保留时长和批量大小查询旧 TEMP 记录，逐张条件抢占为 DELETING，
     * 使用场景：由 BlogImageCleanupJob 的 @Scheduled 定时任务（fixedDelay，默认 3600000 毫秒即 1 小时一轮）每轮调用，无用户入口。
     * 删除文件与元数据并累计成功数；单张失败则恢复 TEMP、记录日志并继续处理下一张，避免整批停住。
     * 具体例子：按默认配置保留期 {@code tempRetentionHours=24} 小时、每批 {@code cleanupBatchSize=100} 条，
     * 图片 501 上传 30 小时仍未发布会被清理；刚上传 2 小时的 502 不会进入本批次。
     */
    @Override
    public int cleanupExpiredTemporaryImages() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(properties.getTempRetentionHours());
        List<BlogImage> expiredImages = blogImageMapper.selectList(
                new LambdaQueryWrapper<BlogImage>()
                        .eq(BlogImage::getStatus, BlogImage.STATUS_TEMP)
                        .lt(BlogImage::getCreateTime, cutoff)
                        .orderByAsc(BlogImage::getCreateTime)
                        .last("LIMIT " + Math.max(1, properties.getCleanupBatchSize()))
        );
        if (expiredImages == null || expiredImages.isEmpty()) {
            return 0;
        }

        int cleaned = 0;
        for (BlogImage image : expiredImages) {
            if (!claimForDeletion(image.getId(), image.getUserId())) {
                continue;
            }
            try {
                storage.delete(image.getStorageKey());
                blogImageMapper.deleteById(image.getId());
                cleaned++;
            } catch (RuntimeException e) {
                restoreTemporaryStatus(image.getId(), image.getUserId());
                log.warn("清理临时博客图片失败，imageId={}", image.getId(), e);
            }
        }
        return cleaned;
    }

    /**
     * 重试待删除图片的完整流程：按 {@code status=DELETING 且 nextRetryTime 已到} 分批（每批 {@code cleanupBatchSize} 条）读取记录，
     * 使用场景：由 BlogImageCleanupJob 的 @Scheduled 定时任务（默认每小时一轮）在 cleanupExpiredTemporaryImages 之后调用，无用户入口。
     * 先用条件更新原子地把 nextRetryTime 向后推进 {@code deletingRetryDelayMinutes}（默认 5）分钟以抢占任务，
     * 再删除物理文件和条件删除元数据；失败时增加 retryCount、保存错误摘要并把重试时间再延后 5 分钟，成功数作为任务结果返回。
     * 具体例子：图片 501 上次因磁盘暂时不可用删除失败，nextRetryTime 到达后再次执行；成功则记录消失，失败则保留记录并延后下一次尝试。
     */
    @Override
    public int cleanupDeletingImages() {
        LocalDateTime now = LocalDateTime.now();
        List<BlogImage> deletingImages = blogImageMapper.selectList(
                new LambdaQueryWrapper<BlogImage>()
                        .eq(BlogImage::getStatus, BlogImage.STATUS_DELETING)
                        .and(query -> query.isNull(BlogImage::getNextRetryTime)
                                .or()
                                .le(BlogImage::getNextRetryTime, now))
                        .orderByAsc(BlogImage::getNextRetryTime, BlogImage::getId)
                        .last("LIMIT " + Math.max(1, properties.getCleanupBatchSize()))
        );
        if (deletingImages == null || deletingImages.isEmpty()) {
            return 0;
        }

        int cleaned = 0;
        for (BlogImage image : deletingImages) {
            if (!claimDeletingRetry(image.getId(), now)) {
                continue;
            }
            if (deletePhysicalAsset(image)) {
                cleaned++;
            }
        }
        return cleaned;
    }

    /**
     * 删除单张图片的物理文件并条件删除 DELETING 元数据记录。
     * 使用场景：被本类 deletePhysicalAssets（事务提交回调）和 cleanupDeletingImages（定时重试）调用。
     * 实现要点：先 storage.delete 删文件，再按 {@code id + status=DELETING} 条件删 tb_blog_image 记录；
     * 文件不存在同样是目标状态，两步都保持幂等；失败时 recordDeletionFailure 记录错误并返回 false，记录保留等下一轮重试。
     */
    private boolean deletePhysicalAsset(BlogImage image) {
        try {
            storage.delete(image.getStorageKey());
            blogImageMapper.delete(new LambdaQueryWrapper<BlogImage>()
                    .eq(BlogImage::getId, image.getId())
                    .eq(BlogImage::getStatus, BlogImage.STATUS_DELETING));
            return true;
        } catch (RuntimeException e) {
            recordDeletionFailure(image.getId(), e);
            log.warn("删除博客图片失败，保留 DELETING 记录等待重试，imageId={}", image.getId(), e);
            return false;
        }
    }

    /**
     * 原子抢占一条到期的 DELETING 重试任务：条件更新（{@code id + status=DELETING 且 nextRetryTime 为空或已到期}）
     * 把 nextRetryTime 推后 deletingRetryDelayMinutes（配置默认 5，最小 1）分钟。
     * 使用场景：仅被本类 cleanupDeletingImages 在删除每条记录前调用。
     * 实现要点：1 条 UPDATE，恰好更新 1 行才算抢占成功，保证多轮调度不会重复处理同一条。
     */
    private boolean claimDeletingRetry(Long imageId, LocalDateTime now) {
        return blogImageMapper.update(
                null,
                new LambdaUpdateWrapper<BlogImage>()
                        .eq(BlogImage::getId, imageId)
                        .eq(BlogImage::getStatus, BlogImage.STATUS_DELETING)
                        .and(query -> query.isNull(BlogImage::getNextRetryTime)
                                .or()
                                .le(BlogImage::getNextRetryTime, now))
                        .set(BlogImage::getNextRetryTime,
                                now.plusMinutes(Math.max(1, properties.getDeletingRetryDelayMinutes())))) == 1;
    }

    /**
     * 记录一次删除失败：retry_count 用 SQL 自增（{@code retry_count = retry_count + 1}），
     * 保存错误摘要（超过 1000 字符截断，常量 MAX_DELETION_ERROR_LENGTH），并把 nextRetryTime 推后 deletingRetryDelayMinutes（默认 5）分钟。
     * 使用场景：仅被本类 deletePhysicalAsset 在删除失败时调用。
     * 实现要点：1 条条件 UPDATE（{@code id + status=DELETING}），只影响仍处于 DELETING 的记录。
     */
    private void recordDeletionFailure(Long imageId, RuntimeException error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + error.getMessage();
        if (message.length() > MAX_DELETION_ERROR_LENGTH) {
            message = message.substring(0, MAX_DELETION_ERROR_LENGTH);
        }
        LocalDateTime nextRetry = LocalDateTime.now()
                .plusMinutes(Math.max(1, properties.getDeletingRetryDelayMinutes()));
        blogImageMapper.update(
                null,
                new LambdaUpdateWrapper<BlogImage>()
                        .eq(BlogImage::getId, imageId)
                        .eq(BlogImage::getStatus, BlogImage.STATUS_DELETING)
                        .setSql("retry_count = retry_count + 1")
                        .set(BlogImage::getLastError, message)
                        .set(BlogImage::getNextRetryTime, nextRetry)
        );
    }

    /**
     * 用条件更新把 TEMP 图片原子抢占为 DELETING：条件为 {@code id + user_id + status=TEMP + blog_id IS NULL}，
     * 同时清零 retry_count/lastError 并设置 nextRetryTime。
     * 使用场景：被本类 deleteTemporaryImage（用户删除前抢占）和 cleanupExpiredTemporaryImages（定时清理前抢占）调用。
     * 实现要点：1 条 UPDATE，返回是否恰好更新 1 行，用于防并发重复删除。
     */
    private boolean claimForDeletion(Long imageId, Long userId) {
        int updated = blogImageMapper.update(
                null,
                new LambdaUpdateWrapper<BlogImage>()
                        .eq(BlogImage::getId, imageId)
                        .eq(BlogImage::getUserId, userId)
                        .eq(BlogImage::getStatus, BlogImage.STATUS_TEMP)
                        .isNull(BlogImage::getBlogId)
                        .set(BlogImage::getStatus, BlogImage.STATUS_DELETING)
                        .set(BlogImage::getRetryCount, 0)
                        .set(BlogImage::getLastError, null)
                        .set(BlogImage::getNextRetryTime, nextDeletionRetryTime())
        );
        return updated == 1;
    }

    /**
     * 把删除失败的图片从 DELETING 恢复为 TEMP，清零重试信息并清空 nextRetryTime。
     * 使用场景：被本类 deleteTemporaryImage 和 cleanupExpiredTemporaryImages 在物理删除抛异常时调用，供用户或下一轮任务重试。
     * 实现要点：1 条条件 UPDATE（{@code id + user_id + status=DELETING + blog_id IS NULL}）。
     */
    private void restoreTemporaryStatus(Long imageId, Long userId) {
        blogImageMapper.update(
                null,
                new LambdaUpdateWrapper<BlogImage>()
                        .eq(BlogImage::getId, imageId)
                        .eq(BlogImage::getUserId, userId)
                        .eq(BlogImage::getStatus, BlogImage.STATUS_DELETING)
                        .isNull(BlogImage::getBlogId)
                        .set(BlogImage::getStatus, BlogImage.STATUS_TEMP)
                        .set(BlogImage::getRetryCount, 0)
                        .set(BlogImage::getLastError, null)
                        .set(BlogImage::getNextRetryTime, null)
        );
    }

    /**
     * 计算下次重试时间：当前时间加 deletingRetryDelayMinutes（配置默认 5，最小 1）分钟。
     * 使用场景：被本类 claimForDeletion、replaceBlogImages（removed 标记）和 detachAllBoundImages 调用，写入 nextRetryTime。
     * 实现要点：纯内存计算。
     */
    private LocalDateTime nextDeletionRetryTime() {
        return LocalDateTime.now()
                .plusMinutes(Math.max(1, properties.getDeletingRetryDelayMinutes()));
    }

    /**
     * 校验图片可被当前用户以“临时图”身份操作：存在、属于该用户、status=TEMP 且尚未绑定博客（blog_id 为空）。
     * 使用场景：被本类 deleteTemporaryImage 和 loadOwnedTemporaryImages 调用。
     * 实现要点：纯内存校验，违规分别抛“图片不存在/无权操作该图片/已发布的图片不能直接删除或重复绑定”。
     */
    private void validateTemporaryOwnership(BlogImage image, Long userId) {
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        if (!userId.equals(image.getUserId())) {
            throw new BusinessException("无权操作该图片");
        }
        if (!BlogImage.STATUS_TEMP.equals(image.getStatus()) || image.getBlogId() != null) {
            throw new BusinessException("已发布的图片不能直接删除或重复绑定");
        }
    }

    /**
     * 校验调用方传入了用户 ID，为空抛“请先登录”。
     * 使用场景：被本类 upload、deleteTemporaryImage、loadOwnedTemporaryImages、bindToBlog、replaceBlogImages、
     * loadOwnedBlogImages、detachAllBoundImages 在入口处调用；两个定时清理方法不经过本方法（直接操作记录归属者的资产）。
     * 实现要点：纯内存校验；真正的登录态由 Controller 层从 {@link UserHolder} 解出后传入。
     */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
    }

    /**
     * 尽力删除刚写入但未登记数据库的图片文件：删除失败仅记录 warn 日志，不向上抛出。
     * 使用场景：仅被本类 upload 的 catch 分支调用（数据库插入失败时回滚磁盘文件）。
     * 实现要点：吞掉存储层异常，保证原始插入异常继续向上传播。
     */
    private void deleteStoredFileQuietly(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException cleanupError) {
            log.warn("回滚未登记的图片文件失败，storageKey={}", storageKey, cleanupError);
        }
    }
}
