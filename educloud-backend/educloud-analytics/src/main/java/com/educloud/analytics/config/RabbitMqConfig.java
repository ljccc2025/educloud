package com.educloud.analytics.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE_USER_EVENTS = "educloud.user.events";
    public static final String EXCHANGE_COURSE_EVENTS = "educloud.course.events";
    public static final String EXCHANGE_PAYMENT_EVENTS = "educloud.payment.events";
    public static final String EXCHANGE_CONTENT_EVENTS = "educloud.content.events";
    public static final String EXCHANGE_AUDIT_EVENTS = "educloud.audit.events";
    public static final String EXCHANGE_ANALYTICS_DLX = "analytics.sync.dlx";

    public static final String QUEUE_ANALYTICS_USER = "analytics.user.events.queue";
    public static final String QUEUE_ANALYTICS_COURSE = "analytics.course.events.queue";
    public static final String QUEUE_ANALYTICS_PAYMENT = "analytics.payment.events.queue";
    public static final String QUEUE_ANALYTICS_CONTENT = "analytics.content.events.queue";
    public static final String QUEUE_ANALYTICS_AUDIT = "analytics.audit.events.queue";
    public static final String QUEUE_ANALYTICS_DLQ = "analytics.sync.dlq";

    // 角色化动态流（规格 2026-08-27-activity-feed-certificate-design.md §5）：
    // 各域独立专用队列订阅领域事件交换机，与聚合消费互不影响。
    // 注意：报名动态统一来源于 course 总线 EnrollmentCreated（付费选课由 order.paid
    // 触发 enrollPaidCourse 后同样发布 EnrollmentCreated），不单独订阅支付事件，
    // 避免同一报名产生重复 ENROLLED 动态。
    public static final String QUEUE_ACTIVITY_FEED_COURSE = "activity_feed.course.queue";
    public static final String QUEUE_ACTIVITY_FEED_CONTENT = "activity_feed.content.queue";
    public static final String QUEUE_ACTIVITY_FEED_ASSIGNMENT = "activity_feed.assignment.queue";

    // 全域领域事件总线：作业批改事件（routing key assignment.graded）发布在该交换机，
    // 名称与 educloud-notification RabbitMqConfiguration.TOPIC_EXCHANGE_NAME 一致（幂等补声明）。
    public static final String EXCHANGE_DOMAIN_EVENTS = "educloud.events";
    public static final String ROUTING_KEY_ASSIGNMENT_GRADED = "assignment.graded";

    public static final String ROUTING_KEY_DLQ = "analytics.sync.dlq";

    @Bean
    public TopicExchange userEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_USER_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange courseEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_COURSE_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_PAYMENT_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange contentEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_CONTENT_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange auditEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_AUDIT_EVENTS).durable(true).build();
    }

    @Bean
    public TopicExchange domainEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_DOMAIN_EVENTS).durable(true).build();
    }

    @Bean
    public DirectExchange analyticsDeadLetterExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_ANALYTICS_DLX).durable(true).build();
    }

    @Bean
    public Queue analyticsUserQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_USER)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsCourseQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_COURSE)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsPaymentQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_PAYMENT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsContentQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_CONTENT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsAuditQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_AUDIT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsDeadLetterQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_DLQ).build();
    }

    @Bean
    public Queue activityFeedCourseQueue() {
        return QueueBuilder.durable(QUEUE_ACTIVITY_FEED_COURSE)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue activityFeedContentQueue() {
        return QueueBuilder.durable(QUEUE_ACTIVITY_FEED_CONTENT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue activityFeedAssignmentQueue() {
        return QueueBuilder.durable(QUEUE_ACTIVITY_FEED_ASSIGNMENT)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Binding userBinding(Queue analyticsUserQueue, TopicExchange userEventsExchange) {
        return BindingBuilder.bind(analyticsUserQueue).to(userEventsExchange).with("user.*");
    }

    @Bean
    public Binding courseBinding(Queue analyticsCourseQueue, TopicExchange courseEventsExchange) {
        return BindingBuilder.bind(analyticsCourseQueue).to(courseEventsExchange).with("course.*");
    }

    @Bean
    public Binding paymentBinding(Queue analyticsPaymentQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(analyticsPaymentQueue).to(paymentEventsExchange).with("payment.*");
    }

    @Bean
    public Binding contentBinding(Queue analyticsContentQueue, TopicExchange contentEventsExchange) {
        return BindingBuilder.bind(analyticsContentQueue).to(contentEventsExchange).with("content.*");
    }

    @Bean
    public Binding auditBinding(Queue analyticsAuditQueue, TopicExchange auditEventsExchange) {
        return BindingBuilder.bind(analyticsAuditQueue).to(auditEventsExchange).with("audit.*");
    }

    // 动态流课程域队列：course 服务 Outbox 投递器将课程/选课/评价事件发布到全域总线
    // educloud.events（routing key 为 aggregateType.aggregateId，如 Enrollment.70001 /
    // Course.1001 / CourseReview.3001），故按聚合前缀定向绑定（非 "#"，避免与
    // 作业批改/支付队列重复消费）。兼容既有 "course.*" 风格 routing key。
    @Bean
    public Binding activityFeedCourseEnrollmentBinding(Queue activityFeedCourseQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(activityFeedCourseQueue).to(domainEventsExchange).with("Enrollment.#");
    }

    @Bean
    public Binding activityFeedCourseLifecycleBinding(Queue activityFeedCourseQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(activityFeedCourseQueue).to(domainEventsExchange).with("Course.#");
    }

    @Bean
    public Binding activityFeedCourseReviewBinding(Queue activityFeedCourseQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(activityFeedCourseQueue).to(domainEventsExchange).with("CourseReview.#");
    }

    @Bean
    public Binding activityFeedContentBinding(Queue activityFeedContentQueue, TopicExchange contentEventsExchange) {
        return BindingBuilder.bind(activityFeedContentQueue).to(contentEventsExchange).with("#");
    }

    // 作业批改事件发布在全域总线 educloud.events（routing key assignment.graded），独立队列定向订阅。
    @Bean
    public Binding activityFeedAssignmentBinding(Queue activityFeedAssignmentQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(activityFeedAssignmentQueue).to(domainEventsExchange).with(ROUTING_KEY_ASSIGNMENT_GRADED);
    }

    @Bean
    public Binding analyticsDlqBinding(Queue analyticsDeadLetterQueue, DirectExchange analyticsDeadLetterExchange) {
        return BindingBuilder.bind(analyticsDeadLetterQueue).to(analyticsDeadLetterExchange).with(ROUTING_KEY_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
