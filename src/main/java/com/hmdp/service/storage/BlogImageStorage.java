package com.hmdp.service.storage;

/*
 * 现实业务背景：用户在博客编辑器选择图片后，服务端必须验证真实文件内容（不能只信前端传来的文件名）
 * 并把二进制安全地保存到受控目录，防止伪造扩展名上传非图片、路径穿越写到上传根目录之外。
 * 实际触发：BlogImageServiceImpl.upload()（博客图片上传流程）调用 store()；
 * delete() 由三类场景调用——用户删除临时图片、事务提交后删除解绑博客的图片、
 * 定时清理任务补偿清理过期 TEMP 和删除失败的 DELETING 图片记录。
 *
 * store() 的完整校验链（任一步不通过即抛 {@link BusinessException} 业务异常）：
 * 1. 文件非空且不超过配置上限（默认 5MB）；
 * 2. 用 ImageIO 探测真实内容，只接受 JPG、PNG、GIF，并读出宽高
 *    （单边上限默认 10000 像素、总像素默认 4000 万）；
 * 3. 原始文件名扩展名必须与真实内容一致（如 .jpg 文件实际解码必须是 JPEG）；
 * 4. 生成存储键 blogs/yyyy/MM/{hash两位}/{uuid}.{扩展名}（如 blogs/2026/08/3/12/xxx.jpg），
 *    解析目标路径时拒绝绝对路径和 .. 穿越，落盘前再用真实路径复核仍在上传根目录内。
 */

import com.hmdp.config.BlogImageProperties;
import com.hmdp.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Component
public class BlogImageStorage {

    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final BlogImageProperties properties;

    /**
     * 构造函数：注入上传配置 {@link BlogImageProperties}（绑定 application.yaml 前缀 hmdp.upload 的配置类），
     * 由 Spring 创建本组件时调用一次，仅字段赋值，无业务逻辑。
     * 使用场景：Spring 容器装配 {@code @Component} 本类时通过构造器注入，项目内无其他调用方。
     * 实现要点：纯内存赋值；大小/尺寸上限（maxBytes、maxWidth、maxHeight、maxPixels）、
     * 上传根目录 root 与公开 URL 前缀 publicPrefix 均从该配置读取。
     */
    public BlogImageStorage(BlogImageProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并保存一张博客图片到上传根目录，成功后返回含存储键和公开 URL 的登记信息。
     * 使用场景：仅被 BlogImageServiceImpl.upload 调用（登录用户 POST /upload/blog 上传图片，
     * 经 UploadController.uploadImage 进入），返回值用于创建 TEMP 图片资产记录；
     * 测试 BlogImageStorageTest 也直接调用。
     * 实现要点：
     * 1. 校验链依次为 {@link #validateBasicFile}（非空、不超过 maxBytes）→ {@link #inspectImage}
     *    （ImageIO 探测真实格式与宽高）→ {@link #validateOriginalExtension}（扩展名与真实内容一致）。
     * 2. {@link #createStorageKey} 生成 "blogs/yyyy/MM/{hash两位}/{uuid}.{扩展名}" 存储键，
     *    {@link #resolveWithinUploadRoot} 解析目标路径并拒绝绝对路径与 .. 穿越，
     *    落盘前再用真实路径（toRealPath）复核仍在上传根目录内。
     * 3. MultipartFile.transferTo 写入目标文件；BusinessException 或 IOException 时
     *    先 {@link #deleteQuietly} 清理半成品再抛出/包装异常，保证不留未登记的脏文件。
     */
    public StoredBlogImage store(MultipartFile file) {
        validateBasicFile(file);
        ImageMetadata metadata = inspectImage(file);
        validateOriginalExtension(file.getOriginalFilename(), metadata.getExtension());

        String storageKey = createStorageKey(metadata.getExtension());
        Path uploadRoot = getUploadRoot();
        Path target = resolveWithinUploadRoot(uploadRoot, storageKey);

        try {
            Files.createDirectories(uploadRoot);
            Files.createDirectories(target.getParent());
            Path realRoot = uploadRoot.toRealPath();
            Path realParent = target.getParent().toRealPath();
            if (!realParent.startsWith(realRoot)) {
                throw new BusinessException("图片路径超出上传目录");
            }
            file.transferTo(target.toFile());
            return new StoredBlogImage(
                    storageKey,
                    buildPublicUrl(storageKey),
                    metadata.getContentType(),
                    file.getSize(),
                    metadata.getWidth(),
                    metadata.getHeight()
            );
        } catch (BusinessException e) {
            deleteQuietly(target);
            throw e;
        } catch (IOException e) {
            deleteQuietly(target);
            throw new BusinessException("图片保存失败", e);
        }
    }

    /**
     * 按存储键删除上传根目录下的图片物理文件，文件不存在时静默返回（幂等）。
     * 使用场景：四类调用方，均在 BlogImageServiceImpl——deleteTemporaryImage（用户删除临时图片，
     * DELETE /upload/blog/{imageId}，经 UploadController.deleteBlogImage 进入）、
     * deletePhysicalAsset（事务提交回调与定时重试删除 DELETING 资产）、
     * cleanupExpiredTemporaryImages（定时清理过期 TEMP 图片，BlogImageCleanupJob 调度）、
     * deleteStoredFileQuietly（上传后数据库登记失败回滚刚落盘的文件）。
     * 实现要点：
     * 1. {@link #resolveWithinUploadRoot} 解析路径并拒绝穿越；文件不存在直接返回；
     *    目标必须是普通文件且不是符号链接（NOFOLLOW_LINKS 检查），否则抛"图片文件类型非法"。
     * 2. 真实路径（toRealPath）复核仍在上传根目录内后 Files.delete；
     *    IOException 包装为 BusinessException（"图片删除失败"），由调用方决定恢复状态或留待重试。
     */
    public void delete(String storageKey) {
        Path uploadRoot = getUploadRoot();
        Path candidate = resolveWithinUploadRoot(uploadRoot, storageKey);
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(candidate)) {
            throw new BusinessException("图片文件类型非法");
        }

        try {
            Path realRoot = uploadRoot.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new BusinessException("图片路径超出上传目录");
            }
            Files.delete(realFile);
        } catch (IOException e) {
            throw new BusinessException("图片删除失败", e);
        }
    }

    /**
     * 读取并规范化上传根目录路径：取配置项 hmdp.upload.root，转绝对路径并消除 .. 等冗余段。
     * 使用场景：仅被本类 store（确定写入基准）和 delete（确定删除基准）调用，作为路径越界校验的根。
     * 实现要点：配置为 null 或空白抛 IllegalStateException（"hmdp.upload.root 未配置"）；
     * yaml 当前取环境变量 HMDP_UPLOAD_ROOT，缺省 ./data/uploads；纯路径计算，无 Redis/SQL 操作。
     */
    Path getUploadRoot() {
        String root = properties.getRoot();
        if (root == null || root.trim().isEmpty()) {
            throw new IllegalStateException("hmdp.upload.root 未配置");
        }
        return Paths.get(root).toAbsolutePath().normalize();
    }

    /**
     * 把存储键解析为上传根目录内的绝对目标路径，拒绝空键、绝对路径和目录穿越。
     * 使用场景：被本类 store（确定写入位置）和 delete（确定删除位置）调用；
     * 包级可见（便于测试），BlogImageStorageTest 直接静态调用验证穿越防护。
     * 实现要点：复合校验按序执行——存储键为 null 或空白抛"图片存储路径不能为空"；
     * 键中反斜杠统一为正斜杠后 Paths.get，路径是绝对路径抛"图片存储路径非法"；
     * root.resolve 后 normalize，结果不以 root 开头抛"图片路径超出上传目录"（拦截 "../" 穿越）。纯路径计算。
     */
    static Path resolveWithinUploadRoot(Path uploadRoot, String storageKey) {
        if (storageKey == null || storageKey.trim().isEmpty()) {
            throw new BusinessException("图片存储路径不能为空");
        }

        Path root = uploadRoot.toAbsolutePath().normalize();
        Path relativePath = Paths.get(storageKey.replace('\\', '/'));
        if (relativePath.isAbsolute()) {
            throw new BusinessException("图片存储路径非法");
        }

        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) {
            throw new BusinessException("图片路径超出上传目录");
        }
        return candidate;
    }

    /**
     * 校验上传文件的基础属性：非空且字节数不超过配置上限。
     * 使用场景：仅被本类 store 作为校验链第一步调用。
     * 实现要点：file 为 null 或 isEmpty 抛"请选择图片"；getSize() > maxBytes 抛含上限 MB 数的提示
     * （maxBytes 默认 5242880 即 5MB，配置项 hmdp.upload.max-bytes）；纯内存校验，未读文件内容。
     */
    private void validateBasicFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        if (file.getSize() > properties.getMaxBytes()) {
            throw new BusinessException("图片不能超过 " + properties.getMaxBytes() / 1024 / 1024 + "MB");
        }
    }

    /**
     * 用 ImageIO 探测文件真实内容，返回格式扩展名、标准 contentType 与宽高元数据（不看文件名）。
     * 使用场景：仅被本类 store 作为校验链第二步调用，结果用于扩展名一致性校验（validateOriginalExtension）
     * 和组装 StoredBlogImage 的 contentType/width/height。
     * 实现要点：
     * 1. ImageIO.createImageInputStream 打不开流抛"无法识别图片内容"；没有可用 ImageReader 抛"仅支持 JPG、PNG 或 GIF 图片"。
     * 2. 取第一个 ImageReader 读出格式名（经 {@link #normalizeFormat} 归一）与宽高（经 {@link #validateDimensions} 校验）；
     *    contentType 为 "image/jpeg"（jpg 归一后）、"image/png" 或 "image/gif"。
     * 3. finally 中 reader.dispose 释放资源；IOException 包装为 BusinessException（"图片内容校验失败"）。
     */
    private ImageMetadata inspectImage(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw new BusinessException("无法识别图片内容");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new BusinessException("仅支持 JPG、PNG 或 GIF 图片");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = normalizeFormat(reader.getFormatName());
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                return new ImageMetadata(
                        format,
                        "image/" + ("jpg".equals(format) ? "jpeg" : format),
                        width,
                        height
                );
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("图片内容校验失败", e);
        }
    }

    /**
     * 把 ImageIO 报告的格式名归一为内部扩展名，只放行 JPG、PNG、GIF 三种格式。
     * 使用场景：仅被本类 inspectImage 在读取 reader.getFormatName() 后调用。
     * 实现要点：转小写后 "jpeg"/"jpg" 归一为 "jpg"，"png"/"gif" 原样返回，
     * 其余值抛"仅支持 JPG、PNG 或 GIF 图片"；纯字符串处理。
     */
    private String normalizeFormat(String formatName) {
        String format = formatName.toLowerCase(Locale.ROOT);
        if ("jpeg".equals(format) || "jpg".equals(format)) {
            return "jpg";
        }
        if ("png".equals(format) || "gif".equals(format)) {
            return format;
        }
        throw new BusinessException("仅支持 JPG、PNG 或 GIF 图片");
    }

    /**
     * 校验原始文件名的扩展名与探测出的真实格式一致，防止改扩展名伪装非图片文件。
     * 使用场景：仅被本类 store 作为校验链第三步调用。
     * 实现要点：原始文件名为空抛"图片文件名不能为空"；无点号或点号在末尾抛"图片扩展名不能为空"；
     * 扩展名转小写后按复合规则比较——真实格式为 jpg 时接受 jpg 或 jpeg，其余必须与真实格式完全相同，
     * 不一致抛"图片扩展名与实际内容不一致"；纯字符串处理。
     */
    private void validateOriginalExtension(String originalFilename, String actualExtension) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new BusinessException("图片文件名不能为空");
        }

        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            throw new BusinessException("图片扩展名不能为空");
        }

        String extension = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        boolean jpegMatches = "jpg".equals(actualExtension)
                && ("jpg".equals(extension) || "jpeg".equals(extension));
        if (!jpegMatches && !actualExtension.equals(extension)) {
            throw new BusinessException("图片扩展名与实际内容不一致");
        }
    }

    /**
     * 校验图片宽高合法且不超过配置上限，防止超大图与解压炸弹。
     * 使用场景：仅被本类 inspectImage 读出宽高后调用。
     * 实现要点：复合条件任一满足即抛"图片尺寸超出限制"——width <= 0、height <= 0、
     * width > maxWidth、height > maxHeight、或 width * height（按 long 计算）> maxPixels；
     * 上限默认分别为 10000、10000、40000000（4000 万），对应配置项 hmdp.upload.max-width/max-height/max-pixels。
     */
    private void validateDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0
                || width > properties.getMaxWidth()
                || height > properties.getMaxHeight()
                || pixels > properties.getMaxPixels()) {
            throw new BusinessException("图片尺寸超出限制");
        }
    }

    /**
     * 生成新图片的存储键："blogs/yyyy/MM/{hash两位}/{uuid}.{扩展名}"，如 blogs/2026/08/3/12/xxx.jpg。
     * 使用场景：仅被本类 store 在校验全部通过后调用，作为磁盘相对路径和资产表 tb_blog_image 的 storage_key。
     * 实现要点：月份取 LocalDate.now() 按 yyyy/MM 格式化（MONTH_PATH）；两级目录取随机 UUID 的
     * hashCode 低 4 位（hash & 0xF）和次 4 位（(hash >> 4) & 0xF），把文件散列到 256 个子目录，
     * 避免单目录文件过多；文件名本体为带连字符的随机 UUID；纯内存计算。
     */
    private String createStorageKey(String extension) {
        String month = LocalDate.now().format(MONTH_PATH);
        String uuid = UUID.randomUUID().toString();
        int hash = uuid.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return String.format("blogs/%s/%d/%d/%s.%s", month, d1, d2, uuid, extension);
    }

    /**
     * 按配置的 URL 前缀拼出图片的公开访问地址：前缀 + 存储键。
     * 使用场景：仅被本类 store 在文件写入成功后调用，结果作为 StoredBlogImage.publicUrl 最终存入资产表。
     * 实现要点：前缀取配置 hmdp.upload.public-prefix（默认 "/imgs/"），为空白时回退 "/imgs/"；
     * 强制以 "/" 开头和结尾；存储键中的反斜杠替换为正斜杠；纯字符串处理。
     */
    private String buildPublicUrl(String storageKey) {
        String prefix = properties.getPublicPrefix();
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = "/imgs/";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + storageKey.replace('\\', '/');
    }

    /**
     * 尽力删除指定路径的文件，失败只吞掉 IOException 不向上抛出。
     * 使用场景：仅被本类 store 的两个 catch 分支调用（校验失败或写盘 IOException 时清理半成品文件）。
     * 实现要点：1 次 Files.deleteIfExists；此时数据库记录尚未创建，个别残留文件由运维处理；
     * 与 {@link #delete} 不同：不解析存储键、不做路径越界校验、不抛业务异常。
     */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The database record is not created when storage fails; cleanup can be handled operationally.
        }
    }

    private static class ImageMetadata {

        private final String extension;
        private final String contentType;
        private final int width;
        private final int height;

        /**
         * 构造函数：打包一次图片探测得到的四项元数据，仅由本外部类 inspectImage 创建。
         * 使用场景：inspectImage 校验通过后 new 出本对象，store 随后读取各字段做扩展名校验并组装 StoredBlogImage。
         * 实现要点：纯字段赋值；四个字段均为不可变 final。
         */
        private ImageMetadata(String extension, String contentType, int width, int height) {
            this.extension = extension;
            this.contentType = contentType;
            this.width = width;
            this.height = height;
        }

        /**
         * 返回归一后的图片格式扩展名（jpg / png / gif）。
         * 使用场景：仅被本外部类 store 调用——用于扩展名一致性校验（validateOriginalExtension）和生成存储键。
         * 实现要点：纯 getter。
         */
        private String getExtension() {
            return extension;
        }

        /**
         * 返回按真实内容确定的标准 contentType（image/jpeg / image/png / image/gif）。
         * 使用场景：仅被本外部类 store 调用，写入 StoredBlogImage.contentType 并最终登记到资产表。
         * 实现要点：纯 getter。
         */
        private String getContentType() {
            return contentType;
        }

        /**
         * 返回探测出的图片宽度（像素）。
         * 使用场景：仅被本外部类 store 调用，写入 StoredBlogImage.width 登记到资产表。
         * 实现要点：纯 getter。
         */
        private int getWidth() {
            return width;
        }

        /**
         * 返回探测出的图片高度（像素）。
         * 使用场景：仅被本外部类 store 调用，写入 StoredBlogImage.height 登记到资产表。
         * 实现要点：纯 getter。
         */
        private int getHeight() {
            return height;
        }
    }
}
