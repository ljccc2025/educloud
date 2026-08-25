# EduCloud M11 搜索中心（educloud-search）技术设计规格说明书

> 模块编号：M11  
> 模块名称：`educloud-search`（全局搜索中心与 Elasticsearch 检索微服务）  
> 日期：2026-08-25  
> 状态：已评审设计（Spec Approved）  

---

## 1. 系统定位与核心架构

### 1.1 模块定位与职责边界
`educloud-search` 是 EduCloud 架构中负责全文检索、实时搜索建议与多维数据聚合的查询面微服务：
1. **纯读优化与投影面**：作为课程域（Course）与课件内容域（Content）的数据投影面，不持有课程或内容的权威写权限，所有索引数据由事件驱动异步同步；
2. **高性能多字段加权检索**：基于 Elasticsearch 8.14.0 提供多字段加权（标题 3.0 > 副标题 2.0 > 讲师 1.5 > 描述 1.0）的高性能毫秒级检索；
3. **前缀补全与搜索建议**：提供高效的 Suggest API，在用户键入时毫秒级返回匹配课程与热门建议；
4. **多维聚合筛选**：支持按课程分类、难度等级、价格区间、免费/收费、评分等维度的 Aggregations 聚合统计与过滤；
5. **别名滚动全量重建**：提供全量索引平滑重建能力，通过 Elasticsearch Index Alias 实现零停机原子切换；
6. **故障优雅降级**：当 Elasticsearch 发生网络分区或集群不可用时，自动降级为 MySQL 只读轻量模糊查询，保证搜索 API 始终不中断。

### 1.2 端口与网络规划
- **业务 HTTP 端口**：`8099`（Nacos 服务名：`educloud-search`）
- **运维与监控端口**：`8100`（Spring Boot Actuator，仅本地与网关内网探针可达）
- **网关路由规则**：
  - 路径匹配：`/api/v1/search/**`
  - 路由目标：`lb://educloud-search`
  - 访问控制：除 `/api/v1/search/admin/**` 需鉴权与管理员权限外，其余搜索与建议接口支持匿名/公开访问。

---

## 2. 数据存储与 Elasticsearch 索引设计

### 2.1 物理存储与逻辑库
- **MySQL 逻辑数据库**：`educloud_search`
  - 仅存储搜索中心的运维与任务数据，包括索引重建任务表 `search_index_task` 与事件幂等接收箱 `search_sync_inbox`。
- **Elasticsearch 集群**：`http://127.0.0.1:9200`（单节点，8.14.0）。

### 2.2 Elasticsearch 索引与别名设计（Alias Rollover）
- **公开访问别名（Alias）**：`educloud_course_search`
- **实际物理索引命名规则**：`educloud_course_v{timestamp}` 或 `educloud_course_v1`
- **Mapping 结构规范**：
```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "search_text_analyzer": {
          "type": "standard"
        },
        "search_suggest_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "courseId": { "type": "keyword" },
      "title": {
        "type": "text",
        "analyzer": "search_text_analyzer",
        "fields": {
          "keyword": { "type": "keyword", "ignore_above": 256 },
          "suggest": { "type": "completion", "analyzer": "search_suggest_analyzer" }
        }
      },
      "subtitle": { "type": "text", "analyzer": "search_text_analyzer" },
      "description": { "type": "text", "analyzer": "search_text_analyzer" },
      "teacherId": { "type": "keyword" },
      "teacherName": {
        "type": "text",
        "analyzer": "search_text_analyzer",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "category": { "type": "keyword" },
      "categoryCode": { "type": "keyword" },
      "coverUrl": { "type": "keyword", "index": false },
      "difficulty": { "type": "keyword" },
      "priceCents": { "type": "long" },
      "isFree": { "type": "boolean" },
      "rating": { "type": "float" },
      "studentCount": { "type": "integer" },
      "lessonCount": { "type": "integer" },
      "status": { "type": "keyword" },
      "tags": { "type": "keyword" },
      "lessons": {
        "type": "nested",
        "properties": {
          "id": { "type": "keyword" },
          "title": { "type": "text", "analyzer": "search_text_analyzer" },
          "chapterTitle": { "type": "text", "analyzer": "search_text_analyzer" },
          "isPreview": { "type": "boolean" }
        }
      },
      "aggregateVersion": { "type": "long" },
      "publishedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis" },
      "updatedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis" }
    }
  }
}
```

### 2.3 数据库表结构设计（`educloud_search`）

#### 1. 索引重建任务表：`search_index_task`
```sql
CREATE TABLE IF NOT EXISTS `search_index_task` (
    `id` BIGINT UNSIGNED NOT NULL COMMENT '雪花ID',
    `task_no` VARCHAR(64) NOT NULL COMMENT '任务唯一编号',
    `index_name` VARCHAR(128) NOT NULL COMMENT '目标物理索引名称',
    `alias_name` VARCHAR(128) NOT NULL COMMENT '关联别名',
    `task_type` VARCHAR(32) NOT NULL COMMENT '任务类型: FULL_REBUILD / INCREMENTAL_REPAIR',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / RUNNING / SUCCESS / FAILED',
    `total_records` INT NOT NULL DEFAULT 0 COMMENT '待处理总记录数',
    `processed_records` INT NOT NULL DEFAULT 0 COMMENT '已成功处理记录数',
    `failed_records` INT NOT NULL DEFAULT 0 COMMENT '失败记录数',
    `error_message` TEXT NULL COMMENT '失败异常原因',
    `started_at` DATETIME NULL COMMENT '开始时间',
    `finished_at` DATETIME NULL COMMENT '完成时间',
    `created_by` VARCHAR(64) NOT NULL COMMENT '触发人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索索引任务表';
```

#### 2. 事件消费幂等接收箱：`search_sync_inbox`
```sql
CREATE TABLE IF NOT EXISTS `search_sync_inbox` (
    `id` BIGINT UNSIGNED NOT NULL COMMENT '雪花ID',
    `message_id` VARCHAR(128) NOT NULL COMMENT '消息全局唯一ID',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
    `aggregate_type` VARCHAR(64) NOT NULL COMMENT '聚合根类型',
    `aggregate_id` VARCHAR(64) NOT NULL COMMENT '聚合根ID',
    `aggregate_version` BIGINT NOT NULL COMMENT '聚合根单调递增版本',
    `payload` JSON NOT NULL COMMENT '事件消息载荷',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSED' COMMENT '处理状态: PROCESSED / FAILED',
    `error_reason` VARCHAR(512) NULL COMMENT '失败原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_id` (`message_id`),
    KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索事件消费接收箱';
```

---

## 3. 领域事件驱动与实时索引同步

### 3.1 监听队列与绑定关系
- **交换机**：
  - `educloud.course.events`（Topic Exchange）
  - `educloud.content.events`（Topic Exchange）
- **队列**：
  - `search.course.sync.queue`（Routing Key: `course.*`）
  - `search.content.sync.queue`（Routing Key: `content.*`）
- **死信队列**：`search.sync.dlq`（处理 3 次重试仍失败的畸形报文）。

### 3.2 增量同步状态机与版本控制
1. **`CoursePublishedEvent` / `CourseUpdatedEvent`**：
   - 检查 `aggregateVersion`：若小于等于已有 Document 版本则视为旧消息直接 ACK 丢弃；
   - 若状态为 `PUBLISHED`：查询或提取课程数据，Upsert 写入 Elasticsearch Document；
   - 若状态变更为了非 `PUBLISHED`（如 `OFFLINE`、`DRAFT`、`ARCHIVED`）：直接执行 ES Delete 操作。
2. **`CourseOfflineEvent` / `CourseDeletedEvent`**：
   - 从 ES 别名索引中直接删除对应的 Document，避免学员搜到已下架课程。
3. **`LessonPublishedEvent` / `LessonUpdatedEvent`**：
   - 使用 ES 局部更新或提取该课程的最新章节，刷新 Document 中的 `lessons` 数组与 `lessonCount`。

---

## 4. 搜索检索与 API 契约设计

### 4.1 课程全文检索接口
- **端点**：`GET /api/v1/search/courses`
- **鉴权**：匿名公开访问（白名单）
- **查询参数**：
  - `keyword` (string, 可选): 搜索关键词（支持多词分词、模糊纠错）
  - `category` (string, 可选): 类目名称或编码筛选
  - `difficulty` (string, 可选): 难度等级 (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`)
  - `isFree` (boolean, 可选): 是否仅看免费
  - `minPriceCents` / `maxPriceCents` (long, 可选): 价格筛选区间（以分为单位）
  - `sortBy` (string, 默认 `relevance`): 排序策略 (`relevance`, `popular`, `newest`, `price_asc`, `price_desc`)
  - `page` (int, 默认 1): 当前页码
  - `size` (int, 默认 10, 最大 50): 每页数量
- **响应体格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 42,
    "page": 1,
    "size": 10,
    "isDegraded": false,
    "items": [
      {
        "id": "2091648316809035780",
        "title": "Spring Cloud <em class=\"search-highlight\">微服务</em>实战开发",
        "subtitle": "从零构建高可用企业级云原生架构",
        "description": "全面掌握 <em class=\"search-highlight\">微服务</em> 架构核心要素...",
        "coverUrl": "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=600",
        "category": "后端开发",
        "teacherId": "9000000000000000001",
        "teacherName": "李明远",
        "difficulty": "INTERMEDIATE",
        "priceCents": 19900,
        "isFree": false,
        "rating": 4.9,
        "studentCount": 1280,
        "lessonCount": 24,
        "tags": ["Spring Boot", "微服务", "Docker"]
      }
    ],
    "aggregations": {
      "categories": [
        { "key": "后端开发", "count": 18 },
        { "key": "人工智能", "count": 12 }
      ],
      "difficulties": [
        { "key": "BEGINNER", "count": 10 },
        { "key": "INTERMEDIATE", "count": 22 },
        { "key": "ADVANCED", "count": 10 }
      ],
      "priceRanges": [
        { "key": "FREE", "count": 8 },
        { "key": "PAID_UNDER_100", "count": 14 },
        { "key": "PAID_OVER_100", "count": 20 }
      ]
    }
  }
}
```

### 4.2 智能搜索建议与自动补全接口
- **端点**：`GET /api/v1/search/suggest`
- **鉴权**：匿名公开访问
- **查询参数**：
  - `q` (string, 必填): 关键词前缀（至少 1 个字符）
  - `limit` (int, 默认 8, 最大 20)
- **响应体格式**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "suggestions": [
      {
        "text": "Spring Cloud 微服务实战",
        "highlight": "<em>Spring</em> Cloud 微服务实战",
        "category": "后端开发",
        "type": "COURSE",
        "targetId": "2091648316809035780"
      },
      {
        "text": "Spring Boot 核心编程",
        "highlight": "<em>Spring</em> Boot 核心编程",
        "category": "后端开发",
        "type": "KEYWORD",
        "targetId": null
      }
    ]
  }
}
```

### 4.3 管理端全量重建与运维接口
- **触发全量重建**：`POST /api/v1/search/admin/rebuild-index`
  - 权限要求：`search:rebuild` 权限或管理员角色；
  - 返回：`{ "taskNo": "SR_20260825234500_A1B2", "status": "RUNNING" }`。
- **查询重建任务进度**：`GET /api/v1/search/admin/tasks/{taskNo}`
  - 返回任务详情、处理进度条与成功/失败量。

---

## 5. 故障优雅降级机制（Resilience）

1. **降级触发条件**：
   - Elasticsearch 连接超时（`ConnectTimeoutException` > 2000ms）；
   - Elasticsearch 节点不可用（`ElasticsearchException`、`ResourceAccessException`）。
2. **降级策略**：
   - 捕获 ES 异常后不向前端返回 500 错误；
   - 自动切换至 `DatabaseFallbackSearchService`，通过 MySQL 只读查询 `educloud_course.course` 基础表（`WHERE status = 'PUBLISHED' AND title LIKE '%keyword%'`）；
   - 响应结构中置 `isDegraded: true`，屏蔽 ES Aggregations 聚合字段，保持主列表与分页数据正常吐出。

---

## 6. 前端双端门户集成设计

1. **学生端门户（`student-portal:5173`）**：
   - 顶部导航栏搜索框（`Navbar.tsx`）：添加输入即搜防抖（300ms）联想浮层（`SuggestDropdown`），支持方向键上下选词与回车快速直达；
   - 课程列表与搜索结果页（`Courses.tsx`）：与 `/api/v1/search/courses` 完全对接，支持关键词高亮展示与左侧分类聚合标签联动。
2. **管理端门户（`admin-portal:5175`）**：
   - 运维中心：新增「搜索引擎管理」子看板，提供一键全量重建索引按钮、当前索引版本与 Document 存量监控卡片。

---

## 7. 质量门禁与验证策略

1. **后端单元与集成测试（`mvn test`）**：
   - 覆盖 ES Mapping 创建、高亮分词构建、RabbitMQ 增量事件消费幂等性、降级服务切换测试，单元测试覆盖率 > 85%。
2. **VM 虚拟机部署与探针验证**：
   - 部署至 Rocky Linux VM（`192.168.100.136`）；
   - 验证 `http://127.0.0.1:8100/actuator/health/readiness` 健康返回 `UP`；
   - 网关 `/api/v1/search/**` 路由转发验证。
3. **全链路 E2E 与 Playwright 视觉验收**：
   - 执行 `test_search_e2e.py` 自动化验证全流程（发布新课 ➔ ES 自动增量索引 ➔ 前端搜索框联想 ➔ 高亮检索 ➔ 别名平滑重建）；
   - Playwright 驱动浏览器验证学生端搜索框即时建议浮层与高亮渲染效果。
