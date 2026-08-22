package com.educloud.file.config;

import com.educloud.file.support.ContentTypePolicy;
import com.educloud.file.support.ObjectKeyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 上传支持组件注册：{@link ContentTypePolicy}（白名单 + 大小上限）与
 * {@link ObjectKeyFactory}（服务端对象键）。
 *
 * <p>与 educloud-user 模块 support 类注册模式一致：支撑组件由配置类从
 * {@link FileProperties} 构建为 Spring Bean，供 {@code UploadSessionService} 构造注入。</p>
 */
@Configuration(proxyBeanMethods = false)
public class FileSupportConfiguration {

    @Bean
    ContentTypePolicy contentTypePolicy(FileProperties properties) {
        FileProperties.Upload upload = properties.upload();
        return new ContentTypePolicy(upload.allowedContentTypes(), upload.maxSizeBytes());
    }

    @Bean
    ObjectKeyFactory objectKeyFactory(FileProperties properties, ContentTypePolicy contentTypePolicy) {
        return new ObjectKeyFactory(properties.storage().bucket(), contentTypePolicy);
    }
}
