package com.educloud.analytics.service;

import com.educloud.analytics.entity.AnalyticsRebuildTaskEntity;

public interface AggregationRebuildService {

    String triggerRebuild(String operatorId);

    AnalyticsRebuildTaskEntity getTaskProgress(String taskNo);
}
