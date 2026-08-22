package com.educloud.file.support;

import com.educloud.file.exception.GrantPurposeNotAllowedException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 下载授权 purpose 策略：白名单 + subject 类型约束。
 *
 * <p>依据：M04 设计规格 6.2 节 —— purpose 白名单（默认 PROFILE_AVATAR/PUBLIC_CATALOG，
 * 可配置）；PUBLIC_CATALOG 允许 ANONYMOUS subject，其余 purpose 仅允许 USER；
 * USER 必须携带 subjectUserId；未知 purpose / 未知 subjectType 抛
 * {@link GrantPurposeNotAllowedException}（任务 7 映射 403）。</p>
 */
public final class GrantPurposePolicy {

    public static final String SUBJECT_USER = "USER";
    public static final String SUBJECT_ANONYMOUS = "ANONYMOUS";

    public static final String PURPOSE_PROFILE_AVATAR = "PROFILE_AVATAR";
    public static final String PURPOSE_PUBLIC_CATALOG = "PUBLIC_CATALOG";

    private final Set<String> purposes;

    public GrantPurposePolicy(List<String> purposes) {
        this.purposes = Set.copyOf(Objects.requireNonNull(purposes, "purposes"));
    }

    /**
     * 校验 subject/purpose 组合；不通过抛 {@link GrantPurposeNotAllowedException}。
     */
    public void validate(String subjectType, Long subjectUserId, String purpose) {
        if (purpose == null || !purposes.contains(purpose)) {
            throw new GrantPurposeNotAllowedException("purpose 不在白名单: " + purpose);
        }
        if (SUBJECT_USER.equals(subjectType)) {
            if (subjectUserId == null) {
                throw new GrantPurposeNotAllowedException(
                        "subjectType=USER 时 subjectUserId 必填");
            }
            return;
        }
        if (SUBJECT_ANONYMOUS.equals(subjectType)) {
            if (!PURPOSE_PUBLIC_CATALOG.equals(purpose)) {
                throw new GrantPurposeNotAllowedException(
                        "ANONYMOUS subject 仅允许 purpose=PUBLIC_CATALOG");
            }
            return;
        }
        throw new GrantPurposeNotAllowedException("未知 subjectType: " + subjectType);
    }
}
