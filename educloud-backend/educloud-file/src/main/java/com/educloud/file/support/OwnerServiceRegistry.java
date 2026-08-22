package com.educloud.file.support;

import com.educloud.file.exception.FileAccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 内部调用方 clientId → 文件属主服务（ownerService）映射。
 *
 * <p>依据：M04 设计规格 6.2 节 —— ownerService 不接收客户端输入，由 File 将已认证
 * clientId 映射为固定值（user-service→user、course-service→course、
 * content-service→content、live-service→live）；未登记 clientId 视为调用方越权，
 * 抛 {@link FileAccessDeniedException}（任务 7 统一映射 403）。</p>
 */
@Component
public class OwnerServiceRegistry {

    private final Map<String, String> clientIdToOwnerService = Map.of(
            "user-service", "user",
            "course-service", "course",
            "content-service", "content",
            "live-service", "live");

    /** 返回 clientId 对应的 ownerService；未知 clientId 抛 {@link FileAccessDeniedException}。 */
    public String require(String clientId) {
        Objects.requireNonNull(clientId, "clientId");
        String ownerService = clientIdToOwnerService.get(clientId);
        if (ownerService == null) {
            throw new FileAccessDeniedException(
                    "内部调用方 clientId 未登记属主服务: " + clientId);
        }
        return ownerService;
    }
}
