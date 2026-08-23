package com.educloud.course.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Course 服务业务指标（M05 任务 16）。
 *
 * <p>依据：任务 16 —— 四个低基数计数：course_published（审核通过原子发布）、
 * enrollment_created（免费选课成功）、audit_approved / audit_rejected（审批/驳回）。
 * Counter 注册名即 Prometheus 名称（_total 后缀，与 educloud-file/educloud-user 的
 * Micrometer 命名约定一致）；无动态标签。</p>
 */
@Component
public final class CourseMetrics {

    private final Counter coursePublished;
    private final Counter enrollmentCreated;
    private final Counter auditApproved;
    private final Counter auditRejected;

    public CourseMetrics(MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.coursePublished = Counter.builder("educloud.course.course_published_total")
                .description("Courses published via audit approval").register(meterRegistry);
        this.enrollmentCreated = Counter.builder("educloud.course.enrollment_created_total")
                .description("Free course enrollments created").register(meterRegistry);
        this.auditApproved = Counter.builder("educloud.course.audit_approved_total")
                .description("Course audit submissions approved").register(meterRegistry);
        this.auditRejected = Counter.builder("educloud.course.audit_rejected_total")
                .description("Course audit submissions rejected").register(meterRegistry);
    }

    public void recordCoursePublished() {
        coursePublished.increment();
    }

    public void recordEnrollmentCreated() {
        enrollmentCreated.increment();
    }

    public void recordAuditApproved() {
        auditApproved.increment();
    }

    public void recordAuditRejected() {
        auditRejected.increment();
    }
}
