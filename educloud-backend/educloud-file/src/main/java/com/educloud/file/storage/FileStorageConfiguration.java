package com.educloud.file.storage;

import com.educloud.file.config.FileProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 存储配置：构建 {@link MinioClient}（懒连接，不发任何网络请求）、
 * 注册 {@link StorageGateway}，并在容器启动时幂等初始化 bucket。
 *
 * <p>依据：M04 计划任务 3 —— bucket 初始化在配置层完成（bucketExists 不存在则
 * makeBucket），异常包装为 {@link FileStorageException}，任务 7 统一错误码。</p>
 */
@Configuration(proxyBeanMethods = false)
public class FileStorageConfiguration {

    @Bean
    @ConditionalOnMissingBean(MinioClient.class)
    MinioClient minioClient(FileProperties properties) {
        FileProperties.Storage storage = properties.storage();
        return MinioClient.builder()
                .endpoint(storage.endpoint())
                .credentials(storage.accessKey(), storage.secretKey())
                .build();
    }

    @Bean
    StorageGateway storageGateway(MinioClient minioClient, FileProperties properties) {
        return new MinioStorageGateway(minioClient, properties.storage().bucket());
    }

    /**
     * 独立初始化器：Spring 启动时幂等确保 bucket 存在（bean 本身无业务职责）。
     * 默认开启；上下文测试等无外部连接的场景可设
     * {@code educloud.file.storage.init-bucket-on-startup=false} 关闭。
     */
    @Bean
    @ConditionalOnProperty(
            name = "educloud.file.storage.init-bucket-on-startup",
            havingValue = "true",
            matchIfMissing = true)
    BucketInitializer minioBucketInitializer(MinioClient minioClient, FileProperties properties) {
        return new BucketInitializer(minioClient, properties.storage().bucket());
    }

    static final class BucketInitializer {

        BucketInitializer(MinioClient minioClient, String bucket) {
            MinioStorageGateway.ensureBucket(minioClient, bucket);
        }
    }
}
