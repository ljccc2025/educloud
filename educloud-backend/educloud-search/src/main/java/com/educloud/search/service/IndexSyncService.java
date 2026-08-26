package com.educloud.search.service;

import com.educloud.search.messaging.event.ContentDomainEvent;
import com.educloud.search.messaging.event.CourseDomainEvent;

/**
 * Elasticsearch 实时增量索引同步服务接口
 */
public interface IndexSyncService {

    /**
     * 处理课程领域事件同步
     *
     * @param event 课程领域事件
     */
    void handleCourseEvent(CourseDomainEvent event);

    /**
     * 处理内容/课件领域事件同步
     *
     * @param event 内容/课件领域事件
     */
    void handleContentEvent(ContentDomainEvent event);
}
