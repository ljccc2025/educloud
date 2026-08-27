# EduCloud M13 规则推荐与降级中心（educloud-recommendation）设计规格

> **面向：** 下一位接手 M13 开发与验收的工程师 / AI 代理。
> **日期：** 2026-08-27
> **状态：** 已与用户逐节确认，待用户书面审查。

## 1. 概述

构建 EduCloud M13 规则推荐微服务（`educloud-recommendation`），基于**确定性规则**（热门 / 新品 / 同类目）输出**可解释**的课程推荐列表，服务学生端首页「为你推荐」与课程详情页「相关课程」两个场景。

- **无 AI 模型**：不创建模型密钥、向量表或自动 AI 决策（沿用路线图 Task 34 边界）。
- **纯派生能力**：任何故障不得影响登录、课程浏览、学习、订单、支付主链路；前端可整体回退现有静态数据。
- **固定输入 → 固定输出**：相同输入（用户、场景、配置）产生相同排序结果，便于测试与验收。

### 1.1 已确认决策（与用户逐项确认）

| 决策点 | 结论 |
|---|---|
| 范围 | 规则推荐（确定性、可解释、无 AI） |
| 场景 | 首页「为你推荐」+ 课程详情「相关课程」，一个 API 两种调用 |
| 策略 | 热门（选课 + 评分加权）+ 新品（最近发布）+ 同类目（基于已购课程分类） |
| 反馈 | 仅「不感兴趣」降权 + 已购/已学自动排除；不做点击/曝光埋点 |
| 配置 | 数据库种子 + 配置文件，保留 `config_version` 读取逻辑，本次不做管理端 UI |
| 数据获取 | 跨库只读直连（方案 A），不维护投影表、不消费领域事件 |

## 2. 模块总览

### 2.1 服务定位

| 项 | 值 |
|---|---|
| 模块路径 | `educloud-backend/educloud-recommendation/` |
| 业务端口 | 8103 |
| 监控端口 | 8104 |
| Nacos 服务名 | `educloud-recommendation` |
| 独立逻辑库 | `educloud_recommendation`（仅 2 张业务表） |
| 网关路由 | `/api/v1/recommendations/**` → `educloud-recommendation` |

### 2.2 Maven 依赖

`educloud-common`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、`spring-cloud-starter-alibaba-nacos-discovery`，及测试依赖（JUnit 5 / Mockito）。

**明确不引入**：Redis（配置本地缓存即可）、RabbitMQ（无事件消费）、OpenFeign（无跨服务调用）。

### 2.3 安全模型

- 网关统一 JWT 鉴权并放行匿名 GET；服务内效仿 course 模块：oauth2-resource-server 校验 JWT + common `SecurityContextFacade.currentUser()` 获取 `AuthenticatedUser`（未登录为 empty）。
- **推荐读取**：公开可访问（未登录 → 无个性化）；登录用户自动带个性化（同类目策略、排除已购与 DISLIKE）。
- **反馈写入**：必须登录，`userId` 取自认证上下文，禁止前端传参伪造。

### 2.4 目录结构

```text
educloud-recommendation/src/main/java/com/educloud/recommendation/
├─ RecommendationApplication.java
├─ config/
│  ├─ MyBatisConfig.java            # 主数据源
│  ├─ SecurityConfig.java           # oauth2-resource-server
│  └─ RecommendationProperties.java
├─ controller/RecommendationController.java
├─ service/
│  ├─ RecommendationService.java    # 推荐引擎
│  ├─ FeedbackService.java          # 不感兴趣反馈
│  └─ RuleConfigService.java        # 规则配置读取（本地缓存 60s）
├─ support/CrossDbCourseAccessor.java  # 跨库只读查询
├─ entity/
│  ├─ RecommendationRuleConfigEntity.java
│  └─ RecommendationFeedbackEntity.java
├─ mapper/
│  ├─ RecommendationRuleConfigMapper.java
│  └─ RecommendationFeedbackMapper.java
└─ dto/
   ├─ response/RecommendationItem.java
   ├─ response/RecommendationResponse.java
   └─ request/FeedbackRequest.java
```

## 3. 数据模型

### 3.1 迁移脚本 `deploy/sql/recommendation/V001__rule_recommendations.sql`

```sql
-- 规则配置表
CREATE TABLE recommendation_rule_config (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  rule_key       VARCHAR(20)  NOT NULL COMMENT 'POPULAR / NEW / SIMILAR',
  enabled        TINYINT      NOT NULL DEFAULT 1,
  weight         INT          NOT NULL COMMENT '权重 0-100，总和不必为 100（按占比分配）',
  config_version INT          NOT NULL DEFAULT 1,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_key (rule_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '推荐规则配置';

-- 种子数据：热门 40 / 新品 30 / 同类目 30
INSERT INTO recommendation_rule_config (rule_key, enabled, weight, config_version)
VALUES ('POPULAR', 1, 40, 1), ('NEW', 1, 30, 1), ('SIMILAR', 1, 30, 1);

-- 反馈表（「不感兴趣」降权）
CREATE TABLE recommendation_feedback (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  course_id  BIGINT       NOT NULL,
  action     VARCHAR(20)  NOT NULL DEFAULT 'DISLIKE',
  reason     VARCHAR(255) NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_feedback (user_id, course_id, action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '推荐反馈（不感兴趣）';
```

- **幂等**：重复反馈依赖 `uk_feedback` 唯一约束，采用 `INSERT ... ON DUPLICATE KEY UPDATE id = id` 静默成功。
- `config_version` 为后续管理端改配置预留；本次种子固定为 1。

## 4. 跨库只读访问

### 4.1 数据源

主数据源单库 + 跨库 SQL（与 analytics `CrossDbBatchExtractor` 先例一致，不配置多数据源）：

| 数据源 | 连接库 | 用途 |
|---|---|---|
| 主数据源 | `educloud_recommendation` | 规则配置、反馈读写 |
| 跨库 SQL（只读） | `educloud_course` | 课程可见性 / 分类 / 发布时间 / 选课 / 热门度 |

> 应用账号需具备 `educloud_course` 只读 SELECT 权限（VM 部署时授权）。

### 4.2 查询清单（`CrossDbCourseAccessor`，全部只读 SQL + `RowMapper`）

| # | 查询 | 来源库 | 说明 |
|---|---|---|---|
| 1 | 可见课程基础信息 | course | `course.lifecycle_status = 'PUBLISHED'` 且 `published_version_id` 非空；JOIN `course_version`（跟随 published_version_id）与 `course_category`；字段：id / title / category_id / category_name / published_at / price / cover_file_id / enrollment_count / rating_avg |
| 2 | 用户已购/已学课程 | course | `course_enrollment`（ACTIVE）按 student_id 查课程 ID 集合 |
| 3 | 已购课程上下文 | course | `course_enrollment` JOIN course/version/category，取课程名 + 分类（同类目理由文案用，LIMIT 50） |
| 4 | 封面直链 | file | `file_object` 按 cover_file_id 批量查 bucket / object_key，组装 MinIO 公开直链（与 content 模块头像反查同模式） |

> **热门度说明**：`course` 表自带 `enrollment_count` 与 `rating_avg`（course 服务维护的实时冗余字段），POPULAR 打分直接使用，不依赖 analytics。

### 4.3 容错规则

- 每个查询独立 `try-catch`：失败 → 依赖该查询的策略跳过，空缺由其他策略补齐。
- 全部查询失败 → 返回空列表（前端回退静态数据）。
- 课程封面沿用 MinIO 公开直链（`course.cover_url` 已存直链），不引入新的鉴权链路。

## 5. 推荐引擎

### 5.1 输入

| 参数 | 来源 | 说明 |
|---|---|---|
| userId | JWT（可空） | 未登录 = null |
| courseId | query（可空） | 非空 = 相关课程场景 |
| limit | query | 钳制 1~20，默认 10 |

### 5.2 执行流程

1. **读配置**：`RuleConfigService` 加载启用的规则与权重，本地缓存 60 秒。
2. **候选集**：查询 1（可见课程）。
3. **排除集**：
   - 已购/已学课程（查询 3，仅登录）；
   - 该用户 DISLIKE 过的课程（feedback 表，仅登录）；
   - 相关课程场景：排除目标课程自身。
4. **分策略打分**：
   - POPULAR：`score = enrollment_count × 0.7 + avg_rating × 10`，降序（数据源：`course` 表自带 `enrollment_count` / `rating_avg`，查询失败则跳过该策略）。**avg_rating 为 NULL（无评分）时按 0 计算**，score 永不为 NULL。
   - NEW：`published_at` 降序（数据源：查询 1）。
   - SIMILAR：登录且存在已购课程 → 已购课程的 category_id 集合；相关课程场景 → 目标课程 category_id。同分类课程按热门度排序。无已购 / 未登录 / 目标课程无分类 → 跳过。
5. **按权重分配条数**：每策略配额 = 四舍五入（limit × weight / 权重总和）；四舍五入导致的差额加到权重最大的策略。某策略不足时，空缺由其余策略按权重序补齐。
6. **去重与排序**：同一课程命中多策略只保留一次，reason 按优先级 POPULAR > NEW > SIMILAR 取最高者；最终按 `(score 降序, course_id 数值升序)` 稳定排序——固定输入得到固定输出。
7. **附加理由**：
   - POPULAR → `热门课程`
   - NEW → `新上架`
   - SIMILAR → 首页场景：`与你学习的《{已购课名}》同属「{分类名}」`；详情场景：`与本课程同属「{分类名}」`

## 6. API 契约

### 6.1 推荐查询

`GET /api/v1/recommendations`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| context | string | 是 | `home` / `course` |
| courseId | string | 否 | context = course 时必填（Snowflake ID 字符串） |
| limit | integer | 否 | 1~20，默认 10 |

鉴权：可选登录（未登录返回热门 + 新品，按启用权重 40/30 分配、差额由热门补齐）。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "configVersion": 1,
    "items": [
      {
        "courseId": "2092160723423784961",
        "title": "Python 入门",
        "categoryId": "3",
        "coverUrl": "http://192.168.100.136:9000/educloud-files/cover/xxx.png",
        "price": "168.00",
        "reason": "热门课程",
        "strategy": "POPULAR"
      }
    ]
  }
}
```

字段说明：
- 所有 ID 为字符串（Snowflake 精度规范）；
- `price` 为十进制金额字符串，单位：元（与 course 服务 `CourseSummaryResponse.price` 对齐）；
- 字段与 course 服务 `CourseSummaryResponse` 对齐，前端一个 API 直接渲染。

### 6.2 反馈提交

`POST /api/v1/recommendations/feedback`

请求体：

```json
{
  "courseId": "2092160723423784961",
  "action": "DISLIKE",
  "reason": "不感兴趣"
}
```

鉴权：必须登录（`userId` 取自 JWT）。重复提交幂等（唯一约束），统一返回成功。

### 6.3 网关

路由已预留（`RouteGroups.RECOMMENDATION` + yml `recommendation-core`）；仅需 `AccessPolicy.PUBLIC_READ` 将 `/api/v1/recommendations/courses` 改为 `/api/v1/recommendations/**`（PUBLIC_READ 仅匹配 GET/HEAD，POST feedback 仍要求认证）。

## 7. 前端改造（student-portal）

### 7.1 Home.tsx「为你推荐」区块（现有第 209-236 行区块改造）

- 挂载时调用 `GET /api/v1/recommendations?context=home`（登录带 Token → 个性化）。
- **降级链**：接口失败 / 返回空 → 回退现有静态「热门课程」数据（保持现状不报错）。
- 每张推荐卡片 hover 显示 ✕「不感兴趣」按钮 → 调用 `POST feedback` → 本地立即移除该卡（乐观更新，失败静默）。

### 7.2 CourseDetail.tsx「相关课程」区块（新增）

- 调用 `GET /api/v1/recommendations?context=course&courseId={id}&limit=6`。
- 同样降级链与「不感兴趣」交互；数据为空时隐藏整个区块。

### 7.3 基础设施

- `services/api.ts` 新增 `getRecommendations(context, courseId?, limit?)` 与 `dislikeCourse(courseId)`。
- `types/index.ts` 新增 `RecommendationItem` 与 `RecommendationResponse`，字段与 6.1 响应一致；复用现有课程卡片渲染组件。

## 8. 错误处理与降级（汇总）

| 故障 | 行为 |
|---|---|
| 推荐服务不可用（网关 503 / 超时） | 前端 catch → 回退热门静态数据 |
| 任一跨库查询失败 | 该策略跳过，其余补齐 |
| 全部跨库失败 | 返回空列表 → 前端回退 |
| 反馈写入失败 / 重复 | 静默成功（幂等），不阻塞 UI |
| 未登录访问 | 返回热门 + 新品（按权重 40/30 分配、差额由热门补齐），无个性化 |
| limit 越界 / 参数非法 | 400 + 错误码，不影响其他端点 |

核心原则：推荐是纯派生能力，任何故障不得影响主链路。

## 9. 测试计划

| 层级 | 内容 |
|---|---|
| 引擎单测 | 权重分配与取整、跨策略去重、确定性排序（同分按 course_id）、排除已购 / DISLIKE、策略空缺补齐、未登录降级、limit 钳制、相关课程排除自身 |
| 反馈单测 | 重复反馈幂等（唯一约束）、未登录 401、参数校验 |
| 跨库单测 | Mockito mock `JdbcTemplate`：查询失败 → 策略跳过；RowMapper 字段映射 |
| 服务验证 | `mvn -f educloud-backend/pom.xml -pl educloud-recommendation -am verify` |
| 前端 | student-portal `tsc --noEmit` + `npm run build`（其余两端回归 `tsc --noEmit`） |
| 浏览器 E2E | 首页推荐展示与理由标签；点「不感兴趣」后卡片消失；详情页相关课程展示；断网关时回退静态 |

## 10. 交付物与验收标准

| 交付物 | 验收标准 |
|---|---|
| `deploy/sql/recommendation/V001__rule_recommendations.sql` | 空库迁移成功，种子数据就位 |
| `educloud-recommendation` 模块 | `mvn verify` 通过，服务注册 Nacos，双端口健康 |
| 网关路由 | `/api/v1/recommendations` 经网关可达，CORS 放行 |
| student-portal 两处 UI | 三端 `tsc --noEmit` 0 错误，student-portal `build` 通过 |
| E2E 验证 | 浏览器全流程验证通过（见测试计划） |
| 部署与提交 | VM 部署冒烟通过；中文约定式提交推送 GitHub main |

## 11. 明确不做（YAGNI 边界）

- ❌ 不引入 AI / 向量模型 / embedding（路线图 Task 34 明确禁止）。
- ❌ 不做点击 / 曝光埋点与转化率统计。
- ❌ 不做管理端规则配置 UI（仅保留 `config_version` 读取逻辑）。
- ❌ 不消费领域事件、不维护投影表、不做 W1/W2 快照重建（跨库直读，源库即真理）。
- ❌ 不引入 Redis / RabbitMQ / OpenFeign 依赖。
- ❌ 不做游客历史行为追踪（无 Cookie / 设备指纹）。

## 12. 参考

- 路线图：`docs/superpowers/plans/2026-08-18-educloud-backend-roadmap.md` Task 34（规则推荐和降级）。
- 复用模式：analytics 模块 `security/`（JWT 校验）、`CrossDbBatchExtractor`（跨库查询）；content 模块 `AssignmentService`（跨库反查 + 容错降级）。
- M12 成果：`educloud_analytics.course_engagement_stats`（热门度数据源）。
