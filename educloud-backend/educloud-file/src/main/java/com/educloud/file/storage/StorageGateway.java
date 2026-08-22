package com.educloud.file.storage;

import java.time.Duration;

/**
 * 对象存储网关抽象：隔离业务代码与具体存储实现（当前为 MinIO，任务 3）。
 *
 * <p>依据：2026-08-22-educloud-file-design.md 第 6.2 节 —— presigned PUT 上传、
 * 对象元信息、限量下载、SHA-256 校验与存储探测均经由本接口，后续任务 7 在服务层
 * 统一转换为对外错误码。</p>
 */
public interface StorageGateway {

    /**
     * 生成对象直传（presigned PUT）URL。
     *
     * @param bucket      存储桶
     * @param objectKey   对象键
     * @param contentType 期望的 Content-Type（PUT 时由客户端随请求头发送，不参与签名）
     * @param ttl         URL 有效期
     * @return 可直接 PUT 的签名 URL
     */
    String presignedPutUrl(String bucket, String objectKey, String contentType, Duration ttl);

    /**
     * 生成对象下载（presigned GET）URL。
     *
     * @param bucket    存储桶
     * @param objectKey 对象键
     * @param ttl       URL 有效期（私有 bucket 下存储层强制过期）
     * @return 可直接 GET 的签名 URL
     */
    String presignedGetUrl(String bucket, String objectKey, Duration ttl);

    /**
     * 查询对象元信息；对象不存在时返回 exists=false 的 ObjectStat。
     */
    ObjectStat stat(String bucket, String objectKey);

    /**
     * 下载对象字节，最多 maxBytes；超限抛 {@link FileTooLargeException}。
     */
    byte[] download(String bucket, String objectKey, int maxBytes);

    /**
     * 计算对象 SHA-256（十六进制小写），最多读取 maxBytes；超限抛
     * {@link FileTooLargeException}，避免为超大对象计算完整摘要。
     */
    String sha256(String bucket, String objectKey, int maxBytes);

    /**
     * 删除对象；对象不存在按幂等处理（MinIO removeObject 对不存在对象不报错）。
     */
    void deleteObject(String bucket, String objectKey);

    /**
     * 存储连通性探测：写入并删除一个临时对象，全程不抛异常。
     *
     * @return ok=false 时 errorCategory 为分类字符串（如 CONNECTION/IO/UNKNOWN），
     *     ok=true 时 errorCategory 为 null
     */
    StorageProbeResult probe();

    /** 对象元信息快照。 */
    record ObjectStat(boolean exists, long size, String contentType) {
    }

    /** 存储探测结果：errorCategory 供任务 7 映射为对外错误码。 */
    record StorageProbeResult(boolean ok, String errorCategory) {
    }
}
