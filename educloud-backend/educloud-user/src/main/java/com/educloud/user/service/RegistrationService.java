package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.config.RegistrationProperties;
import com.educloud.user.dto.request.RegisterStudentRequest;
import com.educloud.user.entity.SysRoleEntity;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.entity.SysUserRoleEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.SysUserRoleMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.messaging.OutboxWriter;
import com.educloud.user.observability.UserMetrics;
import com.educloud.user.support.AuditWriter;
import com.educloud.user.support.Masking;
import com.educloud.user.support.PasswordPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 学生自助注册。依据：M03 设计规格第 5 节（仅 STUDENT、开关、唯一约束、默认角色、
 * UserRegistered Outbox 行、审计）与 API 规范第 7 节。
 */
@Service
public class RegistrationService {

    private static final String STUDENT_ROLE = "STUDENT";

    private final RegistrationProperties registrationProperties;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final OutboxWriter outboxWriter;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final UserMetrics userMetrics;

    public RegistrationService(
            RegistrationProperties registrationProperties,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            OutboxWriter outboxWriter,
            AuditWriter auditWriter,
            ObjectMapper objectMapper,
            UserMetrics userMetrics) {
        this.registrationProperties = Objects.requireNonNull(registrationProperties, "registrationProperties");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.sysUserMapper = Objects.requireNonNull(sysUserMapper, "sysUserMapper");
        this.userProfileMapper = Objects.requireNonNull(userProfileMapper, "userProfileMapper");
        this.sysRoleMapper = Objects.requireNonNull(sysRoleMapper, "sysRoleMapper");
        this.sysUserRoleMapper = Objects.requireNonNull(sysUserRoleMapper, "sysUserRoleMapper");
        this.outboxWriter = Objects.requireNonNull(outboxWriter, "outboxWriter");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
    }

    @Transactional
    public Long register(RegisterStudentRequest request, String ip, String userAgent, String requestId) {
        if (!registrationProperties.enabled()) {
            throw new BusinessException(
                    UserErrorCode.REGISTRATION_DISABLED,
                    "Student registration is disabled");
        }
        passwordPolicy.validate(request.password());

        String username = request.username().trim();
        String email = blankToNull(request.email());
        String phone = blankToNull(request.phone());

        // 唯一性预检；数据库唯一索引为最终保护（并发冲突在插入时捕获）。
        if (sysUserMapper.selectByLoginName(username) != null) {
            throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
        }
        if (email != null && sysUserMapper.selectByEmail(email) != null) {
            throw new BusinessException(UserErrorCode.EMAIL_TAKEN);
        }
        if (phone != null && sysUserMapper.selectByPhone(phone) != null) {
            throw new BusinessException(UserErrorCode.PHONE_TAKEN);
        }

        Instant now = Instant.now();
        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUserType("STUDENT");
        user.setStatus("ACTIVE");
        user.setTokenVersion(0L);
        user.setEmailVerified(false);
        user.setFailedLoginCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setVersion(0);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException duplicate) {
            throw mapDuplicate(duplicate, email, phone);
        }

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(user.getId());
        profile.setDisplayName(
                StringUtils.hasText(request.displayName()) ? request.displayName().trim() : username);
        profile.setLocale("zh-CN");
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        profile.setVersion(0);
        userProfileMapper.insert(profile);

        SysRoleEntity studentRole = sysRoleMapper.selectOne(
                new QueryWrapper<SysRoleEntity>()
                        .eq("code", STUDENT_ROLE)
                        .eq("status", "ACTIVE"));
        if (studentRole == null) {
            throw new IllegalStateException("STUDENT role is missing from the seeded role table");
        }
        SysUserRoleEntity userRole = new SysUserRoleEntity();
        userRole.setUserId(user.getId());
        userRole.setRoleId(studentRole.getId());
        userRole.setAssignedAt(now);
        sysUserRoleMapper.insert(userRole);

        outboxWriter.write(
                "User",
                String.valueOf(user.getId()),
                "UserRegistered",
                1,
                1L,
                payload(user.getId(), Masking.loginName(username), "STUDENT"),
                requestId,
                null);

        auditWriter.write(new AuditWriter.AuditEntry(
                "USER",
                String.valueOf(user.getId()),
                null,
                "USER_REGISTERED",
                "user",
                String.valueOf(user.getId()),
                "SUCCESS",
                null,
                null,
                null,
                ip,
                userAgent,
                requestId,
                null,
                "AUTH"));

        userMetrics.userRegistered();
        return user.getId();
    }

    private BusinessException mapDuplicate(DuplicateKeyException duplicate, String email, String phone) {
        if (email != null && sysUserMapper.selectByEmail(email) != null) {
            return new BusinessException(UserErrorCode.EMAIL_TAKEN);
        }
        if (phone != null && sysUserMapper.selectByPhone(phone) != null) {
            return new BusinessException(UserErrorCode.PHONE_TAKEN);
        }
        return new BusinessException(UserErrorCode.USERNAME_TAKEN);
    }

    private String payload(Long userId, String maskedUsername, String userType) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "userId", String.valueOf(userId),
                    "username", maskedUsername,
                    "userType", userType));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize UserRegistered payload", exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
