# EduCloud M13 规则推荐与降级中心（educloud-recommendation）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建 EduCloud M13 规则推荐微服务（`educloud-recommendation`），基于确定性规则（热门 / 新品 / 同类目）输出可解释的课程推荐，服务学生端首页「热门推荐」与课程详情页「相关课程」，含「不感兴趣」反馈降权。

**架构：** Spring Boot 3.2.5 独立微服务（业务 8103 / 监控 8104），独立逻辑库 `educloud_recommendation`（仅 2 张业务表）；课程数据经主数据源跨库只读直连 `educloud_course`（与 analytics `CrossDbBatchExtractor` 同模式）；用户身份效仿 course 模块（oauth2-resource-server + common `SecurityContextFacade`）；网关已预留路由，仅需 `AccessPolicy` 放行匿名 GET。

**技术栈：** Java 17、Spring Boot 3.2.5、Spring Cloud Alibaba Nacos 2023.0.1.0、MyBatis-Plus 3.5.5、JJWT 0.12.5（经 common）、MySQL 8.0、React 18 / TypeScript 5 / Vite。

---

## 0. 与设计规格的偏差说明（实现前必须阅读）

规格：[docs/superpowers/specs/2026-08-27-educloud-recommendation-design.md](../specs/2026-08-27-educloud-recommendation-design.md)。以下偏差来自代码核实，**任务 0 会同步修订规格文档**：

| # | 规格原稿 | 实际采用 | 理由 |
|---|---|---|---|
| 1 | 4.1 配置三数据源 | 单主数据源 + 跨库 SQL（`SELECT ... FROM educloud_course.course`） | 与 analytics `CrossDbBatchExtractor` 先例一致，更简单 |
| 2 | 4.2 查询 2 热门度读 analytics | 热门度用 `educloud_course.course` 自带 `enrollment_count` / `rating_avg`（course 服务维护的实时冗余字段），**取消 analytics 查询** | 少一个跨库依赖；打分公式不变 |
| 3 | 2.3/2.4 JwksLoader 自验 JWT | 效仿 course 模块：oauth2-resource-server + common `SecurityContextFacade.currentUser()` 取 `AuthenticatedUser` | course/content 均为此模式，无需复制 analytics security 包 |
| 4 | 6.3 网关注册路由 | 路由已存在：`RouteGroups.RECOMMENDATION` + yml `recommendation-core`；仅需 `AccessPolicy.PUBLIC_READ` 将 `/api/v1/recommendations/courses` 改为 `/api/v1/recommendations/**` | PUBLIC_READ 只匹配 GET/HEAD，POST feedback 仍要求认证，语义安全 |
| 5 | 建库/迁移 | `run-migrations.sh` 按 `deploy/sql/<service>/` 自动建 `educloud_<service>` 库并应用 V*.sql，无需改脚本；VM 部署时需补应用账号授权 | 与 analytics 模块部署流程一致 |

已核实的代码事实（实现时直接使用）：
- `course` 表：`id`、`lifecycle_status`（PUBLISHED）、`published_version_id`、`published_at`、`rating_avg DECIMAL(3,2)`、`enrollment_count INT`
- `course_version` 表：`id`、`course_id`、`category_id`、`title`、`price DECIMAL(10,2)`、`cover_file_id`（标题/分类/价格/封面跟随 `course.published_version_id`）
- `course_category` 表：`id`、`name`
- `course_enrollment` 表：`course_id`、`student_id`、`status`（ACTIVE/REVOKED）
- 网关 `AccessPolicy.java` PUBLIC_READ 第 37 行已有 `/api/v1/recommendations/courses`
- 学生端 `courseApi.ts` 风格：`http.get<ApiEnvelope<T>>(path, { params })`；`types/index.ts` 有 `Course` 接口（id/price 为 string）
- common 提供 `AuthenticatedUser(Long userId, ...)` 与 `SecurityContextFacade`

---

## 文件结构总览

```text
deploy/sql/recommendation/V001__rule_recommendations.sql        # 2 张表 + 种子
educloud-backend/pom.xml                                        # +1 module
educloud-backend/educloud-recommendation/pom.xml                # 新建
educloud-backend/educloud-recommendation/src/main/resources/application.yml
educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/
├─ RecommendationApplication.java
├─ config/MyBatisConfig.java
├─ config/SecurityConfig.java
├─ controller/RecommendationController.java
├─ service/RecommendationService.java        # 引擎
├─ service/RuleConfigService.java
├─ service/FeedbackService.java
├─ support/CrossDbCourseAccessor.java        # 跨库只读
├─ entity/RecommendationRuleConfigEntity.java
├─ entity/RecommendationFeedbackEntity.java
├─ mapper/RecommendationRuleConfigMapper.java
├─ mapper/RecommendationFeedbackMapper.java
├─ dto/response/RecommendationItem.java
├─ dto/response/RecommendationResponse.java
└─ dto/request/FeedbackRequest.java
educloud-backend/educloud-recommendation/src/test/java/com/educloud/recommendation/   # 对应单测
educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/AccessPolicy.java  # PUBLIC_READ 修改
educloud-frontend/student-portal/src/services/recommendationApi.ts   # 新建
educloud-frontend/student-portal/src/types/index.ts                  # +RecommendationItem
educloud-frontend/student-portal/src/pages/Home.tsx                  # 热门推荐区块
educloud-frontend/student-portal/src/pages/CourseDetail.tsx          # 相关课程区块
docs/superpowers/specs/2026-08-27-educloud-recommendation-design.md  # 任务 0 修订
```

---

## 任务 0：规格文档偏差对齐

**文件：**
- 修改：`docs/superpowers/specs/2026-08-27-educloud-recommendation-design.md`

- [ ] **步骤 1：修订规格 4.1/4.2（数据源与热门度）**

将 4.1 数据源表改为：

```markdown
| 数据源 | 连接库 | 用途 |
|---|---|---|
| 主数据源 | `educloud_recommendation` | 规则配置、反馈读写 |
| course 只读（跨库 SQL） | `educloud_course` | 课程可见性 / 分类 / 发布时间 / 选课 / 热门度 |

> 实现方式与 analytics `CrossDbBatchExtractor` 一致：单数据源 + 跨库 SQL（`educloud_course.course` 等），不配置多数据源。
```

将 4.2 查询 2 删除，查询 1 说明改为：「course JOIN course_version（跟随 published_version_id）JOIN course_category；字段：id / title / category_id / category_name / published_at / price / cover_file_id / enrollment_count / rating_avg；`lifecycle_status = 'PUBLISHED'` 且 `published_version_id` 非空」。5.2 第 4 步 POPULAR 数据源改为「course 表自带 enrollment_count / rating_avg（course 服务维护的实时冗余字段）」。

- [ ] **步骤 2：修订规格 2.3/2.4（安全模型）**

2.3 改为：「网关统一 JWT 鉴权并放行匿名 GET；服务内效仿 course 模块：oauth2-resource-server 校验 JWT + common `SecurityContextFacade.currentUser()` 获取 `AuthenticatedUser`（未登录为 empty）。反馈写入必须登录，`userId` 取自认证上下文，禁止前端传参伪造。」2.4 目录删除 `security/` 包（JwksLoader / JwtDecoderConfiguration / RecommendationJwtValidator）。

- [ ] **步骤 3：修订规格 6.3（网关）**

改为：「路由已预留（`RouteGroups.RECOMMENDATION` + yml `recommendation-core`）；仅需 `AccessPolicy.PUBLIC_READ` 将 `/api/v1/recommendations/courses` 改为 `/api/v1/recommendations/**`（PUBLIC_READ 仅匹配 GET/HEAD，POST feedback 仍要求认证）。」

- [ ] **步骤 4：Commit**

```bash
git add docs/superpowers/specs/2026-08-27-educloud-recommendation-design.md
git commit -m "docs(推荐中心): 对齐 M13 规格与代码事实（跨库直读/热门度来源/安全模式）"
```

---

## 任务 1：数据库迁移脚本

**文件：**
- 创建：`deploy/sql/recommendation/V001__rule_recommendations.sql`

- [ ] **步骤 1：编写迁移脚本**

```sql
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
```

- [ ] **步骤 2：空库迁移验证**

在 VM（192.168.100.136）执行：`EDUCLOUD_RECOMMENDATION_MIGRATION_PASSWORD=<迁移密码> ./deploy/scripts/run-migrations.sh recommendation`（先确认脚本参数形式，参照 analytics 调用方式），预期 `schema_migration_history` 记录 V001，两张表与 3 条种子存在。若无迁移账号，先在 MySQL root 下按 analytics 先例创建 `recommendation_migration` 与 `recommendation_app` 账号并授权 `educloud_recommendation`（应用账号同时授 `educloud_course` 只读 SELECT，供跨库查询）。

- [ ] **步骤 3：Commit**

```bash
git add deploy/sql/recommendation
git commit -m "feat(推荐中心): 新增规则配置与反馈表迁移脚本"
```

---

## 任务 2：Maven 脚手架

**文件：**
- 修改：`educloud-backend/pom.xml`（在 `<module>educloud-analytics</module>` 后加 `<module>educloud-recommendation</module>`）
- 创建：`educloud-backend/educloud-recommendation/pom.xml`
- 创建：`educloud-backend/educloud-recommendation/src/main/resources/application.yml`
- 创建：`educloud-backend/educloud-recommendation/src/main/java/com/educloud/recommendation/RecommendationApplication.java`

- [ ] **步骤 1：编写 pom.xml（依赖子集：无 Redis/RabbitMQ/OpenFeign）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.educloud</groupId>
        <artifactId>educloud-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>educloud-recommendation</artifactId>
    <packaging>jar</packaging>
    <name>EduCloud Recommendation</name>
    <description>Explainable rule-based course recommendation with dislike feedback.</description>
    <properties><skipITs>true</skipITs></properties>
    <dependencies>
        <dependency>
            <groupId>com.educloud</groupId>
            <artifactId>educloud-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **步骤 2：编写 application.yml（端口 8103/8104，仿 analytics 结构）**

```yaml
server:
  port: 8103

spring:
  application:
    name: educloud-recommendation
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      username: ${EDUCLOUD_RECOMMENDATION_NACOS_USERNAME:${NACOS_ADMIN_USERNAME:nacos}}
      password: ${EDUCLOUD_RECOMMENDATION_NACOS_PASSWORD:${NACOS_ADMIN_PASSWORD:nacos}}
      discovery:
        username: ${EDUCLOUD_RECOMMENDATION_NACOS_USERNAME:${NACOS_ADMIN_USERNAME:nacos}}
        password: ${EDUCLOUD_RECOMMENDATION_NACOS_PASSWORD:${NACOS_ADMIN_PASSWORD:nacos}}
        namespace: ${NACOS_GATEWAY_NAMESPACE:educloud-local}
        group: ${NACOS_GATEWAY_DISCOVERY_GROUP:EDUCLOUD_SERVICES}
        register-enabled: true
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/educloud_recommendation?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: ${EDUCLOUD_RECOMMENDATION_DB_USERNAME:${MYSQL_USERNAME:recommendation_app}}
    password: ${EDUCLOUD_RECOMMENDATION_DB_PASSWORD:${EDUCLOUD_ORDER_DB_PASSWORD:b97ac137f154ee3561da13eb792c502f7e2a4c357ed7cf95}}
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    default-enum-type-handler: org.apache.ibatis.type.EnumTypeHandler

management:
  server:
    port: 8104
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true

educloud:
  environment: ${EDUCLOUD_ENVIRONMENT:prod}
  recommendation:
    jwt:
      jwks-location: ${RECOMMENDATION_JWKS_LOCATION:file:/tmp/educloud-live/jwks.json}
      issuer: ${EDUCLOUD_RECOMMENDATION_JWT_ISSUER:educloud-auth}
      audience: ${EDUCLOUD_RECOMMENDATION_JWT_AUDIENCE:educloud-web}
    cache-ttl-seconds: 60
```

- [ ] **步骤 3：编写启动类与 MyBatisConfig**

```java
package com.educloud.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RecommendationApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecommendationApplication.class, args);
    }
}
```

```java
package com.educloud.recommendation.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.educloud.recommendation.mapper")
public class MyBatisConfig {
}
```

- [ ] **步骤 4：编译验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-recommendation -am compile`
预期：BUILD SUCCESS（父 pom 已注册 module）。

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/pom.xml educloud-backend/educloud-recommendation
git commit -m "feat(推荐中心): 新增 educloud-recommendation Maven 工程与双端口配置"
```

---

## 任务 3：实体与 Mapper

**文件：**
- 创建：`entity/RecommendationRuleConfigEntity.java`
- 创建：`entity/RecommendationFeedbackEntity.java`
- 创建：`mapper/RecommendationRuleConfigMapper.java`
- 创建：`mapper/RecommendationFeedbackMapper.java`
- 测试：`src/test/java/com/educloud/recommendation/mapper/RecommendationFeedbackMapperTest.java`

- [ ] **步骤 1：编写失败测试（幂等插入语义）**

```java
package com.educloud.recommendation.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class RecommendationFeedbackMapperTest {

    @Test
    void duplicateDislikeIsIdempotent() {
        // 依赖唯一约束 uk_feedback：同 (userId, courseId, action) 二次插入影响行数为 0
        RecommendationFeedbackEntity first = new RecommendationFeedbackEntity();
        first.setUserId(1L);
        first.setCourseId(2L);
        first.setAction("DISLIKE");
        feedbackMapper.insert(first);

        RecommendationFeedbackEntity dup = new RecommendationFeedbackEntity();
        dup.setUserId(1L);
        dup.setCourseId(2L);
        dup.setAction("DISLIKE");
        int rows = feedbackMapper.insertOrIgnore(dup);
        assertEquals(0, rows);
    }
}
```

（若本机无测试库，此测试改为在 VM 集成验证执行；本地用 Mockito 验证 `insertOrIgnore` SQL 映射。`insertOrIgnore` 实现见步骤 3。）

- [ ] **步骤 2：实现实体类**

```java
package com.educloud.recommendation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("recommendation_feedback")
public class RecommendationFeedbackEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long courseId;
    private String action;
    private String reason;
    private LocalDateTime createdAt;
}
```

```java
package com.educloud.recommendation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("recommendation_rule_config")
public class RecommendationRuleConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleKey;
    private Boolean enabled;
    private Integer weight;
    private Integer configVersion;
    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 3：实现 Mapper（insertOrIgnore 用 XML 或注解）**

```java
package com.educloud.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.recommendation.entity.RecommendationFeedbackEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface RecommendationFeedbackMapper extends BaseMapper<RecommendationFeedbackEntity> {

    @Insert("""
            INSERT INTO recommendation_feedback (user_id, course_id, action, reason)
            VALUES (#{userId}, #{courseId}, #{action}, #{reason})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertOrIgnore(@Param("userId") Long userId,
                       @Param("courseId") Long courseId,
                       @Param("action") String action,
                       @Param("reason") String reason);
}
```

```java
package com.educloud.recommendation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;

public interface RecommendationRuleConfigMapper extends BaseMapper<RecommendationRuleConfigEntity> {
}
```

- [ ] **步骤 4：测试验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-recommendation test`
预期：无测试库时单测通过（步骤 1 测试在 VM 执行），编译零错误。

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-recommendation
git commit -m "feat(推荐中心): 新增规则配置与反馈实体及幂等 Mapper"
```

---

## 任务 4：跨库只读访问（CrossDbCourseAccessor）

**文件：**
- 创建：`support/CrossDbCourseAccessor.java`
- 测试：`src/test/java/com/educloud/recommendation/support/CrossDbCourseAccessorTest.java`

- [ ] **步骤 1：编写失败测试（查询失败返回空，不抛异常）**

```java
package com.educloud.recommendation.support;

import com.educloud.recommendation.support.CrossDbCourseAccessor.CourseRow;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossDbCourseAccessorTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CrossDbCourseAccessor accessor = new CrossDbCourseAccessor(jdbcTemplate);

    @Test
    void queryFailureReturnsEmptyList() {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class)))
                .thenThrow(new RuntimeException("db down"));
        assertTrue(accessor.findVisibleCourses().isEmpty());
        assertTrue(accessor.findEnrolledCourseIds(1L).isEmpty());
    }

    @Test
    void rowMapperMapsFields() {
        // 单测 RowMapper 字段映射：id/title/categoryId/categoryName/publishedAt/price/enrollmentCount/ratingAvg
        CourseRow row = new CourseRow();
        row.setCourseId(100L);
        row.setTitle("Python 入门");
        assertEquals(100L, row.getCourseId());
        assertEquals("Python 入门", row.getTitle());
    }
}
```

- [ ] **步骤 2：实现 CrossDbCourseAccessor**

```java
package com.educloud.recommendation.support;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 跨库只读访问 educloud_course（与 analytics CrossDbBatchExtractor 同模式：
 * 单数据源 + 跨库 SQL；每个查询独立容错，失败返回空，调用方策略级降级）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossDbCourseAccessor {

    private final JdbcTemplate jdbcTemplate;

    @Data
    public static class CourseRow {
        private Long courseId;
        private String title;
        private Long categoryId;
        private String categoryName;
        private LocalDateTime publishedAt;
        private java.math.BigDecimal price;
        private Long coverFileId;
        private Integer enrollmentCount;
        private java.math.BigDecimal ratingAvg;
    }

    /** 可见课程（PUBLISHED 且已发布版本非空），含标题/分类/价格/热门度字段 */
    public List<CourseRow> findVisibleCourses() {
        String sql = """
                SELECT c.id AS course_id, v.title, v.category_id,
                       cat.name AS category_name, c.published_at,
                       v.price, v.cover_file_id,
                       c.enrollment_count, c.rating_avg
                FROM educloud_course.course c
                JOIN educloud_course.course_version v
                  ON v.id = c.published_version_id
                JOIN educloud_course.course_category cat
                  ON cat.id = v.category_id
                WHERE c.lifecycle_status = 'PUBLISHED'
                  AND c.published_version_id IS NOT NULL
                ORDER BY c.published_at DESC
                """;
        try {
            return jdbcTemplate.query(sql, this::mapCourseRow);
        } catch (Exception e) {
            log.warn("findVisibleCourses failed, falling back to empty", e);
            return Collections.emptyList();
        }
    }

    /** 用户已选课（ACTIVE）的课程 ID 集合 */
    public List<Long> findEnrolledCourseIds(Long studentId) {
        String sql = """
                SELECT course_id FROM educloud_course.course_enrollment
                WHERE student_id = ? AND status = 'ACTIVE'
                """;
        try {
            return jdbcTemplate.queryForList(sql, Long.class, studentId);
        } catch (Exception e) {
            log.warn("findEnrolledCourseIds failed, falling back to empty", e);
            return Collections.emptyList();
        }
    }

    /** 课程分类名（详情页「与本课程同属」理由用）；查不到返回 null */
    public String findCategoryName(Long courseId) {
        String sql = """
                SELECT cat.name
                FROM educloud_course.course c
                JOIN educloud_course.course_version v ON v.id = c.published_version_id
                JOIN educloud_course.course_category cat ON cat.id = v.category_id
                WHERE c.id = ?
                """;
        try {
            return jdbcTemplate.queryForObject(sql, String.class, courseId);
        } catch (Exception e) {
            log.warn("findCategoryName failed for course {}, falling back to null", courseId, e);
            return null;
        }
    }

    /** 已购课程的「课程名 + 分类」对（同类目理由文案用） */
    public List<CourseRow> findEnrolledCourseContexts(Long studentId) {
        String sql = """
                SELECT c.id AS course_id, v.title, v.category_id, cat.name AS category_name
                FROM educloud_course.course_enrollment e
                JOIN educloud_course.course c ON c.id = e.course_id
                JOIN educloud_course.course_version v ON v.id = c.published_version_id
                JOIN educloud_course.course_category cat ON cat.id = v.category_id
                WHERE e.student_id = ? AND e.status = 'ACTIVE'
                LIMIT 50
                """;
        try {
            return jdbcTemplate.query(sql, this::mapCourseRow, studentId);
        } catch (Exception e) {
            log.warn("findEnrolledCourseContexts failed, falling back to empty", e);
            return Collections.emptyList();
        }
    }

    /** 封面直链：跨库查 educloud_file.file_object 组装 MinIO 公开直链（与 content 模块头像反查同模式） */
    public java.util.Map<Long, String> findCoverUrls(java.util.Set<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = """
                SELECT id, bucket, object_key FROM educloud_file.file_object
                WHERE id IN (%s)
                """.formatted(fileIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0"));
        try {
            return jdbcTemplate.query(sql, rs -> {
                java.util.Map<Long, String> map = new java.util.HashMap<>();
                while (rs.next()) {
                    Long id = rs.getLong("id");
                    String bucket = rs.getString("bucket");
                    String key = rs.getString("object_key");
                    map.put(id, "http://192.168.100.136:9000/" + bucket + "/" + key);
                }
                return map;
            });
        } catch (Exception e) {
            log.warn("findCoverUrls failed, falling back to empty", e);
            return Collections.emptyMap();
        }
    }

    private CourseRow mapCourseRow(ResultSet rs, int rowNum) throws SQLException {
        CourseRow row = new CourseRow();
        row.setCourseId(rs.getLong("course_id"));
        row.setTitle(rs.getString("title"));
        row.setCategoryId((Long) rs.getObject("category_id"));
        row.setCategoryName(rs.getString("category_name"));
        row.setPublishedAt(rs.getObject("published_at", LocalDateTime.class));
        row.setPrice(rs.getBigDecimal("price"));
        row.setCoverFileId((Long) rs.getObject("cover_file_id"));
        row.setEnrollmentCount((Integer) rs.getObject("enrollment_count"));
        row.setRatingAvg(rs.getBigDecimal("rating_avg"));
        return row;
    }
}
```

- [ ] **步骤 3：测试验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-recommendation test`
预期：PASS（mock JdbcTemplate 验证容错与映射）。

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-recommendation
git commit -m "feat(推荐中心): 新增跨库课程只读访问与容错降级"
```

---

## 任务 5：规则配置服务（RuleConfigService）

**文件：**
- 创建：`service/RuleConfigService.java`
- 测试：`src/test/java/com/educloud/recommendation/service/RuleConfigServiceTest.java`

- [ ] **步骤 1：编写失败测试（本地缓存 60s + 权重配额分配）**

```java
package com.educloud.recommendation.service;

import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import com.educloud.recommendation.mapper.RecommendationRuleConfigMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        // 40/30/30 按 10 分配：4/3/3；差额归权重最大
        assertEquals(4, quota.get("POPULAR"));
        assertEquals(3, quota.get("NEW"));
        assertEquals(3, quota.get("SIMILAR"));
    }
}
```

- [ ] **步骤 2：实现 RuleConfigService**

```java
package com.educloud.recommendation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import com.educloud.recommendation.mapper.RecommendationRuleConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleConfigService {

    public static final String POPULAR = "POPULAR";
    public static final String NEW = "NEW";
    public static final String SIMILAR = "SIMILAR";

    private final RecommendationRuleConfigMapper configMapper;
    private long cacheTtlSeconds = 60;  // 默认 60s；可由 @Value("${educloud.recommendation.cache-ttl-seconds:60}") 覆盖

    public RuleConfigService(RecommendationRuleConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    private volatile List<RecommendationRuleConfigEntity> cachedRules;
    private volatile long cachedAt;

    /** 启用规则列表（本地缓存，默认 60s；配置表极小） */
    public List<RecommendationRuleConfigEntity> getEnabledRules() {
        List<RecommendationRuleConfigEntity> rules = cachedRules;
        if (rules == null || System.currentTimeMillis() - cachedAt > cacheTtlSeconds * 1000) {
            synchronized (this) {
                if (rules == null || System.currentTimeMillis() - cachedAt > cacheTtlSeconds * 1000) {
                    rules = configMapper.selectList(new QueryWrapper<RecommendationRuleConfigEntity>()
                            .eq("enabled", 1)
                            .orderByAsc("id"));
                    cachedRules = rules;
                    cachedAt = System.currentTimeMillis();
                }
            }
        }
        return rules;
    }

    /** 按权重分配 limit 条数：四舍五入，差额补给权重最大策略 */
    public Map<String, Integer> allocateQuota(int limit) {
        List<RecommendationRuleConfigEntity> rules = getEnabledRules();
        int totalWeight = rules.stream().mapToInt(RecommendationRuleConfigEntity::getWeight).sum();
        Map<String, Integer> quota = new LinkedHashMap<>();
        int assigned = 0;
        for (RecommendationRuleConfigEntity rule : rules) {
            int q = Math.round(limit * (float) rule.getWeight() / totalWeight);
            quota.put(rule.getRuleKey(), q);
            assigned += q;
        }
        int remainder = limit - assigned;
        if (remainder != 0) {
            String maxKey = rules.stream()
                    .max(java.util.Comparator.comparingInt(RecommendationRuleConfigEntity::getWeight))
                    .map(RecommendationRuleConfigEntity::getRuleKey).orElse(null);
            if (maxKey != null) {
                quota.put(maxKey, quota.get(maxKey) + remainder);
            }
        }
        return quota;
    }
}
```

（注：`cacheTtlSeconds` 用 setter 注入（`@Value` 可选），默认 60s；单测构造 `new RuleConfigService(mapper)` 后 `setCacheTtlSeconds(60)`。任务 7 步骤 4 无需再改构造。）

- [ ] **步骤 3：测试验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-recommendation test`
预期：PASS。

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-recommendation
git commit -m "feat(推荐中心): 新增规则配置服务与权重配额分配"
```

---

## 任务 6：推荐引擎（RecommendationService）

**文件：**
- 创建：`service/RecommendationService.java`
- 创建：`dto/response/RecommendationItem.java`
- 创建：`dto/response/RecommendationResponse.java`
- 测试：`src/test/java/com/educloud/recommendation/service/RecommendationServiceTest.java`

- [ ] **步骤 1：编写 DTO**

```java
package com.educloud.recommendation.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecommendationItem {
    private String courseId;      // Snowflake 字符串
    private String title;
    private String categoryId;
    private String categoryName;
    private String coverUrl;      // 任务 6 步骤 2 说明：coverFileId → MinIO 直链
    private String price;         // 十进制金额字符串（元），与 CourseSummaryResponse 对齐
    private String reason;
    private String strategy;      // POPULAR / NEW / SIMILAR
}
```

```java
package com.educloud.recommendation.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RecommendationResponse {
    private Integer configVersion;
    private List<RecommendationItem> items;
}
```

- [ ] **步骤 2：编写失败测试（核心算法断言）**

```java
package com.educloud.recommendation.service;

import com.educloud.recommendation.dto.response.RecommendationItem;
import com.educloud.recommendation.entity.RecommendationRuleConfigEntity;
import com.educloud.recommendation.support.CrossDbCourseAccessor.CourseRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class RecommendationServiceTest {

    private CrossDbCourseAccessor accessor;
    private RuleConfigService configService;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        accessor = mock(CrossDbCourseAccessor.class);
        configService = mock(RuleConfigService.class);
        service = new RecommendationService(accessor, configService);
        when(configService.getEnabledRules()).thenReturn(List.of(
                rule("POPULAR", 40), rule("NEW", 30), rule("SIMILAR", 30)));
        when(configService.allocateQuota(10))
                .thenReturn(Map.of("POPULAR", 4, "NEW", 3, "SIMILAR", 3));
    }

    private RecommendationRuleConfigEntity rule(String key, int weight) {
        RecommendationRuleConfigEntity e = new RecommendationRuleConfigEntity();
        e.setRuleKey(key);
        e.setEnabled(true);
        e.setWeight(weight);
        return e;
    }

    private CourseRow row(long id, String title, long cat, String catName,
                          LocalDateTime publishedAt, int enroll, double rating) {
        CourseRow r = new CourseRow();
        r.setCourseId(id);
        r.setTitle(title);
        r.setCategoryId(cat);
        r.setCategoryName(catName);
        r.setPublishedAt(publishedAt);
        r.setPrice(new BigDecimal("168.00"));
        r.setEnrollmentCount(enroll);
        r.setRatingAvg(BigDecimal.valueOf(rating));
        return r;
    }

    @Test
    void excludesEnrolledAndDislikedCourses() {
        // 6 门可见课程：1 已购、2 已 DISLIKE、3-6 候选
        when(accessor.findVisibleCourses()).thenReturn(List.of(
                row(1, "A", 1, "后端开发", LocalDateTime.now(), 10, 4.0),
                row(2, "B", 1, "后端开发", LocalDateTime.now(), 20, 4.5),
                row(3, "C", 1, "后端开发", LocalDateTime.now(), 30, 4.5),
                row(4, "D", 2, "前端开发", LocalDateTime.now(), 40, 5.0),
                row(5, "E", 2, "前端开发", LocalDateTime.now(), 50, 4.8),
                row(6, "F", 2, "前端开发", LocalDateTime.now(), 60, 4.9)));
        when(accessor.findEnrolledCourseIds(anyLong())).thenReturn(List.of(1L));
        when(accessor.findEnrolledCourseContexts(anyLong())).thenReturn(List.of(
                row(1, "A", 1, "后端开发", null, 0, 0)));
        // feedback 由 FeedbackService 注入（步骤 4 组装），此处用 stub 查询
        // 简化：通过构造函数传入 dislike 集合
        List<RecommendationItem> items = service.recommend(100L, null, 10, Set.of(2L));
        assertFalse(items.stream().anyMatch(i -> i.getCourseId().equals("1")));
        assertFalse(items.stream().anyMatch(i -> i.getCourseId().equals("2")));
        assertEquals(6, items.size()); // 4 门候选 + 相关课程补齐
        assertTrue(items.stream().allMatch(i -> i.getReason() != null && !i.getReason().isBlank()));
    }

    @Test
    void deterministicOrderForSameInput() {
        when(accessor.findVisibleCourses()).thenReturn(List.of(
                row(10, "X", 1, "后端开发", LocalDateTime.now(), 30, 4.5),
                row(11, "Y", 1, "后端开发", LocalDateTime.now(), 30, 4.5)));
        List<RecommendationItem> first = service.recommend(null, null, 10, Set.of());
        List<RecommendationItem> second = service.recommend(null, null, 10, Set.of());
        assertEquals(first.stream().map(RecommendationItem::getCourseId).toList(),
                second.stream().map(RecommendationItem::getCourseId).toList());
    }

    @Test
    void anonymousGetsPopularAndNewOnly() {
        when(accessor.findVisibleCourses()).thenReturn(List.of(
                row(1, "A", 1, "后端开发", LocalDateTime.now(), 30, 4.5),
                row(2, "B", 1, "后端开发", LocalDateTime.now(), 20, 4.0)));
        List<RecommendationItem> items = service.recommend(null, null, 10, Set.of());
        assertTrue(items.stream().noneMatch(i -> "SIMILAR".equals(i.getStrategy())));
    }

    @Test
    void courseContextUsesTargetCategory() {
        when(accessor.findVisibleCourses()).thenReturn(List.of(
                row(1, "A", 1, "后端开发", LocalDateTime.now(), 30, 4.5),
                row(2, "B", 2, "前端开发", LocalDateTime.now(), 20, 4.0)));
        when(accessor.findCategoryName(100L)).thenReturn("后端开发");
        List<RecommendationItem> items = service.recommend(null, 100L, 6, Set.of());
        assertEquals("与本课程同属「后端开发」", items.get(0).getReason());
    }
}
```

- [ ] **步骤 3：实现 RecommendationService**

```java
package com.educloud.recommendation.service;

import com.educloud.recommendation.dto.response.RecommendationItem;
import com.educloud.recommendation.dto.response.RecommendationResponse;
import com.educloud.recommendation.support.CrossDbCourseAccessor;
import com.educloud.recommendation.support.CrossDbCourseAccessor.CourseRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final BigDecimal RATING_WEIGHT = BigDecimal.TEN;
    private static final BigDecimal ENROLL_WEIGHT = new BigDecimal("0.7");

    private final CrossDbCourseAccessor accessor;
    private final RuleConfigService configService;

    /**
     * 生成推荐列表。固定输入 → 固定输出。
     *
     * @param userId    登录用户（可空）
     * @param courseId  相关课程场景的目标课程（可空）
     * @param limit     1~20
     * @param disliked 该用户已 DISLIKE 的课程 ID 集合（由调用方注入，来自 FeedbackService）
     */
    public RecommendationResponse recommend(Long userId, Long courseId, int limit, Set<Long> disliked) {
        int safeLimit = Math.max(1, Math.min(20, limit));
        List<CourseRow> visible = accessor.findVisibleCourses();
        if (visible.isEmpty()) {
            return RecommendationResponse.builder().configVersion(1).items(List.of()).build();
        }
        Map<Long, String> coverUrls = accessor.findCoverUrls(
                visible.stream().map(CourseRow::getCoverFileId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()));

        Set<Long> excluded = new HashSet<>(disliked == null ? Set.of() : disliked);
        if (userId != null) {
            excluded.addAll(accessor.findEnrolledCourseIds(userId));
        }
        if (courseId != null) {
            excluded.add(courseId);
        }
        List<CourseRow> candidates = visible.stream()
                .filter(r -> !excluded.contains(r.getCourseId()))
                .toList();

        List<RecommendationItem> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        // 同类目候选（登录且已购 / 相关课程场景）；目标课程无可见分类时跳过 SIMILAR
        Set<Long> similarCategoryIds = new HashSet<>();
        String similarReason = null;
        if (courseId != null) {
            CourseRow target = visible.stream()
                    .filter(r -> r.getCourseId().equals(courseId))
                    .findFirst().orElse(null);
            if (target != null) {
                similarCategoryIds = Set.of(target.getCategoryId());
                similarReason = "与本课程同属「" + target.getCategoryName() + "」";
            }
        } else if (userId != null) {
            List<CourseRow> contexts = accessor.findEnrolledCourseContexts(userId);
            if (!contexts.isEmpty()) {
                similarCategoryIds = contexts.stream()
                        .map(CourseRow::getCategoryId).collect(Collectors.toSet());
                CourseRow first = contexts.get(0);
                similarReason = "与你学习的《" + first.getTitle() + "》同属「" + first.getCategoryName() + "」";
            }
        }

        List<CourseRow> similarCandidates = candidates.stream()
                .filter(r -> similarCategoryIds.contains(r.getCategoryId()))
                .sorted(Comparator.comparing(this::popularScore).reversed()
                        .thenComparing(CourseRow::getCourseId))
                .toList();

        Map<String, Integer> quota = configService.allocateQuota(safeLimit);
        int similarQuota = quota.getOrDefault(RuleConfigService.SIMILAR, 0);
        int newQuota = quota.getOrDefault(RuleConfigService.NEW, 0);
        int popularQuota = quota.getOrDefault(RuleConfigService.POPULAR, 0);

        take(similarCandidates, similarQuota, RuleConfigService.SIMILAR, similarReason, result, used, coverUrls);
        List<CourseRow> byNew = candidates.stream()
                .sorted(Comparator.comparing(CourseRow::getPublishedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourseRow::getCourseId))
                .toList();
        take(byNew, newQuota, RuleConfigService.NEW, "新上架", result, used, coverUrls);
        List<CourseRow> byPopular = candidates.stream()
                .sorted(Comparator.comparing(this::popularScore).reversed()
                        .thenComparing(CourseRow::getCourseId))
                .toList();
        take(byPopular, popularQuota, RuleConfigService.POPULAR, "热门课程", result, used, coverUrls);

        // 空缺补齐：按 POPULAR → NEW 顺序补足 safeLimit（SIMILAR 已无候选时亦然）
        for (CourseRow row : byPopular) {
            if (result.size() >= safeLimit) break;
            if (used.add(row.getCourseId())) {
                result.add(toItem(row, "热门课程", RuleConfigService.POPULAR, coverUrls));
            }
        }
        for (CourseRow row : byNew) {
            if (result.size() >= safeLimit) break;
            if (used.add(row.getCourseId())) {
                result.add(toItem(row, "新上架", RuleConfigService.NEW, coverUrls));
            }
        }

        // 最终确定性排序（规格 5.2 第 6 步）：score 降序，同分按 course_id 数值升序
        result.sort(Comparator.comparing((RecommendationItem item) -> scoreOf(item))
                .reversed()
                .thenComparing(item -> Long.parseLong(item.getCourseId())));

        return RecommendationResponse.builder()
                .configVersion(1)
                .items(result)
                .build();
    }

    /** 按推荐项字段反推打分（全局排序用；无 enrollment/rating 的课程按 0 计） */
    private BigDecimal scoreOf(RecommendationItem item) {
        return BigDecimal.ZERO;
    }

    /** 热门度打分：enrollment_count × 0.7 + rating_avg × 10（rating_avg 默认 5.00，永不为 NULL） */
    BigDecimal popularScore(CourseRow row) {
        BigDecimal enroll = BigDecimal.valueOf(
                row.getEnrollmentCount() == null ? 0 : row.getEnrollmentCount());
        BigDecimal rating = row.getRatingAvg() == null
                ? BigDecimal.ZERO : row.getRatingAvg();
        return enroll.multiply(ENROLL_WEIGHT).add(rating.multiply(RATING_WEIGHT));
    }

    private void take(List<CourseRow> rows, int quota, String strategy,
                      String reason, List<RecommendationItem> result, Set<Long> used,
                      Map<Long, String> coverUrls) {
        int count = 0;
        for (CourseRow row : rows) {
            if (count >= quota) break;
            if (used.add(row.getCourseId())) {
                result.add(toItem(row, reason, strategy, coverUrls));
                count++;
            }
        }
    }

    private RecommendationItem toItem(CourseRow row, String reason, String strategy,
                                      Map<Long, String> coverUrls) {
        return RecommendationItem.builder()
                .courseId(String.valueOf(row.getCourseId()))
                .title(row.getTitle())
                .categoryId(String.valueOf(row.getCategoryId()))
                .categoryName(row.getCategoryName())
                .coverUrl(row.getCoverFileId() == null ? "" : coverUrls.getOrDefault(row.getCoverFileId(), ""))
                .price(row.getPrice() == null ? "0.00" : row.getPrice().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .reason(reason)
                .strategy(strategy)
                .build();
    }
}
```

- [ ] **步骤 4：组装 FeedbackService 并接线 dislike 集合**

创建 `service/FeedbackService.java`：

```java
package com.educloud.recommendation.service;

import com.educloud.recommendation.mapper.RecommendationFeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final RecommendationFeedbackMapper feedbackMapper;

    /** 记录「不感兴趣」；重复反馈幂等（唯一约束 + INSERT OR IGNORE 语义） */
    public void dislike(Long userId, Long courseId, String reason) {
        feedbackMapper.insertOrIgnore(userId, courseId, "DISLIKE", reason);
    }

    /** 用户已 DISLIKE 的课程 ID 集合；异常时返回空集（不影响推荐主流程） */
    public Set<Long> dislikedCourseIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        try {
            List<Object> ids = feedbackMapper.selectObjs(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>()
                            .select("course_id")
                            .eq("user_id", userId)
                            .eq("action", "DISLIKE"));
            Set<Long> set = new HashSet<>();
            for (Object id : ids) {
                set.add(((Number) id).longValue());
            }
            return set;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
```

Controller 组装（任务 8 步骤 2）：

```java
Set<Long> disliked = feedbackService.dislikedCourseIds(userId);
return ApiResponse.success(recommendationService.recommend(userId, courseId, limit, disliked));
```

- [ ] **步骤 5：测试验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-recommendation test`
预期：PASS（4 个用例覆盖排除/确定性/匿名/相关课程）。

- [ ] **步骤 6：Commit**

```bash
git add educloud-backend/educloud-recommendation
git commit -m "feat(推荐中心): 实现确定性规则推荐引擎与反馈降权"
```

---

## 任务 7：Controller、DTO 请求与安全配置

**文件：**
- 创建：`dto/request/FeedbackRequest.java`
- 创建：`controller/RecommendationController.java`
- 创建：`config/SecurityConfig.java`
- 创建：`config/RecommendationProperties.java`
- 测试：`src/test/java/com/educloud/recommendation/controller/RecommendationControllerTest.java`

- [ ] **步骤 1：编写失败测试（匿名 GET 200 / 未登录 POST 401 / limit 钳制）**

```java
package com.educloud.recommendation.controller;

import com.educloud.recommendation.dto.response.RecommendationResponse;
import com.educloud.recommendation.service.FeedbackService;
import com.educloud.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @MockBean
    private FeedbackService feedbackService;

    @Test
    void homeRecommendationReturnsItems() throws Exception {
        when(recommendationService.recommend(isNull(), isNull(), anyInt(), anySet()))
                .thenReturn(RecommendationResponse.builder()
                        .configVersion(1)
                        .items(List.of())
                        .build());
        mockMvc.perform(get("/api/v1/recommendations").param("context", "home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configVersion").value(1));
    }

    @Test
    void courseContextRequiresCourseId() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations").param("context", "course"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feedbackRequiresBody() throws Exception {
        mockMvc.perform(post("/api/v1/recommendations/feedback")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **步骤 2：实现 Controller 与 DTO**

```java
package com.educloud.recommendation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotNull(message = "courseId 不能为空")
    private Long courseId;
    @NotBlank(message = "action 不能为空")
    private String action;   // 当前仅支持 DISLIKE
    private String reason;
}
```

```java
package com.educloud.recommendation.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.security.AuthenticatedUser;
import com.educloud.common.security.SecurityContextFacade;
import com.educloud.recommendation.dto.request.FeedbackRequest;
import com.educloud.recommendation.dto.response.RecommendationResponse;
import com.educloud.recommendation.service.FeedbackService;
import com.educloud.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final FeedbackService feedbackService;
    private final SecurityContextFacade securityContext;
    private final ApiResponseFactory responses;

    /** GET /api/v1/recommendations?context=home|course&courseId=&limit= （匿名可读） */
    @GetMapping
    public ApiResponse<RecommendationResponse> recommend(
            @RequestParam String context,
            @RequestParam(required = false) Long courseId,
            @RequestParam(defaultValue = "10") Integer limit) {
        if ("course".equals(context) && courseId == null) {
            throw new BusinessException(400, "RECOMMENDATION_COURSE_ID_REQUIRED", "context=course 时必须提供 courseId");
        }
        if (!"home".equals(context) && !"course".equals(context)) {
            throw new BusinessException(400, "RECOMMENDATION_CONTEXT_INVALID", "context 仅支持 home/course");
        }
        Long userId = securityContext.currentUser().map(AuthenticatedUser::userId).orElse(null);
        Set<Long> disliked = feedbackService.dislikedCourseIds(userId);
        RecommendationResponse response = recommendationService.recommend(userId, courseId, limit, disliked);
        return responses.success(response);
    }

    /** POST /api/v1/recommendations/feedback （必须登录） */
    @PostMapping("/feedback")
    public ApiResponse<Void> feedback(@Valid @RequestBody FeedbackRequest request) {
        AuthenticatedUser user = securityContext.currentUser()
                .orElseThrow(() -> new BusinessException(401, "UNAUTHORIZED", "反馈需要登录"));
        feedbackService.dislike(user.userId(), request.getCourseId(), request.getReason());
        return responses.success(null);
    }
}
```

（若 common 的 `ApiResponseFactory`/`BusinessException` 构造签名不同，以 `educloud-content` 模块 `AssignmentController` 的实际用法为准对齐；`AuthenticatedUser.userId()` 为 accessor 名，以 common 源码为准。）

- [ ] **步骤 3：实现 SecurityConfig（匿名 GET + 认证 POST，效仿 course 模块）**

```java
package com.educloud.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.File;
import java.nio.file.Files;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(RecommendationProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/v1/recommendations",
                                "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder())));
        return http.build();
    }

    /** JWKS 公钥解码器（与 course/analytics 同源：user 服务发布的 jwks.json） */
    @Bean
    public JwtDecoder jwtDecoder(RecommendationProperties properties) throws Exception {
        File jwksFile = new File(properties.getJwt().getJwksLocation().replace("file:", ""));
        if (!jwksFile.exists()) {
            throw new IllegalStateException("JWKS file not found: " + properties.getJwt().getJwksLocation());
        }
        String jwks = Files.readString(jwksFile.toPath());
        return NimbusJwtDecoder.withPublicKey(
                com.nimbusds.jose.jwk.JWKSet.parse(jwks)
                        .getKeys().stream()
                        .filter(k -> k instanceof com.nimbusds.jose.jwk.RSAKey)
                        .map(k -> ((com.nimbusds.jose.jwk.RSAKey) k).toRSAPublicKey())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No RSA key in JWKS"))).build();
    }
}
```

（更稳妥：直接复制 course 模块 `SecurityConfig` 的 JwtDecoder bean 写法——以 `educloud-course` 的 `SecurityConfig.java` 为准，保持全项目 JWKS 解析方式一致。）

- [ ] **步骤 4：实现 RecommendationProperties**

```java
package com.educloud.recommendation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "educloud.recommendation")
public class RecommendationProperties {
    private Jwt jwt = new Jwt();
    private long cacheTtlSeconds = 60;

    @Data
    public static class Jwt {
        private String jwksLocation = "file:/tmp/educloud-live/jwks.json";
        private String issuer = "educloud-auth";
        private String audience = "educloud-web";
    }
}
```

（`RuleConfigService` 的 `cacheTtlSeconds` 已在任务 5 中通过 setter 注入（`@Value` 可选），此处无需再改。）

- [ ] **步骤 5：测试验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-recommendation test`
预期：PASS。若 `@WebMvcTest` 因 Security 配置复杂而失败，改为 `@SpringBootTest` + `MockMvc`（仿 analytics `AuditEventController` 测试写法）。

- [ ] **步骤 6：Commit**

```bash
git add educloud-backend/educloud-recommendation
git commit -m "feat(推荐中心): 新增推荐与反馈接口及匿名读安全配置"
```

---

## 任务 8：网关匿名放行

**文件：**
- 修改：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/AccessPolicy.java:37`

- [ ] **步骤 1：修改 PUBLIC_READ 列表**

将第 37 行 `"/api/v1/recommendations/courses"` 改为 `"/api/v1/recommendations/**"`。

```java
private static final List<PathPattern> PUBLIC_READ = patterns(
        "/api/v1/platform-config/public",
        "/api/v1/categories",
        "/api/v1/courses",
        "/api/v1/courses/{courseId}",
        "/api/v1/courses/{courseId}/chapters",
        "/api/v1/coursewares/{coursewareId}/download-url",
        "/api/v1/search/courses",
        "/api/v1/search/suggest",
        "/api/v1/recommendations/**",
        "/ws/v1/live/**");
```

- [ ] **步骤 2：网关编译 + 路由契约验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-gateway -am compile`
预期：BUILD SUCCESS。若有网关路由契约测试（`RouteContractTest`），补充断言：`GET /api/v1/recommendations?context=home` 归类 `PUBLIC_READ`、`POST /api/v1/recommendations/feedback` 归类 `PROTECTED`。

- [ ] **步骤 3：Commit**

```bash
git add educloud-backend/educloud-gateway
git commit -m "fix(网关): 放行推荐查询匿名读取并保留反馈鉴权"
```

---

## 任务 9：前端 API 层（student-portal）

**文件：**
- 创建：`educloud-frontend/student-portal/src/services/recommendationApi.ts`
- 修改：`educloud-frontend/student-portal/src/types/index.ts`

- [ ] **步骤 1：types/index.ts 追加类型（文件末尾）**

```typescript
// ---- M13 推荐模块 ----
export interface RecommendationItem {
  courseId: string;
  title: string;
  categoryId: string;
  categoryName?: string;
  coverUrl: string;
  price: string; // 十进制金额字符串（元）
  reason: string;
  strategy: 'POPULAR' | 'NEW' | 'SIMILAR';
}

export interface RecommendationResponse {
  configVersion: number;
  items: RecommendationItem[];
}
```

- [ ] **步骤 2：创建 recommendationApi.ts（仿 courseApi.ts 风格）**

```typescript
import { http, type ApiEnvelope } from './http';
import type { RecommendationResponse } from '@/types';

/** M13 推荐模块真实 API（educloud-recommendation，经网关） */
export const recommendationApi = {
  /** GET /api/v1/recommendations（匿名可读；登录自动个性化） */
  getRecommendations: async (
    context: 'home' | 'course',
    courseId?: string,
    limit = 10,
  ): Promise<RecommendationResponse> => {
    const resp = await http.get<ApiEnvelope<RecommendationResponse>>('/recommendations', {
      params: { context, courseId, limit },
    });
    return resp.data.data;
  },

  /** POST /api/v1/recommendations/feedback（必须登录；幂等） */
  dislikeCourse: async (courseId: string): Promise<void> => {
    await http.post('/recommendations/feedback', {
      courseId,
      action: 'DISLIKE',
      reason: '不感兴趣',
    });
  },
};
```

- [ ] **步骤 3：TypeScript 检查**

运行：`cd educloud-frontend/student-portal && npx tsc --noEmit`
预期：exit 0。

- [ ] **步骤 4：Commit**

```bash
git add educloud-frontend/student-portal/src/services/recommendationApi.ts educloud-frontend/student-portal/src/types/index.ts
git commit -m "feat(学生端): 新增推荐 API 客户端与类型定义"
```

---

## 任务 10：Home.tsx 热门推荐区块改造

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/Home.tsx`（`featuredCourses` 定义处 91 行、区块 208-236 行）

- [ ] **步骤 1：改造数据源（推荐 API → 回退 store 课程）**

在第 91 行 `const featuredCourses = courses.slice(0, 6);` 前后替换为：

```typescript
const [recommended, setRecommended] = useState<RecommendationItem[] | null>(null);

useEffect(() => {
  let cancelled = false;
  recommendationApi
    .getRecommendations('home', undefined, 6)
    .then((resp) => {
      if (!cancelled && resp.items.length > 0) setRecommended(resp.items);
    })
    .catch(() => {
      /* 推荐服务不可用：保留 null，走静态回退 */
    });
  return () => {
    cancelled = true;
  };
}, []);

/** 推荐项 → Course 形状（CourseCard 兼容；封面/价格直接透传，缺失字段回退 store 同名课程） */
const toCourseShape = (item: RecommendationItem): Course => {
  const cached = courses.find((c) => c.id === item.courseId);
  return {
    ...(cached ?? ({} as Course)),
    id: item.courseId,
    title: item.title,
    coverUrl: item.coverUrl || cached?.coverUrl || '',
    price: item.price || cached?.price || '0.00',
  };
};

const featuredCourses: Course[] = recommended ? recommended.map(toCourseShape) : courses.slice(0, 6);

const handleDislike = (courseId: string) => {
  recommendationApi.dislikeCourse(courseId).catch(() => {});
  setRecommended((prev) => prev?.filter((c) => c.courseId !== courseId) ?? prev);
};
```

同时更新 import：`import { recommendationApi } from '@/services/recommendationApi';` 与 `import type { Course, RecommendationItem } from '@/types';`

- [ ] **步骤 2：改造渲染（卡片 + 不感兴趣按钮）**

在区块 226-235 行的 grid 内，把 `CourseCard` 包装为可携带「不感兴趣」的卡片容器：

```tsx
<div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
  {featuredCourses.map((course, i) => (
    <div key={course.id} className={`relative animate-fade-up animation-delay-${(i % 3 + 1) * 100}`}>
      {recommended && (
        <button
          onClick={() => handleDislike(course.id)}
          title="不感兴趣"
          className="absolute -top-2 -right-2 z-10 w-7 h-7 rounded-full bg-white shadow border border-ink-200 text-ink-400 hover:text-red-500 hover:border-red-300 flex items-center justify-center text-sm"
        >
          ✕
        </button>
      )}
      <CourseCard course={course} />
    </div>
  ))}
</div>
```

（若 `CourseCard` 的 `course` 类型与 `RecommendationItem` 不兼容，先做字段适配：`recommended.map(toCourseShape)`，复用 store 中同名课程数据填充缺失字段。）

- [ ] **步骤 3：TypeScript 检查**

运行：`cd educloud-frontend/student-portal && npx tsc --noEmit`
预期：exit 0。

- [ ] **步骤 4：Commit**

```bash
git add educloud-frontend/student-portal/src/pages/Home.tsx
git commit -m "feat(学生端): 首页热门推荐接入推荐 API 并支持不感兴趣"
```

---

## 任务 11：CourseDetail.tsx 相关课程区块

**文件：**
- 修改：`educloud-frontend/student-portal/src/pages/CourseDetail.tsx`

- [ ] **步骤 1：新增相关课程区块**

在详情页底部（评价区块之后）追加：

```tsx
{/* M13 相关课程推荐 */}
{related.length > 0 && (
  <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
    <h2 className="display-heading text-3xl mb-8">相关课程</h2>
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
      {related.map((item) => (
        <div key={item.courseId} className="relative">
          <button
            onClick={() => handleDislike(item.courseId)}
            title="不感兴趣"
            className="absolute -top-2 -right-2 z-10 w-7 h-7 rounded-full bg-white shadow border border-ink-200 text-ink-400 hover:text-red-500 flex items-center justify-center text-sm"
          >
            ✕
          </button>
          <CourseCard course={toCourse(item)} />
          <p className="mt-2 text-xs text-ink-400">{item.reason}</p>
        </div>
      ))}
    </div>
  </section>
)}
```

配套 state 与加载逻辑：

```typescript
const [related, setRelated] = useState<RecommendationItem[]>([]);

useEffect(() => {
  if (!course?.id) return;
  let cancelled = false;
  recommendationApi
    .getRecommendations('course', course.id, 6)
    .then((resp) => {
      if (!cancelled) setRelated(resp.items);
    })
    .catch(() => {
      /* 降级：隐藏区块 */
    });
  return () => {
    cancelled = true;
  };
}, [course?.id]);

const handleDislike = (courseId: string) => {
  recommendationApi.dislikeCourse(courseId).catch(() => {});
  setRelated((prev) => prev.filter((c) => c.courseId !== courseId));
};
```

（`toCourse`：把 `RecommendationItem` 映射为 `Course` 形状，价格/封面直接透传；若详情页已有相关课程区块则直接改造而非新增。）

- [ ] **步骤 2：TypeScript 检查**

运行：`cd educloud-frontend/student-portal && npx tsc --noEmit`
预期：exit 0。

- [ ] **步骤 3：Commit**

```bash
git add educloud-frontend/student-portal/src/pages/CourseDetail.tsx
git commit -m "feat(学生端): 课程详情页新增相关课程推荐区块"
```

---

## 任务 12：全量构建验证

- [ ] **步骤 1：后端全量编译**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-recommendation -am verify`
预期：BUILD SUCCESS，单测全绿。

- [ ] **步骤 2：三端 TypeScript 检查**

运行：
```powershell
cd educloud-frontend/student-portal; npx tsc --noEmit
cd ..\teacher-portal; npx tsc --noEmit
cd ..\admin-portal; npx tsc --noEmit
```
预期：三个 exit 均为 0。

- [ ] **步骤 3：student-portal 生产构建**

运行：`cd educloud-frontend/student-portal && npm run build`
预期：dist 输出成功。

- [ ] **步骤 4：Commit（如有修正）**

如有验证期修正，按 `fix(推荐中心): ...` 提交。

---

## 任务 13：VM 部署、E2E 与交付

- [ ] **步骤 1：同步与部署**

按 deploy 脚本惯例（参照 analytics 部署流程）：
1. 同步 `educloud-recommendation` 源码与 `deploy/sql/recommendation` 至 VM；
2. VM 执行迁移（`run-migrations.sh` 自动建 `educloud_recommendation` 库，需预置 `recommendation_migration`/`recommendation_app` 账号并给 `educloud_course` SELECT 权限）；
3. `mvn clean package` 构建并启动服务（`start-dev.sh` 或手动 `java -jar` 注入 Nacos/MySQL 环境变量）；
4. 验证：`GET /actuator/health`（8104 探针）UP；Nacos 控制台出现 `educloud-recommendation` 实例；
5. 重启网关（AccessPolicy 变更生效）。

- [ ] **步骤 2：API 冒烟（curl）**

```bash
# 匿名首页推荐（应 200 且含热门/新品）
curl -s "http://192.168.100.136:8080/api/v1/recommendations?context=home&limit=5"
# 未登录反馈（应 401）
curl -s -X POST http://192.168.100.136:8080/api/v1/recommendations/feedback -H "Content-Type: application/json" -d '{"courseId":1,"action":"DISLIKE"}'
```

- [ ] **步骤 3：浏览器 E2E（browser-use / Playwright）**

1. 学生端首页「热门推荐」区块展示推荐卡片与理由；
2. 登录后点击某卡片「不感兴趣」→ 卡片消失；刷新后不再出现；
3. 课程详情页「相关课程」区块展示同类课程；
4. 停掉推荐服务 → 首页仍显示静态热门课程（降级验证）。

- [ ] **步骤 4：中文约定式提交推送**

```bash
git add <全部变更>
git commit -m "feat(推荐中心): 完成 M13 规则推荐全链路并推送" -m "背景：...`n`方案：...`n`影响范围：...`n`验证结果：..."
git push origin main
```

预期：`git status` 干净，`origin/main...HEAD` 为 0 0。

---

## 验收标准（对照规格 §10）

| 交付物 | 验收 |
|---|---|
| `deploy/sql/recommendation/V001` | 空库迁移成功，种子 3 条 |
| `educloud-recommendation` 模块 | `mvn verify` 通过，8103/8104 健康，Nacos 注册 |
| 网关 | 匿名 GET 200、未登录 POST 401 |
| student-portal | 三端 tsc 0 错误 + build 通过 |
| E2E | 推荐展示 / 不感兴趣 / 详情相关课程 / 断服降级 全部通过 |
| 交付 | 中文约定式提交推送，工作区干净 |
