# EduCloud 在线教育平台 — 全栈技术方案与架构文档

---

## 一、需求深度复述与分析

### 1.1 功能模块分析

| 功能模块 | 需求描述 | 技术要求 | 优先级 |
|---------|---------|---------|--------|
| 用户管理 | 注册、登录、角色管理、权限控制 | JWT认证 + RBAC权限模型 | P0 |
| 课程管理 | 课程创建、分类、搜索、推荐 | Elasticsearch全文搜索 + 推荐算法 | P0 |
| 内容管理 | 课程内容、章节、课件管理 | 对象存储(MinIO) + CDN分发 | P0 |
| 订单系统 | 订单创建、支付状态、退款处理 | 分布式事务 + 状态机 | P0 |
| 支付集成 | 支付宝/微信支付、退款、账单 | 支付回调 + 幂等性设计 | P0 |
| 直播系统 | 直播房间、实时互动、录制回放 | WebSocket + WebRTC + 录制服务 | P1 |
| 通知系统 | 站内信、邮件、短信通知 | 消息队列 + 多渠道推送 | P1 |
| 文件服务 | 文件上传、存储、CDN分发 | MinIO对象存储 + 分片上传 | P1 |
| 数据分析 | 学习数据、报表、统计 | 数据仓库 + 可视化图表 | P1 |
| 搜索服务 | 课程搜索、内容搜索 | Elasticsearch + 搜索建议 | P1 |
| 推荐系统 | 个性化推荐、学习路径 | 协同过滤 + 内容推荐 | P2 |
| 作业考试 | 作业提交、在线考试、自动评分 | 富文本编辑器 + 定时任务 | P1 |

### 1.2 非功能需求分析

| 需求维度 | 具体要求 | 技术挑战 |
|---------|---------|---------|
| 高可用 | 服务99.9%可用性 | 服务发现、负载均衡、熔断降级 |
| 高性能 | 接口响应<200ms，首页加载<2s | 缓存策略、CDN、数据库优化 |
| 可扩展 | 支持水平扩展 | 无状态设计、分布式会话 |
| 安全性 | 数据加密、防攻击 | JWT + HTTPS + 参数校验 |
| 可观测 | 日志、监控、链路追踪 | ELK + Prometheus + Zipkin |
| 容器化 | Docker + K8s部署 | Helm Charts + 配置管理 |

### 1.3 隐含技术挑战

| 挑战 | 描述 | 影响范围 |
|-----|------|---------|
| 分布式事务 | 订单+支付+库存跨服务事务 | Seata/TCC模式 |
| 实时通信 | 直播互动、消息推送 | WebSocket + 消息队列 |
| 数据一致性 | 缓存与数据库一致性 | Cache Aside Pattern |
| 服务治理 | 服务发现、配置管理、熔断 | Spring Cloud Alibaba |
| 链路追踪 | 跨服务调用追踪 | Micrometer + Zipkin |
| 安全认证 | 统一认证、权限校验 | Spring Security + OAuth2 |

### 1.4 本系统与单体应用的本质区别

| 对比维度 | 本系统(微服务) | 单体应用 | 传统分布式 |
|---------|---------------|---------|-----------|
| 服务拆分 | 12个独立服务，职责单一 | 所有功能在一个应用 | 粗粒度拆分 |
| 技术栈 | 每个服务可选最适合的技术 | 统一技术栈 | 统一技术栈 |
| 部署方式 | 独立部署、弹性伸缩 | 整体部署 | 整体部署 |
| 数据库 | 每个服务独立数据库 | 共享数据库 | 共享数据库 |
| 通信方式 | REST + 消息队列 | 进程内调用 | HTTP/RPC |
| 故障隔离 | 单服务故障不影响其他 | 一处故障全局崩溃 | 部分隔离 |

---

## 二、终极技术栈选型（带具体版本及决策矩阵）

### 2.1 后端框架

| 维度 | 详情 |
|------|------|
| **我们的选择** | **Spring Boot 3.2.x + Spring Cloud 2023.0.x** |
| 备选方案 | Go (Gin/gRPC)、Node.js (Nest.js)、Python (FastAPI) |
| 决定性理由 | 1) Java生态成熟度最高，企业级首选；2) Spring Cloud微服务组件完整(Nacos/Gateway/Feign)；3) 国内就业市场需求最大；4) 毕业设计展示价值最高 |
| 关键应用点 | REST API、服务注册发现、配置中心、网关路由、熔断降级 |

**🔧 核心技术栈**:
- `spring-boot` 3.2.5 — 核心框架
- `spring-cloud` 2023.0.3 — 微服务组件
- `spring-cloud-alibaba` 2023.0.1.0 — Nacos/Sentinel
- `spring-cloud-gateway` 4.1.4 — API网关
- `spring-security` 6.2.4 — 安全认证
- `mybatis-plus` 3.5.5 — ORM框架

**🎯 推荐Skills**: `senior-fullstack`（微服务架构）、`spring-boot-security-jwt`（JWT认证）

---

### 2.2 前端框架

| 维度 | 详情 |
|------|------|
| **我们的选择** | **React 18.3.x + TypeScript 5.5 + Vite 5.x** |
| 备选方案 | Vue 3 + Composition API、Angular 17 |
| 决定性理由 | 1) React生态最丰富，就业市场最大；2) TypeScript类型安全；3) Vite构建速度最快；4) shadcn/ui组件库现代化 |
| 关键应用点 | 三个独立前端应用、组件化开发、状态管理 |

**🔧 核心技术栈**:
- `react` 18.3.1 — UI框架
- `typescript` 5.5.4 — 类型系统
- `vite` 5.4.0 — 构建工具
- `react-router-dom` 6.27.0 — 路由管理
- `zustand` 5.0.0 — 状态管理
- `axios` 1.7.4 — HTTP客户端
- `tailwindcss` 3.4.10 — CSS框架
- `shadcn/ui` — 组件库

**🎯 推荐Skills**: `react-best-practices`（性能优化）、`react-patterns`（设计模式）、`frontend-design`（UI设计）

---

### 2.3 数据库与存储

| 维度 | 详情 |
|------|------|
| **我们的选择** | **MySQL 8.0 + Redis 7.0 + Elasticsearch 8.x + MinIO** |
| 备选方案 | PostgreSQL、MongoDB、OSS |
| 决定性理由 | 1) MySQL成熟稳定，国内使用最广；2) Redis高性能缓存；3) ES全文搜索；4) MinIO私有化部署 |
| 关键应用点 | 主数据库、缓存、搜索、对象存储 |

**🔧 核心技术栈**:
- `mysql` 8.0.36 — 关系型数据库
- `redis` 7.2 — 缓存与会话
- `elasticsearch` 8.14.0 — 全文搜索引擎
- `minio` — 对象存储
- `mybatis-plus` 3.5.5 — ORM框架
- `spring-data-redis` — Redis集成
- `spring-data-elasticsearch` — ES集成

**🎯 推荐Skills**: `database-design`（数据库设计）、`database-optimizer`（性能优化）

---

### 2.4 消息队列与异步处理

| 维度 | 详情 |
|------|------|
| **我们的选择** | **RabbitMQ 3.13** |
| 备选方案 | Kafka、RocketMQ |
| 决定性理由 | 1) 功能完整，支持多种消息模式；2) 管理界面友好；3) Spring AMQP集成完善；4) 适合中小规模场景 |
| 关键应用点 | 订单超时取消、通知推送、日志收集 |

**🔧 核心技术栈**:
- `rabbitmq` 3.13 — 消息队列
- `spring-amqp` — Spring集成
- `rabbitmq-delayed-message-exchange` — 延迟消息插件

**🎯 推荐Skills**: `senior-fullstack`（消息队列架构）

---

### 2.5 服务注册与配置中心

| 维度 | 详情 |
|------|------|
| **我们的选择** | **Nacos 2.3.x** |
| 备选方案 | Eureka + Config、Consul、Zookeeper |
| 决定性理由 | 1) 注册中心+配置中心一体化；2) 阿里开源，国内使用广泛；3) 支持多环境配置；4) 健康检查完善 |
| 关键应用点 | 服务注册发现、配置管理、命名空间隔离 |

**🔧 核心技术栈**:
- `nacos` 2.3.2 — 注册中心+配置中心
- `spring-cloud-starter-alibaba-nacos-discovery` — 服务发现
- `spring-cloud-starter-alibaba-nacos-config` — 配置管理

**🎯 推荐Skills**: `kubernetes`（服务治理）

---

### 2.6 API网关

| 维度 | 详情 |
|------|------|
| **我们的选择** | **Spring Cloud Gateway 4.1.x** |
| 备选方案 | Kong、Zuul、APISIX |
| 决定性理由 | 1) Spring生态原生支持；2) 响应式编程模型，性能优秀；3) 路由、限流、认证一站式；4) 与Nacos无缝集成 |
| 关键应用点 | 路由转发、限流熔断、统一认证、日志记录 |

**🔧 核心技术栈**:
- `spring-cloud-gateway` 4.1.4 — API网关
- `spring-cloud-starter-loadbalancer` — 负载均衡
- `resilience4j` — 熔断限流

**🎯 推荐Skills**: `senior-fullstack`（网关架构）

---

### 2.7 安全认证

| 维度 | 详情 |
|------|------|
| **我们的选择** | **Spring Security 6.x + JWT + OAuth2** |
| 备选方案 | Shiro、自研认证 |
| 决定性理由 | 1) Spring生态标准安全框架；2) JWT无状态认证；3) RBAC权限模型；4) OAuth2第三方登录 |
| 关键应用点 | 用户认证、权限校验、接口保护 |

**🔧 核心技术栈**:
- `spring-security` 6.2.4 — 安全框架
- `jjwt` 0.12.5 — JWT工具
- `spring-security-oauth2` — OAuth2支持

**🎯 推荐Skills**: `spring-boot-security-jwt`（JWT认证）

---

### 2.8 实时通信

| 维度 | 详情 |
|------|------|
| **我们的选择** | **WebSocket + STOMP + SockJS** |
| 备选方案 | Socket.IO、SSE、长轮询 |
| 决定性理由 | 1) Spring原生支持WebSocket；2) STOMP协议可靠；3) SockJS降级兼容；4) 适合直播互动场景 |
| 关键应用点 | 直播聊天、实时通知、在线状态 |

**🔧 核心技术栈**:
- `spring-websocket` — WebSocket支持
- `spring-messaging` — 消息协议
- `sockjs-client` — 客户端降级
- `stompjs` — STOMP协议

**🎯 推荐Skills**: `websocket-engineer`（实时通信）

---

### 2.9 链路追踪与可观测性

| 维度 | 详情 |
|------|------|
| **我们的选择** | **Micrometer Tracing + Zipkin + Prometheus + Grafana** |
| 备选方案 | SkyWalking、Jaeger、ELK |
| 决定性理由 | 1) Spring Boot Actuator原生支持；2) Zipkin轻量级；3) Prometheus+Grafana监控完善；4) 全链路追踪 |
| 关键应用点 | 链路追踪、性能监控、告警通知 |

**🔧 核心技术栈**:
- `micrometer-tracing` — 链路追踪
- `zipkin` 2.24 — 链路存储
- `prometheus` — 指标采集
- `grafana` — 可视化面板

**🎯 推荐Skills**: `distributed-tracing`（链路追踪）、`observability-engineer`（可观测性）

---

### 2.10 容器化与编排

| 维度 | 详情 |
|------|------|
| **我们的选择** | **Docker + Kubernetes + Helm** |
| 备选方案 | Docker Compose、Podman、Nomad |
| 决定性理由 | 1) K8s是容器编排事实标准；2) 云原生生产级部署；3) 弹性伸缩能力；4) 毕业设计亮点 |
| 关键应用点 | 容器化部署、服务编排、配置管理 |

**🔧 核心技术栈**:
- `docker` 24.x — 容器化
- `kubernetes` 1.29 — 容器编排
- `helm` 3.x — 包管理
- `ingress-nginx` — 入口控制器

**🎯 推荐Skills**: `kubernetes`（K8s部署）、`devops-engineer`（CI/CD）

---

### 2.11 CI/CD 流水线

| 维度 | 详情 |
|------|------|
| **我们的选择** | **GitHub Actions + Harbor + ArgoCD** |
| 备选方案 | GitLab CI、Jenkins、Tekton |
| 决定性理由 | 1) GitHub Actions免费且功能强大；2) Harbor私有镜像仓库；3) ArgoCD GitOps部署；4) 完整DevOps流程 |
| 关键应用点 | 代码检查、镜像构建、自动部署 |

**🔧 核心技术栈**:
- `github-actions` — CI/CD流水线
- `harbor` 2.x — 镜像仓库
- `argocd` — GitOps部署
- `sonarqube` — 代码质量

**🎯 推荐Skills**: `devops-engineer`（CI/CD）、`gitops-workflow`（GitOps）

---

### 2.12 测试与代码质量

| 维度 | 详情 |
|------|------|
| **我们的选择** | **JUnit 5 + Mockito + Testcontainers + Playwright** |
| 备选方案 | Spock、WireMock、Selenium |
| 决定性理由 | 1) JUnit 5是Java标准测试框架；2) Mock隔离依赖；3) Testcontainers集成测试；4) Playwright E2E测试 |
| 关键应用点 | 单元测试、集成测试、E2E测试 |

**🔧 核心技术栈**:
- `junit-jupiter` 5.10 — 单元测试
- `mockito` 5.11 — Mock框架
- `testcontainers` 1.19 — 集成测试
- `playwright` 1.47 — E2E测试

**🎯 推荐Skills**: `tdd`（测试驱动开发）、`testing-patterns`（测试模式）

---

## 三、高保真系统架构与模块详设

### 3.1 架构全景图（微服务拓扑）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              KUBERNETES CLUSTER                              │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                         INGRESS CONTROLLER                           │   │
│  │                    (nginx-ingress / traefik)                         │   │
│  └──────────────────────────────┬───────────────────────────────────────┘   │
│                                 │                                            │
│  ┌──────────────────────────────▼───────────────────────────────────────┐   │
│  │                        GATEWAY SERVICE                               │   │
│  │              (Spring Cloud Gateway :8080)                            │   │
│  │    路由转发 | 限流熔断 | 统一认证 | 日志记录 | 跨域处理              │   │
│  └──────────────────────────────┬───────────────────────────────────────┘   │
│                                 │                                            │
│  ┌──────────────────────────────▼───────────────────────────────────────┐   │
│  │                         SERVICE MESH                                  │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │   │
│  │  │   USER     │ │  COURSE    │ │   ORDER    │ │  PAYMENT   │       │   │
│  │  │  SERVICE   │ │  SERVICE   │ │  SERVICE   │ │  SERVICE   │       │   │
│  │  │   :8081    │ │   :8082    │ │   :8083    │ │   :8084    │       │   │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐       │   │
│  │  │  CONTENT   │ │    LIVE    │ │   FILE     │ │ NOTIFICATION│      │   │
│  │  │  SERVICE   │ │  SERVICE   │ │  SERVICE   │ │  SERVICE   │       │   │
│  │  │   :8085    │ │   :8086    │ │   :8087    │ │   :8088    │       │   │
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘       │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐                      │   │
│  │  │  ANALYTICS │ │   SEARCH   │ │ RECOMMEND  │                      │   │
│  │  │  SERVICE   │ │  SERVICE   │ │  SERVICE   │                      │   │
│  │  │   :8089    │ │   :8090    │ │   :8091    │                      │   │
│  │  └────────────┘ └────────────┘ └────────────┘                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       INFRASTRUCTURE                                 │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐│   │
│  │  │  MySQL   │ │  Redis   │ │   ES     │ │  MinIO   │ │ RabbitMQ ││   │
│  │  │  :3306   │ │  :6379   │ │  :9200   │ │  :9000   │ │  :5672   ││   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘│   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐              │   │
│  │  │  Nacos   │ │  Zipkin  │ │Prometheus│ │ Grafana  │              │   │
│  │  │  :8848   │ │  :9411   │ │  :9090   │ │  :3000   │              │   │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 3.2 全部功能模块深度分解（总计12个核心服务 + 3个前端应用）

---

#### 模块 01: 用户服务 (user-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M01-UserService |
| **物理文件路径** | `backend/user-service/` |
| **核心职责** | 用户注册、登录、JWT认证、角色管理、权限控制 |
| **对外API** | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/users/{id}`, `PUT /api/users/{id}` |
| **内部技术** | Spring Security 6.x, JWT, RBAC权限模型, BCrypt密码加密 |
| **交互流程** | 用户注册→密码加密→存储MySQL→登录→生成JWT→返回Token→后续请求携带Token→网关验证 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `spring-boot-starter-security` 6.2.4 — 安全框架
- `jjwt` 0.12.5 — JWT生成与验证
- `mybatis-plus` 3.5.5 — ORM框架
- `spring-data-redis` — Token存储与会话

**🎯 推荐Skills**: `spring-boot-security-jwt`（JWT认证）、`senior-fullstack`（用户系统架构）

**数据库表设计**:
```sql
-- 用户表
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  email VARCHAR(100),
  phone VARCHAR(20),
  avatar VARCHAR(255),
  role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',  -- STUDENT/TEACHER/ADMIN
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 角色权限表
CREATE TABLE sys_role_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role VARCHAR(20) NOT NULL,
  permission VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

#### 模块 02: 课程服务 (course-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M02-CourseService |
| **物理文件路径** | `backend/course-service/` |
| **核心职责** | 课程创建、分类管理、课程搜索、课程推荐 |
| **对外API** | `POST /api/courses`, `GET /api/courses/{id}`, `GET /api/courses?keyword=`, `GET /api/courses/recommended` |
| **内部技术** | Elasticsearch全文搜索, Redis缓存, 推荐算法 |
| **交互流程** | 教师创建课程→存储MySQL→同步到ES→学生搜索→ES返回结果→缓存热门课程 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `mybatis-plus` 3.5.5 — ORM框架
- `spring-data-elasticsearch` 5.2.0 — ES集成
- `spring-data-redis` — 缓存

**🎯 推荐Skills**: `senior-fullstack`（课程系统架构）、`database-optimizer`（搜索优化）

**数据库表设计**:
```sql
-- 课程表
CREATE TABLE course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  cover_image VARCHAR(255),
  category_id BIGINT,
  teacher_id BIGINT NOT NULL,
  price DECIMAL(10,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PUBLISHED/OFFLINE
  student_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 课程分类表
CREATE TABLE course_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  parent_id BIGINT DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

#### 模块 03: 内容服务 (content-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M03-ContentService |
| **物理文件路径** | `backend/content-service/` |
| **核心职责** | 课程内容、章节管理、课件上传、视频点播 |
| **对外API** | `POST /api/courses/{id}/chapters`, `GET /api/courses/{id}/content`, `POST /api/content/upload` |
| **内部技术** | MinIO对象存储, 视频转码, 分片上传 |
| **交互流程** | 教师上传课件→分片上传到MinIO→存储元数据→学生观看→从MinIO流式播放 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `minio` 8.5.7 — 对象存储SDK
- `mybatis-plus` 3.5.5 — ORM框架

**🎯 推荐Skills**: `senior-fullstack`（文件上传架构）

**数据库表设计**:
```sql
-- 章节表
CREATE TABLE chapter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 课件表
CREATE TABLE courseware (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  chapter_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  type VARCHAR(20) NOT NULL,  -- VIDEO/PDF/PPT/DOC
  file_url VARCHAR(500) NOT NULL,
  file_size BIGINT,
  duration INT,  -- 视频时长(秒)
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

#### 模块 04: 订单服务 (order-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M04-OrderService |
| **物理文件路径** | `backend/order-service/` |
| **核心职责** | 订单创建、订单查询、订单状态管理、超时取消 |
| **对外API** | `POST /api/orders`, `GET /api/orders/{id}`, `GET /api/orders?userId=`, `PUT /api/orders/{id}/cancel` |
| **内部技术** | 状态机, 分布式事务, RabbitMQ延迟队列 |
| **交互流程** | 学生下单→创建订单(待支付)→发送延迟消息→30分钟未支付→自动取消→释放库存 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `mybatis-plus` 3.5.5 — ORM框架
- `spring-amqp` — RabbitMQ集成
- `spring-state-machine` — 状态机

**🎯 推荐Skills**: `senior-fullstack`（订单系统架构）、`microservices-patterns`（分布式事务）

**数据库表设计**:
```sql
-- 订单表
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(50) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/PAID/CANCELLED/REFUNDED
  payment_time DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

---

#### 模块 05: 支付服务 (payment-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M05-PaymentService |
| **物理文件路径** | `backend/payment-service/` |
| **核心职责** | 支付宝/微信支付集成、支付回调处理、退款管理 |
| **对外API** | `POST /api/payments/create`, `POST /api/payments/notify`, `POST /api/payments/refund` |
| **内部技术** | 支付宝SDK、微信支付SDK、幂等性设计、签名验证 |
| **交互流程** | 创建支付→生成支付链接→用户支付→支付回调→验证签名→更新订单状态→通知用户 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `alipay-sdk-java` 4.39.0 — 支付宝SDK
- `wechatpay-java` 0.2.14 — 微信支付SDK
- `mybatis-plus` 3.5.5 — ORM框架

**🎯 推荐Skills**: `senior-fullstack`（支付系统架构）

---

#### 模块 06: 直播服务 (live-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M06-LiveService |
| **物理文件路径** | `backend/live-service/` |
| **核心职责** | 直播房间管理、实时互动、录制回放 |
| **对外API** | `POST /api/lives/rooms`, `GET /api/lives/rooms/{id}`, `POST /api/lives/rooms/{id}/start` |
| **内部技术** | WebSocket + STOMP, SRS流媒体服务器, 录制服务 |
| **交互流程** | 教师创建直播间→获取推流地址→开始直播→学生观看→实时互动→直播结束→生成回放 |

**🔧 核心技术栈**:
- `spring-boot-starter-websocket` — WebSocket支持
- `spring-messaging` — STOMP协议
- `sockjs-client` — 降级兼容
- `mybatis-plus` 3.5.5 — ORM框架

**🎯 推荐Skills**: `websocket-engineer`（实时通信）、`websocket-realtime-builder`（Socket.IO）

**数据库表设计**:
```sql
-- 直播房间表
CREATE TABLE live_room (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',  -- CREATED/LIVING/ENDED
  start_time DATETIME,
  end_time DATETIME,
  viewer_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 直播回放表
CREATE TABLE live_replay (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  video_url VARCHAR(500) NOT NULL,
  duration INT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

#### 模块 07: 通知服务 (notification-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M07-NotificationService |
| **物理文件路径** | `backend/notification-service/` |
| **核心职责** | 站内信、邮件通知、短信通知、推送通知 |
| **对外API** | `POST /api/notifications/send`, `GET /api/notifications?userId=`, `PUT /api/notifications/{id}/read` |
| **内部技术** | RabbitMQ异步发送, 邮件服务, 短信服务 |
| **交互流程** | 业务事件→发送消息到RabbitMQ→通知服务消费→根据渠道发送→记录发送状态 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `spring-boot-starter-mail` — 邮件发送
- `spring-amqp` — RabbitMQ集成
- `mybatis-plus` 3.5.5 — ORM框架

**🎯 推荐Skills**: `notification-best-practices`（通知最佳实践）

---

#### 模块 08: 文件服务 (file-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M08-FileService |
| **物理文件路径** | `backend/file-service/` |
| **核心职责** | 文件上传、文件存储、CDN分发、文件管理 |
| **对外API** | `POST /api/files/upload`, `GET /api/files/{id}`, `DELETE /api/files/{id}` |
| **内部技术** | MinIO对象存储, 分片上传, 断点续传 |
| **交互流程** | 文件上传→分片→上传到MinIO→合并分片→返回文件URL→存储元数据 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `minio` 8.5.7 — 对象存储SDK
- `mybatis-plus` 3.5.5 — ORM框架

**🎯 推荐Skills**: `senior-fullstack`（文件服务架构）

---

#### 模块 09: 分析服务 (analytics-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M09-AnalyticsService |
| **物理文件路径** | `backend/analytics-service/` |
| **核心职责** | 学习数据统计、报表生成、数据分析 |
| **对外API** | `GET /api/analytics/learning/{userId}`, `GET /api/analytics/courses/{courseId}`, `GET /api/analytics/dashboard` |
| **内部技术** | 数据仓库, 定时任务, 可视化图表 |
| **交互流程** | 用户行为→记录日志→定时聚合→生成报表→前端展示 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `mybatis-plus` 3.5.5 — ORM框架
- `spring-boot-starter-quartz` — 定时任务

**🎯 推荐Skills**: `data-analytics`（数据分析）

---

#### 模块 10: 搜索服务 (search-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M10-SearchService |
| **物理文件路径** | `backend/search-service/` |
| **核心职责** | 课程搜索、内容搜索、搜索建议 |
| **对外API** | `GET /api/search?keyword=`, `GET /api/search/suggest?keyword=` |
| **内部技术** | Elasticsearch全文搜索, 分词器, 搜索建议 |
| **交互流程** | 用户输入关键词→分词→ES搜索→返回结果→高亮显示 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `spring-data-elasticsearch` 5.2.0 — ES集成
- `elasticsearch-rest-high-level-client` — ES客户端

**🎯 推荐Skills**: `database-optimizer`（搜索优化）

---

#### 模块 11: 推荐服务 (recommendation-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M11-RecommendationService |
| **物理文件路径** | `backend/recommendation-service/` |
| **核心职责** | 个性化推荐、学习路径推荐 |
| **对外API** | `GET /api/recommendations/courses/{userId}`, `GET /api/recommendations/learning-path/{userId}` |
| **内部技术** | 协同过滤算法, 内容推荐, 机器学习 |
| **交互流程** | 用户行为→收集数据→计算相似度→生成推荐→缓存结果 |

**🔧 核心技术栈**:
- `spring-boot-starter-web` 3.2.5 — REST API
- `spring-data-redis` — 推荐结果缓存
- `mybatis-plus` 3.5.5 — ORM框架

**🎯 推荐Skills**: `senior-fullstack`（推荐算法）

---

#### 模块 12: 网关服务 (gateway-service)

| 属性 | 内容 |
|------|------|
| **模块ID** | M12-GatewayService |
| **物理文件路径** | `backend/gateway-service/` |
| **核心职责** | API路由、限流熔断、统一认证、日志记录、跨域处理 |
| **对外API** | 所有外部请求入口 |
| **内部技术** | Spring Cloud Gateway, Sentinel限流, JWT验证 |
| **交互流程** | 请求进入→路由匹配→限流检查→JWT验证→转发到后端服务→返回响应 |

**🔧 核心技术栈**:
- `spring-cloud-gateway` 4.1.4 — API网关
- `spring-cloud-starter-alibaba-sentinel` — 限流熔断
- `spring-cloud-starter-loadbalancer` — 负载均衡
- `jjwt` 0.12.5 — JWT验证

**🎯 推荐Skills**: `senior-fullstack`（网关架构）、`microservices-patterns`（服务治理）

---

#### 模块 13: 学生端前端 (student-portal)

| 属性 | 内容 |
|------|------|
| **模块ID** | M13-StudentPortal |
| **物理文件路径** | `frontend/student-portal/` |
| **核心职责** | 课程浏览、购买、学习、作业、考试、直播参与 |
| **对外API** | 调用后端REST API |
| **内部技术** | React 18, TypeScript, Zustand, React Router, Tailwind CSS |
| **交互流程** | 浏览课程→查看详情→购买支付→开始学习→完成作业→参加考试 |

**🔧 核心技术栈**:
- `react` 18.3.1 — UI框架
- `typescript` 5.5.4 — 类型系统
- `vite` 5.4.0 — 构建工具
- `react-router-dom` 6.27.0 — 路由管理
- `zustand` 5.0.0 — 状态管理
- `axios` 1.7.4 — HTTP客户端
- `tailwindcss` 3.4.10 — CSS框架
- `shadcn/ui` — 组件库

**🎯 推荐Skills**: `react-best-practices`（性能优化）、`frontend-design`（UI设计）

**页面结构**:
```
src/
├── pages/
│   ├── Home.tsx              # 首页（课程推荐）
│   ├── CourseList.tsx        # 课程列表
│   ├── CourseDetail.tsx      # 课程详情
│   ├── MyCourses.tsx         # 我的课程
│   ├── Learning.tsx          # 学习页面
│   ├── LiveRoom.tsx          # 直播房间
│   ├── Assignments.tsx       # 作业列表
│   ├── Exams.tsx             # 考试列表
│   ├── Profile.tsx           # 个人中心
│   └── Orders.tsx            # 订单管理
├── components/
│   ├── CourseCard.tsx         # 课程卡片
│   ├── VideoPlayer.tsx       # 视频播放器
│   ├── ProgressBar.tsx       # 进度条
│   └── ChatBox.tsx           # 聊天框
├── stores/
│   ├── useAuthStore.ts       # 认证状态
│   ├── useCourseStore.ts     # 课程状态
│   └── useCartStore.ts       # 购物车状态
└── layouts/
    └── StudentLayout.tsx     # 学生端布局
```

---

#### 模块 14: 教师端前端 (teacher-portal)

| 属性 | 内容 |
|------|------|
| **模块ID** | M14-TeacherPortal |
| **物理文件路径** | `frontend/teacher-portal/` |
| **核心职责** | 课程管理、内容上传、直播管理、作业批改、成绩管理 |
| **对外API** | 调用后端REST API |
| **内部技术** | React 18, TypeScript, Zustand, React Router, Tailwind CSS |
| **交互流程** | 创建课程→上传内容→开始直播→布置作业→批改作业→管理成绩 |

**🔧 核心技术栈**:
- `react` 18.3.1 — UI框架
- `typescript` 5.5.4 — 类型系统
- `vite` 5.4.0 — 构建工具
- `react-router-dom` 6.27.0 — 路由管理
- `zustand` 5.0.0 — 状态管理
- `axios` 1.7.4 — HTTP客户端
- `tailwindcss` 3.4.10 — CSS框架
- `shadcn/ui` — 组件库

**🎯 推荐Skills**: `react-best-practices`（性能优化）、`frontend-design`（UI设计）

**页面结构**:
```
src/
├── pages/
│   ├── Dashboard.tsx         # 工作台
│   ├── CourseManage.tsx      # 课程管理
│   ├── CourseEdit.tsx        # 课程编辑
│   ├── ContentManage.tsx     # 内容管理
│   ├── LiveManage.tsx        # 直播管理
│   ├── AssignmentGrade.tsx   # 作业批改
│   ├── ExamManage.tsx        # 考试管理
│   ├── StudentList.tsx       # 学生列表
│   └── Analytics.tsx         # 数据分析
├── components/
│   ├── CourseForm.tsx         # 课程表单
│   ├── ContentEditor.tsx     # 内容编辑器
│   ├── LivePreview.tsx       # 直播预览
│   └── GradeSheet.tsx        # 成绩单
├── stores/
│   ├── useAuthStore.ts       # 认证状态
│   ├── useCourseStore.ts     # 课程状态
│   └── useLiveStore.ts       # 直播状态
└── layouts/
    └── TeacherLayout.tsx     # 教师端布局
```

---

#### 模块 15: 管理后台前端 (admin-portal)

| 属性 | 内容 |
|------|------|
| **模块ID** | M15-AdminPortal |
| **物理文件路径** | `frontend/admin-portal/` |
| **核心职责** | 用户管理、课程审核、系统配置、数据统计、内容审核、财务管理 |
| **对外API** | 调用后端REST API |
| **内部技术** | React 18, TypeScript, Zustand, React Router, Tailwind CSS |
| **交互流程** | 用户管理→课程审核→内容审核→订单管理→数据统计→系统配置 |

**🔧 核心技术栈**:
- `react` 18.3.1 — UI框架
- `typescript` 5.5.4 — 类型系统
- `vite` 5.4.0 — 构建工具
- `react-router-dom` 6.27.0 — 路由管理
- `zustand` 5.0.0 — 状态管理
- `axios` 1.7.4 — HTTP客户端
- `tailwindcss` 3.4.10 — CSS框架
- `shadcn/ui` — 组件库
- `recharts` 2.12.0 — 图表库

**🎯 推荐Skills**: `react-best-practices`（性能优化）、`frontend-design`（UI设计）

**页面结构**:
```
src/
├── pages/
│   ├── Dashboard.tsx         # 数据看板
│   ├── UserManage.tsx        # 用户管理
│   ├── CourseAudit.tsx       # 课程审核
│   ├── ContentAudit.tsx      # 内容审核
│   ├── OrderManage.tsx       # 订单管理
│   ├── Finance.tsx           # 财务管理
│   ├── SystemConfig.tsx      # 系统配置
│   └── Logs.tsx              # 操作日志
├── components/
│   ├── DataTable.tsx          # 数据表格
│   ├── AuditModal.tsx        # 审核弹窗
│   ├── StatsCard.tsx         # 统计卡片
│   └── ConfigForm.tsx        # 配置表单
├── stores/
│   ├── useAuthStore.ts       # 认证状态
│   ├── useUserStore.ts       # 用户状态
│   └── useSystemStore.ts     # 系统状态
└── layouts/
    └── AdminLayout.tsx       # 管理后台布局
```

---

## 四、项目工程化终极指南

### 4.1 完整项目目录树

```
educloud/
├── frontend/
│   ├── student-portal/           # 学生端前端
│   │   ├── src/
│   │   │   ├── pages/            # 页面组件
│   │   │   ├── components/       # 通用组件
│   │   │   ├── stores/           # Zustand状态管理
│   │   │   ├── hooks/            # 自定义Hook
│   │   │   ├── services/         # API服务
│   │   │   ├── types/            # TypeScript类型
│   │   │   ├── utils/            # 工具函数
│   │   │   ├── styles/           # 样式文件
│   │   │   ├── App.tsx           # 根组件
│   │   │   └── main.tsx          # 入口文件
│   │   ├── public/               # 静态资源
│   │   ├── index.html            # HTML入口
│   │   ├── package.json          # 依赖配置
│   │   ├── tsconfig.json         # TypeScript配置
│   │   ├── vite.config.ts        # Vite配置
│   │   └── tailwind.config.js    # Tailwind配置
│   │
│   ├── teacher-portal/           # 教师端前端
│   │   └── ... (同student-portal结构)
│   │
│   ├── admin-portal/             # 管理后台前端
│   │   └── ... (同student-portal结构)
│   │
│   └── shared/                   # 共享组件库
│       ├── components/           # 共享组件
│       ├── hooks/                # 共享Hook
│       ├── types/                # 共享类型
│       └── utils/                # 共享工具
│
├── backend/
│   ├── user-service/             # 用户服务
│   │   ├── src/main/java/com/educloud/user/
│   │   │   ├── controller/       # 控制器
│   │   │   ├── service/          # 服务层
│   │   │   ├── repository/       # 数据访问层
│   │   │   ├── entity/           # 实体类
│   │   │   ├── dto/              # 数据传输对象
│   │   │   ├── config/           # 配置类
│   │   │   └── UserApplication.java
│   │   ├── src/main/resources/
│   │   │   ├── application.yml   # 应用配置
│   │   │   └── bootstrap.yml     # 启动配置
│   │   ├── pom.xml               # Maven配置
│   │   └── Dockerfile            # Docker配置
│   │
│   ├── course-service/           # 课程服务
│   │   └── ... (同user-service结构)
│   │
│   ├── order-service/            # 订单服务
│   │   └── ... (同user-service结构)
│   │
│   ├── payment-service/          # 支付服务
│   │   └── ... (同user-service结构)
│   │
│   ├── content-service/          # 内容服务
│   │   └── ... (同user-service结构)
│   │
│   ├── live-service/             # 直播服务
│   │   └── ... (同user-service结构)
│   │
│   ├── notification-service/     # 通知服务
│   │   └── ... (同user-service结构)
│   │
│   ├── file-service/             # 文件服务
│   │   └── ... (同user-service结构)
│   │
│   ├── analytics-service/        # 分析服务
│   │   └── ... (同user-service结构)
│   │
│   ├── search-service/           # 搜索服务
│   │   └── ... (同user-service结构)
│   │
│   ├── recommendation-service/   # 推荐服务
│   │   └── ... (同user-service结构)
│   │
│   ├── gateway-service/          # 网关服务
│   │   └── ... (同user-service结构)
│   │
│   └── common/                   # 公共模块
│       ├── src/main/java/com/educloud/common/
│       │   ├── exception/        # 异常处理
│       │   ├── response/         # 统一响应
│       │   ├── utils/            # 工具类
│       │   └── config/           # 公共配置
│       └── pom.xml
│
├── k8s/                          # Kubernetes配置
│   ├── namespaces/               # 命名空间
│   │   ├── educloud-dev.yaml
│   │   ├── educloud-staging.yaml
│   │   └── educloud-prod.yaml
│   ├── deployments/              # 部署配置
│   │   ├── user-service.yaml
│   │   ├── course-service.yaml
│   │   └── ...
│   ├── services/                 # 服务配置
│   │   ├── user-service-svc.yaml
│   │   ├── course-service-svc.yaml
│   │   └── ...
│   ├── ingress/                  # 入口配置
│   │   └── educloud-ingress.yaml
│   ├── configmaps/               # 配置映射
│   │   └── educloud-config.yaml
│   ├── secrets/                  # 密钥配置
│   │   └── educloud-secrets.yaml
│   └── helm/                     # Helm Charts
│       └── educloud/
│           ├── Chart.yaml
│           ├── values.yaml
│           └── templates/
│
├── docker/                       # Docker配置
│   ├── mysql/
│   │   └── init.sql              # 数据库初始化
│   ├── redis/
│   │   └── redis.conf            # Redis配置
│   ├── elasticsearch/
│   │   └── elasticsearch.yml     # ES配置
│   └── rabbitmq/
│       └── rabbitmq.conf         # RabbitMQ配置
│
├── docs/                         # 文档
│   ├── api/                      # API文档
│   ├── architecture/             # 架构文档
│   └── deployment/               # 部署文档
│
├── scripts/                      # 脚本工具
│   ├── build.sh                  # 构建脚本
│   ├── deploy.sh                 # 部署脚本
│   └── init-db.sh                # 数据库初始化
│
├── .github/                      # GitHub配置
│   └── workflows/
│       ├── ci.yml                # CI流水线
│       └── cd.yml                # CD流水线
│
├── docker-compose.yml            # Docker Compose配置
├── pom.xml                       # Maven父POM
└── README.md                     # 项目说明
```

---

### 4.2 后端 `pom.xml` (父POM)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.educloud</groupId>
    <artifactId>educloud</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <name>EduCloud Online Education Platform</name>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <modules>
        <module>common</module>
        <module>user-service</module>
        <module>course-service</module>
        <module>order-service</module>
        <module>payment-service</module>
        <module>content-service</module>
        <module>live-service</module>
        <module>notification-service</module>
        <module>file-service</module>
        <module>analytics-service</module>
        <module>search-service</module>
        <module>recommendation-service</module>
        <module>gateway-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <jjwt.version>0.12.5</jjwt.version>
        <minio.version>8.5.7</minio.version>
        <knife4j.version>4.4.0</knife4j.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Cloud -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud Alibaba -->
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring-cloud-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- MyBatis Plus -->
            <dependency>
                <groupId>com.baomidou</groupId>
                <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
                <version>${mybatis-plus.version}</version>
            </dependency>

            <!-- JWT -->
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
            </dependency>

            <!-- MinIO -->
            <dependency>
                <groupId>io.minio</groupId>
                <artifactId>minio</artifactId>
                <version>${minio.version}</version>
            </dependency>

            <!-- Knife4j API文档 -->
            <dependency>
                <groupId>com.github.xiaoymin</groupId>
                <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
                <version>${knife4j.version}</version>
            </dependency>

            <!-- 公共模块 -->
            <dependency>
                <groupId>com.educloud</groupId>
                <artifactId>educloud-common</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### 4.3 前端 `package.json` (student-portal)

```json
{
  "name": "educloud-student-portal",
  "version": "1.0.0",
  "description": "EduCloud Student Portal",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.27.0",
    "zustand": "^5.0.0",
    "axios": "^1.7.4",
    "clsx": "^2.1.1",
    "dayjs": "^1.11.13",
    "lucide-react": "^0.427.0",
    "class-variance-authority": "^0.7.0",
    "tailwind-merge": "^2.5.0",
    "tailwindcss-animate": "^1.0.7",
    "@radix-ui/react-dialog": "^1.1.1",
    "@radix-ui/react-dropdown-menu": "^2.1.1",
    "@radix-ui/react-label": "^2.1.0",
    "@radix-ui/react-select": "^2.1.1",
    "@radix-ui/react-slot": "^1.1.0",
    "@radix-ui/react-tabs": "^1.1.0",
    "@radix-ui/react-toast": "^1.2.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.10",
    "@types/react-dom": "^18.3.0",
    "@typescript-eslint/eslint-plugin": "^8.5.0",
    "@typescript-eslint/parser": "^8.5.0",
    "@vitejs/plugin-react": "^4.3.1",
    "autoprefixer": "^10.4.20",
    "eslint": "^8.57.0",
    "eslint-plugin-react-hooks": "^4.6.2",
    "eslint-plugin-react-refresh": "^0.4.11",
    "postcss": "^8.4.47",
    "tailwindcss": "^3.4.10",
    "typescript": "^5.5.4",
    "vite": "^5.4.0"
  }
}
```

---

### 4.4 Docker Compose 配置

```yaml
version: '3.8'

services:
  # MySQL
  mysql:
    image: mysql:8.0
    container_name: educloud-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123456
      MYSQL_DATABASE: educloud
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./docker/mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  # Redis
  redis:
    image: redis:7.2-alpine
    container_name: educloud-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  # Elasticsearch
  elasticsearch:
    image: elasticsearch:8.14.0
    container_name: educloud-es
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data

  # RabbitMQ
  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: educloud-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin123456
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq

  # MinIO
  minio:
    image: minio/minio
    container_name: educloud-minio
    environment:
      MINIO_ROOT_USER: admin
      MINIO_ROOT_PASSWORD: admin123456
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data
    command: server /data --console-address ":9001"

  # Nacos
  nacos:
    image: nacos/nacos-server:v2.3.2
    container_name: educloud-nacos
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: ""
    ports:
      - "8848:8848"
      - "9848:9848"

  # Zipkin
  zipkin:
    image: openzipkin/zipkin
    container_name: educloud-zipkin
    ports:
      - "9411:9411"

volumes:
  mysql-data:
  redis-data:
  es-data:
  rabbitmq-data:
  minio-data:
```

---

### 4.5 环境变量配置

创建 `.env` 文件：

```env
# ==============================
# EduCloud - 环境变量配置
# ==============================

# ---- 数据库配置 ----
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=educloud
MYSQL_USERNAME=root
MYSQL_PASSWORD=root123456

# ---- Redis配置 ----
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ---- Elasticsearch配置 ----
ES_HOST=localhost
ES_PORT=9200

# ---- RabbitMQ配置 ----
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=admin123456

# ---- MinIO配置 ----
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=admin
MINIO_SECRET_KEY=admin123456
MINIO_BUCKET=educloud

# ---- Nacos配置 ----
NACOS_HOST=localhost
NACOS_PORT=8848
NACOS_NAMESPACE=educloud-dev

# ---- JWT配置 ----
JWT_SECRET=your-jwt-secret-key-here
JWT_EXPIRATION=86400000

# ---- 支付配置 ----
ALIPAY_APP_ID=your-alipay-app-id
ALIPAY_PRIVATE_KEY=your-alipay-private-key
WECHAT_APP_ID=your-wechat-app-id
WECHAT_MCH_ID=your-wechat-mch-id
```

---

### 4.6 从零启动的"傻瓜式"指南

```markdown
# EduCloud — 从零启动指南

## 前置条件
- JDK 17+
- Maven 3.9+
- Node.js 20.x LTS
- pnpm 9.x
- Docker 24.x
- Docker Compose 2.x
- Git

## 步骤1：克隆项目
```bash
git clone <your-repo-url> educloud
cd educloud
```

## 步骤2：启动基础设施
```bash
# 启动MySQL、Redis、ES、RabbitMQ、MinIO、Nacos、Zipkin
docker-compose up -d

# 等待所有服务启动完成
docker-compose ps
```

## 步骤3：初始化数据库
```bash
# 连接MySQL执行初始化脚本
mysql -h localhost -u root -proot123456 < docker/mysql/init.sql
```

## 步骤4：启动后端服务
```bash
# 编译所有模块
mvn clean install -DskipTests

# 启动用户服务
cd backend/user-service
mvn spring-boot:run

# 启动其他服务（在不同终端）
cd backend/course-service
mvn spring-boot:run

# ... 依次启动其他服务
```

## 步骤5：启动前端应用
```bash
# 启动学生端
cd frontend/student-portal
pnpm install
pnpm dev

# 启动教师端（在不同终端）
cd frontend/teacher-portal
pnpm install
pnpm dev

# 启动管理后台（在不同终端）
cd frontend/admin-portal
pnpm install
pnpm dev
```

## 步骤6：访问应用
- 学生端: http://localhost:5173
- 教师端: http://localhost:5174
- 管理后台: http://localhost:5175
- Nacos控制台: http://localhost:8848/nacos (nacos/nacos)
- RabbitMQ控制台: http://localhost:15672 (admin/admin123456)
- MinIO控制台: http://localhost:9001 (admin/admin123456)
- Zipkin: http://localhost:9411

## 常用命令速查
| 命令 | 说明 |
|------|------|
| `docker-compose up -d` | 启动基础设施 |
| `docker-compose down` | 停止基础设施 |
| `mvn clean install` | 编译后端项目 |
| `mvn spring-boot:run` | 启动单个服务 |
| `pnpm dev` | 启动前端开发服务器 |
| `pnpm build` | 构建前端项目 |
```

---

## 五、关键设计决策补充

### 5.1 数据流向图

```
用户访问 → Nginx Ingress → Gateway Service
                              ↓ 路由匹配
                        JWT验证 + 限流
                              ↓ 转发
                        后端微服务
                              ↓ 业务处理
                        MySQL/Redis/ES
                              ↓ 返回结果
                        Gateway Service
                              ↓ 响应
                        前端应用
```

### 5.2 分布式事务设计

```
订单创建流程:
1. 用户下单 → Order Service
2. 扣减库存 → Course Service (TCC)
3. 创建支付 → Payment Service
4. 支付回调 → 更新订单状态
5. 通知用户 → Notification Service

失败处理:
- 库存不足 → 回滚订单
- 支付超时 → 自动取消订单 → 释放库存
- 支付失败 → 重试机制
```

### 5.3 缓存策略

```
缓存层次:
1. 本地缓存 (Caffeine) - 热点数据
2. 分布式缓存 (Redis) - 共享数据
3. 数据库 (MySQL) - 持久化数据

缓存模式:
- Cache Aside Pattern - 读写分离
- Write Behind Pattern - 异步写入
- Read Through Pattern - 缓存穿透保护
```

### 5.4 安全模型

```
┌────────────────────────────────────┐
│          CLIENT (Browser)          │
│  JWT Token存储在localStorage       │
└────────────────┬───────────────────┘
                 │ Authorization: Bearer <token>
┌────────────────▼───────────────────┐
│         GATEWAY SERVICE            │
│  - JWT验证 + 签名校验              │
│  - 路由转发 + 限流                 │
│  - 日志记录                        │
└────────────────┬───────────────────┘
                 │ 内部调用
┌────────────────▼───────────────────┐
│         BACKEND SERVICES           │
│  - Spring Security权限校验         │
│  - RBAC角色权限控制                │
│  - 数据权限过滤                    │
└────────────────────────────────────┘
```

### 5.5 性能优化策略

1. **数据库优化**: 索引优化、读写分离、分库分表
2. **缓存优化**: 多级缓存、缓存预热、缓存降级
3. **接口优化**: 接口合并、数据裁剪、异步处理
4. **前端优化**: 代码分割、懒加载、CDN加速
5. **网络优化**: HTTP/2、Gzip压缩、连接池

---

**文档版本：** v1.0
**创建日期：** 2026-08-17
**作者：** MiMoCode
