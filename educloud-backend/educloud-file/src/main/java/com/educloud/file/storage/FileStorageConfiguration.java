package com.educloud.file.storage;

import com.educloud.file.config.FileProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置：仅构建客户端（懒连接，不发任何网络请求），
 * bucket 校验/初始化由后续任务（StorageGateway/MinioStorageGateway）完成。
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
}
