package com.educloud.file.support;

import com.educloud.file.storage.FileTooLargeException;
import com.educloud.file.storage.FileTypeNotAllowedException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 上传 Content-Type 白名单与大小上限策略。
 *
 * <p>依据：M04 设计规格 9 节（类型白名单 + 大小上限 10MB，可配置）与 6.3 节错误语义：
 * 类型拒绝 → {@link FileTypeNotAllowedException}（415）、超限 → {@link FileTooLargeException}（413），
 * 任务 7 统一映射对外错误码。</p>
 */
public final class ContentTypePolicy {

    /** contentType → 对象键扩展名（任务 4 固定映射；未知类型抛异常）。 */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "application/pdf", "pdf");

    private final Set<String> allowedContentTypes;
    private final long maxSizeBytes;

    public ContentTypePolicy(List<String> allowedContentTypes, long maxSizeBytes) {
        this.allowedContentTypes = Set.copyOf(Objects.requireNonNull(
                allowedContentTypes, "allowedContentTypes"));
        this.maxSizeBytes = maxSizeBytes;
    }

    /** 校验 contentType 是否在白名单内（供 complete 二次校验等只查不改的场景）。 */
    public boolean isAllowed(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType);
    }

    /** 由 contentType 返回对象键扩展名；白名单外抛 {@link FileTypeNotAllowedException}。 */
    public String extension(String contentType) {
        String ext = contentType == null ? null : EXTENSIONS.get(contentType);
        if (ext == null) {
            throw new FileTypeNotAllowedException("Content-Type 不在白名单: " + contentType);
        }
        return ext;
    }

    /** 上传预检：白名单 + expectedSizeBytes 超上限拒绝（为 null 则跳过大小预检）。 */
    public void validate(String contentType, Long expectedSizeBytes) {
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new FileTypeNotAllowedException("Content-Type 不在白名单: " + contentType);
        }
        if (expectedSizeBytes != null && expectedSizeBytes > maxSizeBytes) {
            throw new FileTooLargeException(
                    "文件大小超过上限: " + expectedSizeBytes + " > " + maxSizeBytes);
        }
    }
}

