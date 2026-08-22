package com.educloud.file.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * 服务端对象键工厂：{bucket}/{owner}/{yyyyMMdd}/{uuid}.{ext}。
 *
 * <p>依据：M04 设计规格第 2 节「对象键」决策 —— 服务端生成、文件名不参与路径、
 * 客户端不可指定任意 MinIO 路径；owner 由调用方传入（如 "user-" + uploaderId），
 * ext 由 {@link ContentTypePolicy} 按 contentType 固定映射（未知类型抛异常）。</p>
 */
public final class ObjectKeyFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final String bucket;
    private final ContentTypePolicy contentTypePolicy;
    private final Clock clock;

    public ObjectKeyFactory(String bucket, ContentTypePolicy contentTypePolicy, Clock clock) {
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.contentTypePolicy = Objects.requireNonNull(contentTypePolicy, "contentTypePolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 生成对象键；owner 形如 "user-42"。
     *
     * @throws com.educloud.file.storage.FileTypeNotAllowedException 未知 contentType
     */
    public String create(String owner, String contentType) {
        String ext = contentTypePolicy.extension(contentType);
        return bucket + "/" + owner + "/" + LocalDate.now(clock).format(DATE_FORMAT)
                + "/" + UUID.randomUUID() + "." + ext;
    }
}

