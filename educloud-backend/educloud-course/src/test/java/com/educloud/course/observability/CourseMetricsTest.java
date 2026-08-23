package com.educloud.course.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M05 任务 16：CourseMetrics 计数行为测试（SimpleMeterRegistry，与 FileMetricsTest 同法）。
 *
 * <p>依据：任务 16 —— course_published / enrollment_created / audit_approved / audit_rejected
 * 四个业务计数；Counter 注册名即任务清单中的 Prometheus 名称（_total 后缀，与
 * educloud-file/educloud-user 的指标命名约定一致）。</p>
 */
class CourseMetricsTest {

    @Test
    void countsPublishEnrollmentAndAuditEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CourseMetrics metrics = new CourseMetrics(registry);

        metrics.recordCoursePublished();
        metrics.recordCoursePublished();
        metrics.recordEnrollmentCreated();
        metrics.recordAuditApproved();
        metrics.recordAuditRejected();

        assertCounter(registry, "educloud.course.course_published_total", 2);
        assertCounter(registry, "educloud.course.enrollment_created_total", 1);
        assertCounter(registry, "educloud.course.audit_approved_total", 1);
        assertCounter(registry, "educloud.course.audit_rejected_total", 1);
    }

    private static void assertCounter(SimpleMeterRegistry registry, String name, double expected) {
        Counter counter = registry.get(name).counter();
        assertThat(counter.count()).isEqualTo(expected);
    }
}
