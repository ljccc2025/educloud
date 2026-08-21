package com.educloud.user.controller;

import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.security.InternalApiFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部状态快照。依据：API 规范第 14 节（GET /internal/v1/users/{id}/status-snapshot，
 * 返回 {userId,status,tokenVersion,aggregateVersion}，只允许登记投影消费者）。
 */
@RestController
@RequestMapping("/internal/v1/users")
public final class InternalUserStatusSnapshotController {

    private final SysUserMapper sysUserMapper;

    public InternalUserStatusSnapshotController(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @GetMapping(value = "/{id}/status-snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> statusSnapshot(@PathVariable Long id, HttpServletRequest request) {
        InternalApiFilter.requireClientId(request);
        SysUserEntity user = sysUserMapper.selectById(id);
        if (user == null) {
            return Map.of();
        }
        return Map.of(
                "userId", String.valueOf(user.getId()),
                "status", user.getStatus(),
                "tokenVersion", user.getTokenVersion(),
                "aggregateVersion", user.getVersion() == null ? 0L : user.getVersion().longValue());
    }
}
