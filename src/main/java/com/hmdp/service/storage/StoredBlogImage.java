package com.hmdp.service.storage;

/*
 * 现实业务背景：文件成功落盘后，业务层需要取得可信的存储键、公开地址、类型、大小和尺寸来登记图片资产。
 * 实际触发：{@link BlogImageStorage}（博客图片的落盘与删除组件）的 store() 校验全部通过后创建该结果，
 * BlogImageServiceImpl（博客图片业务实现）随后据此向 tb_blog_image（博客图片资产表）写入一条记录。
 *
 * 各字段含义：
 * - storageKey：上传根目录下的相对路径（如 blogs/2026/08/3/12/xxx.jpg），后续删除物理文件凭它定位；
 * - publicUrl：前端可直接访问的 URL 前缀 + storageKey（默认前缀 /imgs/）；
 * - contentType：按探测出的真实内容确定（image/jpeg、image/png、image/gif），不信任原始文件名；
 * - fileSize / width / height：文件字节数和像素尺寸，写入资产表供后续校验和展示使用。
 */

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoredBlogImage {

    private String storageKey;

    private String publicUrl;

    private String contentType;

    private long fileSize;

    private int width;

    private int height;
}
