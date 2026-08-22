package com.educloud.file.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import io.minio.messages.ErrorResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * MinIO（minio-java 8.5.7）存储网关实现。
 *
 * <p>职责：presigned PUT 签名、对象元信息、限量下载、SHA-256 校验、
 * 删除与连通性探测；底层 MinioException/IOException 统一包装为
 * {@link FileStorageException}（任务 7 统一错误码）。</p>
 */
public class MinioStorageGateway implements StorageGateway {

    private static final int READ_BUFFER_SIZE = 8192;
    private static final int PROBE_PAYLOAD_BYTES = 16;

    private final MinioClient minioClient;
    private final String defaultBucket;

    public MinioStorageGateway(MinioClient minioClient, String defaultBucket) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient");
        this.defaultBucket = Objects.requireNonNull(defaultBucket, "defaultBucket");
    }

    @Override
    public String presignedPutUrl(String bucket, String objectKey, String contentType, Duration ttl) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(Math.toIntExact(ttl.getSeconds()))
                    .build();
            return minioClient.getPresignedObjectUrl(args);
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new FileStorageException(
                    "生成 presigned PUT URL 失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, Duration ttl) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(Math.toIntExact(ttl.getSeconds()))
                    .build();
            return minioClient.getPresignedObjectUrl(args);
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new FileStorageException(
                    "生成 presigned GET URL 失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }

    @Override
    public ObjectStat stat(String bucket, String objectKey) {
        try {
            StatObjectResponse response = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return new ObjectStat(true, response.size(), response.contentType());
        } catch (ErrorResponseException e) {
            if (isNotFound(e)) {
                return new ObjectStat(false, 0, null);
            }
            throw new FileStorageException(
                    "查询对象元信息失败: bucket=" + bucket + ", object=" + objectKey, e);
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new FileStorageException(
                    "查询对象元信息失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }

    @Override
    public byte[] download(String bucket, String objectKey, int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes 不能为负数: " + maxBytes);
        }
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int total = 0;
            int read;
            while ((read = response.read(buffer, 0, buffer.length)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new FileTooLargeException("对象超过下载上限: bucket=" + bucket
                            + ", object=" + objectKey + ", maxBytes=" + maxBytes);
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new FileStorageException(
                    "下载对象失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }

    @Override
    public String sha256(String bucket, String objectKey, int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes 不能为负数: " + maxBytes);
        }
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
                DigestInputStream digest = new DigestInputStream(
                        response, MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int total = 0;
            int read;
            while ((read = digest.read(buffer, 0, buffer.length)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new FileTooLargeException("对象超过校验读取上限: bucket=" + bucket
                            + ", object=" + objectKey + ", maxBytes=" + maxBytes);
                }
            }
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new FileStorageException(
                    "计算对象 SHA-256 失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new FileStorageException(
                    "删除对象失败: bucket=" + bucket + ", object=" + objectKey, e);
        }
    }

    @Override
    public StorageProbeResult probe() {
        String objectKey = "probe/" + UUID.randomUUID() + ".bin";
        byte[] payload = new byte[PROBE_PAYLOAD_BYTES];
        new java.util.Random().nextBytes(payload);
        boolean uploaded = false;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(defaultBucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(payload), payload.length, -1)
                    .build());
            uploaded = true;
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(defaultBucket).object(objectKey).build());
            if (stat.size() != payload.length) {
                return new StorageProbeResult(false, "IO");
            }
            return new StorageProbeResult(true, null);
        } catch (MinioException | IOException | GeneralSecurityException e) {
            return new StorageProbeResult(false, errorCategory(e));
        } finally {
            if (uploaded) {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(defaultBucket).object(objectKey).build());
                } catch (MinioException | IOException | GeneralSecurityException ignored) {
                    // 探测失败时清理为尽力而为，不再掩盖原始结果
                }
            }
        }
    }

    private static boolean isNotFound(ErrorResponseException e) {
        ErrorResponse error = e.errorResponse();
        if (error != null) {
            String code = error.code();
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                return true;
            }
        }
        return e.response() != null && e.response().code() == 404;
    }

    private static String errorCategory(Exception e) {
        if (e instanceof IOException) {
            return "CONNECTION";
        }
        if (e instanceof MinioException) {
            return "IO";
        }
        return "UNKNOWN";
    }

    /** 供 FileStorageConfiguration 复用：幂等确保 bucket 存在。 */
    public static void ensureBucket(MinioClient minioClient, String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (MinioException | IOException | GeneralSecurityException e) {
            throw new FileStorageException("初始化 MinIO bucket 失败: " + bucket, e);
        }
    }
}
