-- EduCloud Recommendation 数据库：推荐规则配置与反馈表（V001）
-- 依据：docs/superpowers/specs/2026-08-27-educloud-recommendation-design.md

CREATE TABLE IF NOT EXISTS recommendation_rule_config (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rule_key VARCHAR(20) NOT NULL COMMENT 'POPULAR / NEW / SIMILAR',
    enabled TINYINT NOT NULL DEFAULT 1,
    weight INT NOT NULL COMMENT '权重 0-100',
    config_version INT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_key (rule_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐规则配置';

INSERT INTO recommendation_rule_config (rule_key, enabled, weight, config_version)
SELECT 'POPULAR', 1, 40, 1 WHERE NOT EXISTS (SELECT 1 FROM recommendation_rule_config WHERE rule_key = 'POPULAR');
INSERT INTO recommendation_rule_config (rule_key, enabled, weight, config_version)
SELECT 'NEW', 1, 30, 1 WHERE NOT EXISTS (SELECT 1 FROM recommendation_rule_config WHERE rule_key = 'NEW');
INSERT INTO recommendation_rule_config (rule_key, enabled, weight, config_version)
SELECT 'SIMILAR', 1, 30, 1 WHERE NOT EXISTS (SELECT 1 FROM recommendation_rule_config WHERE rule_key = 'SIMILAR');

CREATE TABLE IF NOT EXISTS recommendation_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL DEFAULT 'DISLIKE',
    reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_feedback (user_id, course_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐反馈（不感兴趣）';

-- 应用账号权限：业务表逐表授权（不授予库级权限），与 001-create-databases.sh 约定一致
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_recommendation.recommendation_rule_config TO 'recommendation_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_recommendation.recommendation_feedback TO 'recommendation_app'@'%';
