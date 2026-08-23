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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 任务 4 的实体契约测试：8 个实体的表名与 V001__course.sql 对齐、
 * 主键均为 ASSIGN_ID（雪花 ID 由 MyBatis-Plus 分配，DB 无自增）、
 * 乐观锁 @Version 仅出现在 course 与 course_enrollment。
 *
 * <p>质量审查修订：三个契约断言共用单一 {@link #ENTITY_TABLE_NAMES} 常量
 * （Map&lt;Class&lt;?&gt;, String&gt; 表名映射），消除多处硬编码列表漂移。</p>
 */
class CourseEntityTest {

    /** V001__course.sql 表名映射：三个契约断言共用同一事实来源。 */
    private static final Map<Class<?>, String> ENTITY_TABLE_NAMES = Map.of(
            CourseEntity.class, "course",
            CourseVersionEntity.class, "course_version",
            CourseCategoryEntity.class, "course_category",
            CourseTeacherEntity.class, "course_teacher",
            CourseAuditSubmissionEntity.class, "course_audit_submission",
            CourseEnrollmentEntity.class, "course_enrollment",
            CourseContentReadinessProjectionEntity.class, "course_content_readiness_projection",
            CourseReviewEntity.class, "course_review");

    @Test
    void allEightEntitiesMapToV001TableNames() {
        assertThat(ENTITY_TABLE_NAMES).hasSize(8);
        ENTITY_TABLE_NAMES.forEach((type, expected) ->
                assertThat(tableName(type))
                        .as("%s table name must match V001__course.sql", type.getSimpleName())
                        .isEqualTo(expected));
    }

    @Test
    void allEntitiesUseAssignedSnowflakeId() throws Exception {
        for (Class<?> type : ENTITY_TABLE_NAMES.keySet()) {
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
        for (Map.Entry<Class<?>, String> entry : ENTITY_TABLE_NAMES.entrySet()) {
            Class<?> type = entry.getKey();
            if (type == CourseEntity.class || type == CourseEnrollmentEntity.class) {
                assertThat(type.getDeclaredField("version").getAnnotation(Version.class))
                        .as("%s.version is the optimistic lock", type.getSimpleName())
                        .isNotNull();
            } else {
                // 其余 6 张表在 V001 中没有 version 列：不得声明 version 字段。
                assertThat(hasField(type, "version"))
                        .as("%s must not declare a version column", type.getSimpleName())
                        .isFalse();
            }
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

    private static String tableName(Class<?> type) {
        TableName tableName = type.getAnnotation(TableName.class);
        assertThat(tableName).as("%s must declare @TableName", type.getSimpleName()).isNotNull();
        return tableName.value();
    }

    private static boolean hasField(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getName().equals(name));
    }
}
