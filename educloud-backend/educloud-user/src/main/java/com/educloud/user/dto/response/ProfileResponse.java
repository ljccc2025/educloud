package com.educloud.user.dto.response;

/** 本人档案响应。 */
public record ProfileResponse(
        String id,
        String displayName,
        String avatarFileId,
        String bio,
        String locale) {
}
