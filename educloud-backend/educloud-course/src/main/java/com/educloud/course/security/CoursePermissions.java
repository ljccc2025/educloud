package com.educloud.course.security;

import java.util.Set;

/**
 * Course 域权限码常量集：与 user 库 V004__course_permissions.sql 的 9 项一一对应
 * （course:create/update/submit/audit/offline/republish/archive/enroll/student:read）。
 * 控制器 @PreAuthorize 使用（hasAuthority），避免魔法字符串漂移。
 */
public final class CoursePermissions {

    public static final String CREATE = "course:create";
    public static final String UPDATE = "course:update";
    public static final String SUBMIT = "course:submit";
    public static final String AUDIT = "course:audit";
    public static final String OFFLINE = "course:offline";
    public static final String REPUBLISH = "course:republish";
    public static final String ARCHIVE = "course:archive";
    public static final String ENROLL = "course:enroll";
    public static final String STUDENT_READ = "course:student:read";

    public static final Set<String> ALL = Set.of(
            CREATE, UPDATE, SUBMIT, AUDIT, OFFLINE, REPUBLISH, ARCHIVE, ENROLL, STUDENT_READ);

    private CoursePermissions() {
    }
}
