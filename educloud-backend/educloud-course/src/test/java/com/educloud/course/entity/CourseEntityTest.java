package com.educloud.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 任务 4 的实体契约测试：8 个实体的表名与 V001__course.sql 对齐、
 * 主键均为 ASSIGN_ID（雪花 ID 由 MyBatis-Plus 分配，DB 无自增）、
 * 乐观锁 @Version 仅出现在 course 与 course_enrollment。
 */
class CourseEntityTest {

    @Test
    void allEightEntitiesMapToV001TableNames() {
        assertThat(tableName(CourseEntity.class)).isEqualTo("course");
        assertThat(tableName(CourseVersionEntity.class)).isEqualTo("course_version");
        assertThat(tableName(CourseCategoryEntity.class)).isEqualTo("course_category");
        assertThat(tableName(CourseTeacherEntity.class)).isEqualTo("course_teacher");
        assertThat(tableName(CourseAuditSubmissionEntity.class)).isEqualTo("course_audit_submission");
        assertThat(tableName(CourseEnrollmentEntity.class)).isEqualTo("course_enrollment");
        assertThat(tableName(CourseContentReadinessProjectionEntity.class))
                .isEqualTo("course_content_readiness_projection");
        assertThat(tableName(CourseReviewEntity.class)).isEqualTo("course_review");
    }

    @Test
    void allEntitiesUseAssignedSnowflakeId() throws Exception {
        for (Class<?> type : allEntities()) {
            TableId tableId = type.getDeclaredField("id").getAnnotation(TableId.class);
            assertThat(tableId)
                    .as("%s.id must declare @TableId", type.getSimpleName())
                    .isNotNull();
            assertThat(tableId.type())
                    .as("%s.id must use ASSIGN_ID (no DB auto-increment)", type.getSimpleName())
                    .isEqualTo(IdType.ASSIGN_ID);
        }
    }

    @Test
    void onlyCourseAndEnrollmentCarryOptimisticLockVersion() throws Exception {
        assertThat(CourseEntity.class.getDeclaredField("version").getAnnotation(Version.class))
                .as("course.version is the aggregate-root optimistic lock")
                .isNotNull();
        assertThat(CourseEnrollmentEntity.class.getDeclaredField("version").getAnnotation(Version.class))
                .as("course_enrollment.version is the enrollment optimistic lock")
                .isNotNull();

        // 其余 6 张表在 V001 中没有 version 列：不得声明 version 字段。
        for (Class<?> type : List.of(
                CourseVersionEntity.class,
                CourseCategoryEntity.class,
                CourseTeacherEntity.class,
                CourseAuditSubmissionEntity.class,
                CourseContentReadinessProjectionEntity.class,
                CourseReviewEntity.class)) {
            assertThat(hasField(type, "version"))
                    .as("%s must not declare a version column", type.getSimpleName())
                    .isFalse();
        }
    }

    @Test
    void courseEntityGetterSetterRoundTrip() {
        CourseEntity course = new CourseEntity();
        course.setId(9001L);
        course.setOwnerTeacherId(1001L);
        course.setLifecycleStatus("DRAFT");
        course.setPublishedVersionId(2001L);
        course.setDraftVersionId(2002L);
        course.setRatingAvg(new BigDecimal("4.50"));
        course.setRatingCount(12);
        course.setEnrollmentCount(3);
        course.setVersion(0L);
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 10, 0, 0, 123_000_000);
        course.setPublishedAt(now);
        course.setCreatedBy(1001L);
        course.setCreatedAt(now);
        course.setUpdatedBy(1001L);
        course.setUpdatedAt(now);

        assertThat(course.getId()).isEqualTo(9001L);
        assertThat(course.getOwnerTeacherId()).isEqualTo(1001L);
        assertThat(course.getLifecycleStatus()).isEqualTo("DRAFT");
        assertThat(course.getPublishedVersionId()).isEqualTo(2001L);
        assertThat(course.getDraftVersionId()).isEqualTo(2002L);
        assertThat(course.getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(course.getRatingCount()).isEqualTo(12);
        assertThat(course.getEnrollmentCount()).isEqualTo(3);
        assertThat(course.getVersion()).isEqualTo(0L);
        assertThat(course.getPublishedAt()).isEqualTo(now);
        assertThat(course.getCreatedAt()).isEqualTo(now);
        assertThat(course.getUpdatedAt()).isEqualTo(now);
    }

    private static List<Class<?>> allEntities() {
        return List.of(
                CourseEntity.class,
                CourseVersionEntity.class,
                CourseCategoryEntity.class,
                CourseTeacherEntity.class,
                CourseAuditSubmissionEntity.class,
                CourseEnrollmentEntity.class,
                CourseContentReadinessProjectionEntity.class,
                CourseReviewEntity.class);
    }

    private static String tableName(Class<?> type) {
        TableName tableName = type.getAnnotation(TableName.class);
        assertThat(tableName).as("%s must declare @TableName", type.getSimpleName()).isNotNull();
        return tableName.value();
    }

    private static boolean hasField(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getName().equals(name));
    }
}
