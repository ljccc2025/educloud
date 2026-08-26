package com.educloud.analytics.service;

import com.educloud.analytics.dto.response.admin.FinanceOverviewResponse;

public interface FinanceAnalyticsService {

    FinanceOverviewResponse getFinanceOverview();
}
