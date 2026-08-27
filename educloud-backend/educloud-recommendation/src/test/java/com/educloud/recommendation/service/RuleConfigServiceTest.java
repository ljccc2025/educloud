package com.educloud.recommendation.service;

import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import com.educloud.recommendation.mapper.RecommendationRuleConfigMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuleConfigServiceTest {

    private final RecommendationRuleConfigMapper mapper = mock(RecommendationRuleConfigMapper.class);
    private final RuleConfigService service = new RuleConfigService(mapper);

    {
        service.setCacheTtlSeconds(60);
    }

    private RecommendationRuleConfigEntity rule(String key, int weight) {
        RecommendationRuleConfigEntity e = new RecommendationRuleConfigEntity();
        e.setRuleKey(key);
        e.setEnabled(true);
        e.setWeight(weight);
        e.setConfigVersion(1);
        return e;
    }

    @Test
    void cachesConfigForTtl() {
        when(mapper.selectList(any())).thenReturn(List.of(rule("POPULAR", 40), rule("NEW", 30), rule("SIMILAR", 30)));
        service.getEnabledRules();
        service.getEnabledRules();
        verify(mapper, times(1)).selectList(any());
    }

    @Test
    void allocatesQuotaByWeight() {
        when(mapper.selectList(any())).thenReturn(List.of(rule("POPULAR", 40), rule("NEW", 30), rule("SIMILAR", 30)));
        Map<String, Integer> quota = service.allocateQuota(10);
        assertEquals(4, quota.get("POPULAR"));
        assertEquals(3, quota.get("NEW"));
        assertEquals(3, quota.get("SIMILAR"));
    }

    @Test
    void emptyRulesReturnsEmptyQuota() {
        when(mapper.selectList(any())).thenReturn(List.of());
        Map<String, Integer> quota = service.allocateQuota(10);
        assertTrue(quota.isEmpty());
    }

    @Test
    void zeroWeightNoDivisionByZero() {
        when(mapper.selectList(any())).thenReturn(List.of(rule("POPULAR", 0), rule("NEW", 0)));
        Map<String, Integer> quota = service.allocateQuota(10);
        assertEquals(0, quota.get("POPULAR"));
        assertEquals(0, quota.get("NEW"));
    }

    @Test
    void nonPositiveLimitReturnsZeroQuota() {
        when(mapper.selectList(any())).thenReturn(List.of(rule("POPULAR", 40), rule("NEW", 30), rule("SIMILAR", 30)));
        Map<String, Integer> zero = service.allocateQuota(0);
        Map<String, Integer> negative = service.allocateQuota(-5);
        assertEquals(0, zero.get("POPULAR"));
        assertEquals(0, zero.get("NEW"));
        assertEquals(0, zero.get("SIMILAR"));
        assertEquals(0, negative.get("POPULAR"));
        assertEquals(0, negative.get("NEW"));
        assertEquals(0, negative.get("SIMILAR"));
    }

    @Test
    void negativeDiffKeepsQuotaNonNegative() {
        when(mapper.selectList(any())).thenReturn(List.of(
                rule("A", 1), rule("B", 1), rule("C", 1), rule("D", 1)));
        Map<String, Integer> quota = service.allocateQuota(2);
        assertTrue(quota.values().stream().allMatch(v -> v >= 0));
        assertEquals(2, quota.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void cacheRefreshesAfterTtlExpiry() throws InterruptedException {
        when(mapper.selectList(any())).thenReturn(List.of(rule("POPULAR", 40), rule("NEW", 30), rule("SIMILAR", 30)));
        service.setCacheTtlSeconds(0);
        service.getEnabledRules();
        // TTL=0 时缓存立即过期；间隔 2ms 确保两次读取落在不同毫秒，避免同毫秒导致误命中缓存
        Thread.sleep(2);
        service.getEnabledRules();
        verify(mapper, times(2)).selectList(any());
    }
}
