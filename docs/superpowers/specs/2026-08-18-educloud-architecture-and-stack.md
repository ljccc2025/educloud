# EduCloud 总体架构与技术栈

> 状态：`【目标设计】`
>
> 前提：当前仓库尚无后端实现。

## 1. 架构目标

- 为学生端、教师端和管理端提供统一、可审计的后端入口。
- 维持既定的 12 服务边界，不随意增加新技术或新微服务。
- 核心交易和教学流程由权威服务保证，搜索、分析和推荐故障不阻塞主流程。
- 通过明确的数据所有权、接口契约和事件契约降低跨服务耦合。
- 支持从本地 Docker Compose 逐步演进到 Kubernetes 部署。

## 2. 逻辑架构

```text
┌──────────────────────────────────────────────────────────────┐
│  student-portal        teacher-portal        admin-portal   │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTPS / WebSocket
                               ▼
                    ┌──────────────────────┐
                    │  educloud-gateway    │
                    │ 路由/认证/限流/追踪   │
                    └──────────┬───────────┘
                               │
       ┌───────────────────────┼────────────────────────┐
       ▼                       ▼                        ▼
  核心领域服务             支撑领域服务             派生能力服务
  user/course/content      live/file/notification    analytics/search/
  order/payment                                      recommendation
       │                       │                        │
       └───────────────┬───────┴───────────────┬────────┘
                       ▼                       ▼
           MySQL / Redis / RabbitMQ    MinIO / Elasticsearch
                       │
                       ▼
        Nacos / Zipkin / Prometheus / Grafana
```

## 3. 服务与端口

沿用现有规范中的本地端口：

| 组件 | 端口 | 说明 |
|---|---:|---|
| Gateway | 8080 | 三套前端统一入口 |
| User | 8081 | 身份、用户、RBAC |
| Course | 8082 | 课程、分类、选课、课程审核 |
| Order | 8083 | 购物车、订单、退款申请 |
| Payment | 8084 | 支付、回调、退款、对账 |
| Content | 8085 | 章节、课件、进度、作业、考试、社区 |
| Live | 8086 | 直播控制面、聊天、观看和回放关系 |
| File | 8087 | 文件元数据和对象存储访问 |
| Notification | 8088 | 站内通知和投递任务 |
| Analytics | 8089 | 教学、运营、财务和审计聚合视图 |
| Search | 8090 | Elasticsearch 检索和索引任务 |
| Recommendation | 8091 | 规则推荐和反馈 |
| 学生端 | 5173 | Vite 开发端口 |
| 教师端 | 5174 | Vite 开发端口 |
| 管理端 | 5175 | Vite 开发端口 |

端口只用于本地开发。生产环境通过服务发现和 Kubernetes Service 通信，不把业务服务直接暴露到公网。

## 4. 固定技术栈

### 4.1 后端

| 技术 | 版本 | 用途 |
|---|---:|---|
| Java | 17 | 运行时与语言基线 |
| Spring Boot | 3.2.5 | 服务基础框架 |
| Spring Cloud | 2023.0.3 | 微服务基础能力 |
| Spring Cloud Alibaba | 2023.0.1.0 | Nacos 集成 |
| Nacos | 2.3.2 | 服务发现与配置 |
| MyBatis-Plus | 3.5.5 | 数据访问 |
| Spring Security | 随 Boot BOM | 认证和方法级授权 |
| JJWT | 0.12.5 | JWT 签发和验证 |
| Knife4j | 4.4.0 | OpenAPI 文档展示 |
| MinIO SDK | 8.5.7 | 对象存储 |

依赖版本由 Maven 父工程和 BOM 集中管理，子模块不得自行覆盖核心框架版本。

### 4.2 数据与中间件

| 技术 | 版本 | 用途 |
|---|---:|---|
| MySQL | 8.0.36 | 权威业务数据 |
| Redis | 7.2 | 缓存、验证码、会话撤销、限流、幂等辅助 |
| RabbitMQ | 3.13 | 领域事件、通知和异步任务 |
| Elasticsearch | 8.14 | 课程与内容检索 |
| MinIO | 与现有环境约定一致 | 文件对象 |

### 4.3 可观测性与部署

| 技术 | 用途 |
|---|---|
| Spring Boot Actuator | 健康与指标端点 |
| Zipkin | 分布式链路追踪 |
| Prometheus | 指标采集 |
| Grafana | 仪表盘与告警展示 |
| Docker Compose | 本地依赖编排 |
| Kubernetes 1.29 | 生产编排目标 |
| Helm | Kubernetes 模板化部署 |

### 4.4 前端现状

保持 React 18.3.1、TypeScript 5.5.4、Vite 5.4、React Router 6.27、Zustand 5、Axios 1.7 和 Tailwind CSS 3.4，不要求更换框架或重构既有信息架构。

### 4.5 测试栈

沿用既有总体设计：JUnit 5.10、Mockito 5.11、Testcontainers 1.19 和 Playwright 1.47。JUnit/Mockito/Testcontainers 进入 Maven 测试依赖；Playwright 建立独立 `educloud-frontend/e2e` 工程，不改变三个门户现有包管理边界。

## 5. 服务通信

### 5.1 外部请求

- 浏览器只访问 Gateway。
- Gateway 完成路由、Access Token 基础验证、跨域、安全响应头、限流和追踪 ID 注入。
- 业务服务必须再次执行权限与资源归属校验，不能只信任 Gateway 或前端。

### 5.2 同步调用

- 需要立即返回的查询或命令使用 REST。
- 服务内部可使用现有 Spring Cloud OpenFeign 体系，但必须设置超时。
- 禁止同一请求链形成递归调用环；跨域发布就绪等状态优先用事件投影到调用方本库，聚合查询优先由前端分别请求或由明确的权威服务返回必要快照。
- Gateway 不承担订单、支付、选课等业务编排。

### 5.3 异步事件

RabbitMQ 用于：

- 支付成功、退款完成、订单状态变化。
- 课程发布、内容更新和索引刷新。
- 选课成功、作业批改和考试成绩通知。
- 统计、审计和推荐反馈。
- 文件绑定和清理任务。

事件发布采用本地事务加 Outbox。消费者使用事件 ID 去重并保存处理结果。需要连续聚合版本的投影队列必须接收该聚合类型的完整事件信封；无关事件只推进版本水位。事件载荷只包含稳定业务 ID、必要快照、发生时间、版本和追踪信息。

## 6. 数据架构原则

- 每个服务拥有独立逻辑数据库和最小权限账号。
- 开发环境可以共用一个 MySQL 实例，但不能共表或跨库访问。
- 服务间不建立外键，不共享实体和 Mapper。
- Search、Analytics、Recommendation 的数据为派生视图，可以从权威服务事件重建。
- Redis 不作为订单、支付、成绩或权限的唯一事实来源。

## 7. 关键请求流

### 7.1 普通查询

```text
Portal → Gateway → 权威服务 → MySQL/Redis → Gateway → Portal
```

缓存只用于已定义失效策略的查询。缓存缺失时必须能够回源权威数据库。

### 7.2 课程购买

```text
Portal → Gateway → Order → Payment
                           │ 支付回调
                           ▼
                    PaymentSucceeded
                           ▼
                 Order → Course → Notification
                           └────→ Analytics
```

任何环节都不能由浏览器直接把订单或支付单改为成功。

### 7.3 课程发布

```text
Teacher → Gateway → Course 审核版本
Admin   → Gateway → Course 审批
CoursePublished → Search / Recommendation / Notification / Analytics
```

### 7.4 文件上传

```text
Portal → File 创建上传会话 → MinIO 上传 → File 确认完成
Portal → 领域服务绑定 fileId → File 接收绑定事件
```

领域表保存 `fileId`，不保存浏览器提交的任意对象存储内部路径。

## 8. 故障隔离

- Search 故障不影响课程详情和选课。
- Recommendation 故障返回热门课程或空结果。
- Analytics 故障不阻断交易与教学写操作。
- Notification 故障保留待投递任务，不回滚已完成业务。
- Payment 故障使订单停留在待支付，不生成虚假成功。
- Nacos、数据库、消息队列异常通过就绪检查和告警暴露，不以无限重启掩盖问题。

## 9. 架构约束

- 不引入第 13 个微服务；Admin 是门户，不是业务服务。
- 不在首期引入 Seata、服务网格、多租户、分库分表或事件溯源。
- `educloud-common` 不允许包含领域实体和跨服务业务逻辑。
- 所有外部供应商通过适配边界接入；没有凭证或沙箱时必须标注模拟状态。
- 真实 AI、真实音视频媒体和生产容量只有在完成专项决策与验证后才可承诺。
