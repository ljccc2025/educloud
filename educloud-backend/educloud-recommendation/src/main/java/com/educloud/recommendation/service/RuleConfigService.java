package com.educloud.recommendation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import com.educloud.recommendation.mapper.RecommendationRuleConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐规则配置服务：读取 recommendation_rule_config 表（种子：POPULAR=40 / NEW=30 / SIMILAR=30），
 * 本地缓存 60 秒，按权重分配推荐条数配额。
 */
@Service
public class RuleConfigService {

    /** 热门策略规则键 */
    public static final String POPULAR = "POPULAR";
    /** 新课策略规则键 */
    public static final String NEW = "NEW";
    /** 相似课程策略规则键 */
    public static final String SIMILAR = "SIMILAR";

    private final RecommendationRuleConfigMapper configMapper;

    /** 规则配置本地缓存 TTL（秒）；由 yml educloud.recommendation.cache-ttl-seconds 注入 */
    @Value("${educloud.recommendation.cache-ttl-seconds:60}")
    private long cacheTtlSeconds = 60;

    /** 本地缓存的启用规则列表，volatile 保证跨线程可见 */
    private volatile List<RecommendationRuleConfigEntity> cachedRules;
    /** 缓存写入时间戳（毫秒） */
    private volatile long cachedAt;

    public RuleConfigService(RecommendationRuleConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    /**
     * 读取启用规则（enabled=1，按 id 升序），双重检查锁本地缓存 cacheTtlSeconds 秒。
     */
    public List<RecommendationRuleConfigEntity> getEnabledRules() {
        List<RecommendationRuleConfigEntity> rules = cachedRules;
        long now = System.currentTimeMillis();
        if (rules == null || now - cachedAt > cacheTtlSeconds * 1000L) {
            synchronized (this) {
                rules = cachedRules;
                now = System.currentTimeMillis();
                if (rules == null || now - cachedAt > cacheTtlSeconds * 1000L) {
                    rules = configMapper.selectList(new QueryWrapper<RecommendationRuleConfigEntity>()
                            .eq("enabled", 1)
                            .orderByAsc("id"));
                    cachedRules = rules;
                    cachedAt = now;
                }
            }
        }
        return rules;
    }

    /**
     * 按权重占比分配推荐条数配额：Math.round(limit * weight / totalWeight) 四舍五入，
     * 四舍五入产生的差额循环分摊（保证所有配额非负且总和 == limit）；
     * 返回 LinkedHashMap（key = ruleKey，保持 id 升序）。
     */
    public Map<String, Integer> allocateQuota(int limit) {
        List<RecommendationRuleConfigEntity> rules = getEnabledRules();
        LinkedHashMap<String, Integer> quota = new LinkedHashMap<>();
        if (limit <= 0) {
            for (RecommendationRuleConfigEntity rule : rules) {
                quota.put(rule.getRuleKey(), 0);
            }
            return quota;
        }
        int totalWeight = 0;
        for (RecommendationRuleConfigEntity rule : rules) {
            totalWeight += weightOf(rule);
        }
        if (totalWeight <= 0) {
            for (RecommendationRuleConfigEntity rule : rules) {
                quota.put(rule.getRuleKey(), 0);
            }
            return quota;
        }
        int allocated = 0;
        for (RecommendationRuleConfigEntity rule : rules) {
            int weight = weightOf(rule);
            int share = (int) Math.round(limit * (float) weight / totalWeight);
            quota.put(rule.getRuleKey(), share);
            allocated += share;
        }
        // 四舍五入差额循环分摊：diff > 0 时对当前配额最大的策略 +1；diff < 0 时仅在配额 > 0 的策略中
        // 取最大者 -1，保证所有配额非负且总和 == limit。
        int diff = limit - allocated;
        while (diff != 0) {
            final int step = Integer.signum(diff);
            String targetKey = quota.entrySet().stream()
                    .filter(e -> step > 0 || e.getValue() > 0)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (targetKey == null) {
                break;
            }
            quota.put(targetKey, quota.get(targetKey) + step);
            diff -= step;
        }
        return quota;
    }

    private int weightOf(RecommendationRuleConfigEntity rule) {
        return rule.getWeight() == null ? 0 : rule.getWeight();
    }
}
