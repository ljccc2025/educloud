package com.educloud.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.user.dto.request.ProfileUpdateRequest;
import com.educloud.user.dto.response.ProfileResponse;
import com.educloud.user.dto.response.UserSummary;
import com.educloud.user.entity.SysUserEntity;
import com.educloud.user.entity.UserProfileEntity;
import com.educloud.user.exception.UserErrorCode;
import com.educloud.user.mapper.SysPermissionMapper;
import com.educloud.user.mapper.SysRoleMapper;
import com.educloud.user.mapper.SysUserMapper;
import com.educloud.user.mapper.UserProfileMapper;
import com.educloud.user.support.AuditWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 当前用户与本人档案。依据：M03 设计规格第 10 节与 API 规范第 7 节
 * （本人可读写本人允许字段；头像 fileId 不触发 File 调用、不落 URL）。
 */
@Service
public final class ProfileService {

    private final SysUserMapper sysUserMapper;
    private final UserProfileMapper userProfileMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final AuditWriter auditWriter;

    public ProfileService(
            SysUserMapper sysUserMapper,
            UserProfileMapper userProfileMapper,
            SysRoleMapper sysRoleMapper,
            SysPermissionMapper sysPermissionMapper,
            AuditWriter auditWriter) {
        this.sysUserMapper = Objects.requireNonNull(sysUserMapper, "sysUserMapper");
        this.userProfileMapper = Objects.requireNonNull(userProfileMapper, "userProfileMapper");
        this.sysRoleMapper = Objects.requireNonNull(sysRoleMapper, "sysRoleMapper");
        this.sysPermissionMapper = Objects.requireNonNull(sysPermissionMapper, "sysPermissionMapper");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
    }

    public UserSummary me(Long userId) {
        SysUserEntity user = requireUser(userId);
        UserProfileEntity profile = profile(userId);
        List<String> roles = sysRoleMapper.selectCodesByUserId(userId);
        List<String> permissions = sysPermissionMapper.selectCodesByUserId(userId);
        return new UserSummary(
                String.valueOf(user.getId()),
                user.getUsername(),
                profile == null ? user.getUsername() : profile.getDisplayName(),
                user.getUserType(),
                roles,
                permissions);
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request, String ip, String userAgent, String requestId) {
        requireUser(userId);
        Instant now = Instant.now();
        UserProfileEntity profile = profile(userId);
        if (profile == null) {
            profile = new UserProfileEntity();
            profile.setUserId(userId);
            profile.setCreatedAt(now);
            profile.setVersion(0);
        }
        profile.setDisplayName(request.displayName().trim());
        profile.setBio(request.bio());
        profile.setLocale(request.locale() == null ? "zh-CN" : request.locale());
        profile.setAvatarFileId(request.avatarFileId());
        profile.setUpdatedAt(now);
        if (profile.getId() == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }

        auditWriter.write(new AuditWriter.AuditEntry(
                "USER",
                String.valueOf(userId),
                null,
                "PROFILE_UPDATED",
                "user_profile",
                String.valueOf(userId),
                "SUCCESS",
                null,
                null,
                null,
                ip,
                userAgent,
                requestId,
                null,
                "PROFILE"));

        return new ProfileResponse(
                String.valueOf(userId),
                profile.getDisplayName(),
                profile.getAvatarFileId() == null ? null : String.valueOf(profile.getAvatarFileId()),
                profile.getBio(),
                profile.getLocale());
    }

    private SysUserEntity requireUser(Long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private UserProfileEntity profile(Long userId) {
        return userProfileMapper.selectOne(
                new QueryWrapper<UserProfileEntity>().eq("user_id", userId));
    }
}
