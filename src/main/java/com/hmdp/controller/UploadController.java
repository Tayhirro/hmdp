package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.IBlogImageService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 博客图片上传前端控制器（根路径 {@code /upload/blog}）。
 * 负责发布笔记前的临时图片上传与删除；图片资产记录在 tb_blog_image（状态 TEMP/BOUND/DELETING），文件由存储层写入磁盘。
 */
@RestController
@RequestMapping("/upload/blog")
public class UploadController {

    private final IBlogImageService blogImageService;

    /**
     * 构造函数：注入博客图片服务（由 Spring 在装配该 Controller 时调用一次）。
     */
    public UploadController(IBlogImageService blogImageService) {
        this.blogImageService = blogImageService;
    }

    /**
     * 上传博客图片（临时图片）。
     * 使用场景：登录用户在“发布/编辑笔记”页选择本地图片后，前端以 multipart/form-data 发送 POST /upload/blog，
     * 文件参数名为 file；未登录时由 currentUserId 抛出 401。
     * 数据库：存储层校验并写入磁盘后，向 tb_blog_image 插入 status=TEMP 的资产记录（含路径、URL、类型、大小、宽高），
     * 插入失败会反向删除刚写入的文件；返回 imageId 与 publicUrl，图片在博客发布成功前不属于任何博客。
     */
    @PostMapping
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        return Result.ok(blogImageService.upload(image, currentUserId()));
    }

    /**
     * 删除尚未绑定博客的临时图片。
     * 使用场景：用户在发布/编辑页移除刚上传、还没随博客发布出去的图片时，前端发送 DELETE /upload/blog/{imageId}。
     * 数据库：校验图片属于当前用户、status=TEMP 且 blog_id 为空后，用条件更新
     * （id + user_id + status=TEMP + blog_id IS NULL）原子抢占为 DELETING，再删除磁盘文件和 tb_blog_image 记录；
     * 文件删除失败则把状态恢复为 TEMP 供用户重试。
     */
    @DeleteMapping("/{imageId}")
    public Result deleteBlogImage(@PathVariable("imageId") Long imageId) {
        blogImageService.deleteTemporaryImage(imageId, currentUserId());
        return Result.ok();
    }

    /**
     * 读取当前登录用户 ID。
     * 使用场景：本控制器的上传/删除方法在处理请求时内部调用，统一做登录前置校验。
     * 数据来源：从 {@link UserHolder}（基于 ThreadLocal 的当前登录用户上下文工具）取 {@link UserDTO}（用户脱敏信息 DTO），
     * 未登录或缺少用户 ID 时抛出 {@link BusinessException}（携带 HTTP 状态码与错误码的业务异常，由全局异常处理器转成统一失败响应）。
     */
    private Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return user.getId();
    }
}
