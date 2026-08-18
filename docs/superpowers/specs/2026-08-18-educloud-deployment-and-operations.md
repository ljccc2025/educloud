# EduCloud 部署、配置与运维规范

> 状态：`【目标设计】`
>
> 当前事实：仓库尚无后端容器、Docker Compose 或 Kubernetes 资源。

## 1. 目标目录

```text
项目根目录/
├─ educloud-frontend/
├─ educloud-backend/
│  ├─ pom.xml
│  ├─ educloud-common/
│  ├─ educloud-gateway/
│  └─ educloud-<service>/
└─ deploy/
   ├─ docker-compose/
   ├─ kubernetes/
   │  ├─ base/
   │  └─ overlays/<environment>/
   ├─ helm/
   └─ sql/<service>/
```

若现有实施计划已确定其他等价目录，落地时保持一个来源，不重复维护两套部署文件。

## 2. 环境

| 环境 | 目的 | 数据要求 |
|---|---|---|
| local | 开发者本地联调 | 可重建模拟数据，不使用生产凭据 |
| dev | 持续集成和多人联调 | 独立 namespace 与测试账号 |
| test | 集成、回归和迁移验证 | 可控测试数据，可反复初始化 |
| staging | 生产前验证 | 拓扑接近生产，使用沙箱外部服务 |
| production | 正式运行 | 最小权限、备份、告警和审计 |

Nacos 使用不同 namespace 隔离环境；Kubernetes 使用不同 namespace。禁止通过 profile 名称误连生产数据库。

## 3. 配置分级

| 配置类型 | 示例 | 来源 |
|---|---|---|
| 编译期常量 | 错误码、事件版本 | 代码 |
| 非敏感运行配置 | 超时、重试次数、分页上限 | Nacos/ConfigMap |
| 公开业务配置 | 站点名称、Logo、备案号 | User 数据库 |
| 敏感配置 | 数据库密码、JWT 私钥、SMTP/MinIO/支付密钥 | 环境变量/Kubernetes Secret |
| 本地占位 | `.env.example` | Git，可提交假值 |

- Secret 不能进入 Nacos 公共配置、前端环境变量、构建日志或 Git。
- 管理端可通过非敏感 `VITE_OPERATIONS_DASHBOARD_URL` 配置受保护 Grafana 入口；未配置时只显示缺失状态，不生成随机服务健康数据。Grafana 自身认证与网络访问由运维平台控制。
- 产品 API 不提供 Secret 读取或更新；生产更新通过 Kubernetes Secret/CI 受控发布，本地通过忽略提交的 `.env`，管理端只读取脱敏状态和执行限频连接测试。
- 配置变更需要操作者、时间、旧版本和回滚记录。
- 影响支付、认证和数据源的配置不允许无审批热改。
- 服务启动时校验必要配置，缺失时快速失败并给出不泄密的错误。

### 3.1 服务客户端凭据初始化与轮换

- User 服务镜像提供非 Web 的 `ServiceClientCredentialCommand`，只支持 `bootstrap/rotate/revoke/verify`，不是 HTTP 产品接口。
- 本地脚本从忽略提交的 `.env`/stdin 读取 11 个客户端 Secret；Kubernetes 一次性 Job 从独立 Secret volume 读取。Secret 不放命令行、ConfigMap、stdout 或审计摘要。
- `bootstrap` 以 `clientId + Secret 哈希` 幂等，已存在但值不同时失败；`rotate` 在客户端行锁内创建新 `ACTIVE`、旧凭据进入定时 `GRACE`，到期撤销。
- Job 使用最小化 User 数据库权限、固定允许受众/scope 清单和 `SERVICE_BOOTSTRAP_JOB` 审计主体；成功后删除 Job Pod，Secret 的保留/轮换由平台策略控制。
- 灾备同时恢复 User DB 与 Kubernetes Secret；先执行 `verify`。不匹配时通过恢复审批运行全量 `rotate` 并递增 `tokenVersion`，不能修改数据库哈希去迎合未知 Secret。

## 4. 本地 Docker Compose

目标依赖：

| 组件 | 默认端口 | 持久化 |
|---|---:|---|
| MySQL | 3306 | 必须 |
| Redis | 6379 | 推荐 |
| RabbitMQ | 5672 / 15672 | 必须 |
| Elasticsearch | 9200 | 必须 |
| MinIO | 9000 / 9001 | 必须 |
| Nacos | 8848 | 必须 |
| Zipkin | 9411 | 可重建 |
| Prometheus | 9090 | 本地可选持久化 |
| Grafana | 3000 | 本地可选持久化 |

要求：

- 所有镜像版本固定，不使用不可追踪的 `latest`。
- 使用健康检查和依赖就绪，而不是固定 sleep。
- 数据卷使用项目专用名称，清理脚本不得删除其他项目卷。
- 默认账号和密码仅适用于本地，并通过 `.env.example` 说明。
- 初始化脚本只创建逻辑数据库、账号和最小权限，不把全部服务授予 root。
- 业务账号对本库 `audit_event` 只授予 INSERT/SELECT；不得授予 UPDATE/DELETE。保留期清理由独立运维身份执行并记录审批证据。

## 5. 服务容器

- 使用 Java 17 运行时。
- 构建阶段和运行阶段分离，运行镜像不包含 Maven 缓存和源码。
- 以非 root 用户运行。
- 使用 UTC 时区处理数据；展示时区由前端处理。
- JVM 参数通过环境配置，不能硬编码生产堆大小。
- 暴露应用端口和 Actuator 健康端点。
- 容器文件系统默认只读；需要临时目录时显式挂载。
- 镜像标签至少包含 Git SHA，不只使用环境名。

## 6. Kubernetes 资源

每个服务至少包含：

- `Deployment`：副本、滚动策略、探针、资源和安全上下文。
- `Service`：集群内部发现。
- `ConfigMap`：非敏感配置引用。
- `Secret`：敏感配置引用，不在清单明文提交。
- 非本地环境为每个服务挂载 HTTPS 证书、私钥和受信 CA；服务间 Feign、Gateway 下游和内部 Token 端点校验证书，不允许跳过主机名/证书校验。证书签发来源由平台方确认，但明文回退不是默认方案。
- Prometheus 抓取配置或标准抓取注解；不要求额外引入 Prometheus Operator。

Gateway 额外通过 Ingress 暴露。MySQL、RabbitMQ、Elasticsearch、MinIO 等有状态组件采用已有运维方案或受控部署，不与普通无状态服务按同一方式滚动。

### 探针

- `startupProbe`：允许冷启动和配置加载。
- `livenessProbe`：只判断进程是否存活。
- `readinessProbe`：判断是否可以接受新请求。

### 资源与伸缩

- 初始 request/limit 通过本地和 staging 压测确定。
- HPA 仅在指标稳定后启用，不能用副本数掩盖数据库瓶颈。
- 关键服务生产至少两个副本的要求需结合可用性目标确认。
- PodDisruptionBudget 和拓扑分散在生产可用性目标确定后配置。

## 7. Nacos

- 服务名固定为 `educloud-<domain>`。
- 配置 Data ID、Group 和 namespace 命名统一，不复用生产 namespace。
- 配置订阅失败、注册失败和实例下线产生指标与告警。
- 动态配置只用于已证明可以安全热更新的字段。
- JWT 私钥、数据库密码等不存入普通 Nacos 文本配置。

## 8. 数据库发布

### 8.1 顺序

1. 备份并验证恢复入口。
2. 运行向后兼容的扩展迁移。
3. 验证结构、索引和基础数据。
4. 发布兼容新旧结构的服务。
5. 执行分批数据回填并监控。
6. 切换读取或写入逻辑。
7. 经过至少一个稳定版本后收缩旧字段。

迁移由发布前独立 Job/脚本执行：读取 `schema_migration_history`、校验 SHA-256、获取 MySQL 命名锁、按版本执行并记录成功/失败。应用副本只校验所需版本，不自行执行 DDL。checksum 改变、存在 FAILED 记录或无法取得锁时发布停止。

### 8.2 禁止事项

- 在应用启动时由多个副本并发执行不可控 DDL。
- 在未评估的生产大表上直接阻塞式增加索引。
- 同一次发布中直接重命名/删除仍被旧版本使用的字段。
- 假设所有 DDL 都能通过简单 downgrade 无损恢复。

## 9. CI 流水线

### 后端

```text
检出 → Java 17/Maven 缓存 → 格式与静态检查 → 单元测试
→ 集成/契约测试 → mvn -f educloud-backend/pom.xml verify → 镜像构建 → 镜像检查 → 发布制品
```

### 前端

学生端：

```powershell
pnpm install --frozen-lockfile
pnpm run typecheck
pnpm run build
```

教师端、管理端分别执行：

```powershell
npm ci
npm run typecheck
npm run build
```

E2E 工程使用 Playwright 1.47，在三端构建和后端集成环境就绪后运行 `npm ci` 与 `npx playwright test`。

当前未固定 Node.js 版本，CI 建设时必须从现有应用兼容性验证后固定，不能在文档中臆测。

### 制品

- Maven 包和容器镜像由 Git SHA、语义版本和构建时间标识。
- 同一镜像从测试晋级生产，不在生产阶段重新构建。
- Harbor 和 GitHub Actions 可沿用既有规划。
- ArgoCD/GitOps 仅在真正配置并验证后标记为已实现。

## 10. 发布策略

### 发布前

- 变更、迁移、配置、权限、事件兼容和回滚方案评审。
- `mvn -f educloud-backend/pom.xml verify`、三端相关 `typecheck/build` 和迁移验证通过。
- 确认外部支付、邮件、对象存储使用正确环境。
- 记录当前指标基线和待观察告警。

### 发布中

- 先发布向后兼容的数据库变更，再发布应用。
- Gateway、消费者和生产者的事件版本按兼容顺序部署。
- 观察就绪、错误率、延迟、队列积压和业务成功率。
- 新实例未就绪时不接流量。

### 回滚

- 应用回滚使用上一已验证镜像。
- 配置回滚恢复已记录版本。
- 数据库按迁移性质采用兼容继续运行、补偿或备份恢复，不盲目反向 DDL。
- 支付、订单等外部事实不能通过回滚应用删除，必须对账。

## 11. 备份与恢复

### MySQL

- 定期全量备份并保留增量恢复能力。
- 备份覆盖 11 个逻辑数据库、账号权限和必要配置说明。
- 恢复演练验证数据完整性、服务账号和事件水位。

### MinIO

- 同时保护对象和 `file_object/file_binding` 元数据。
- 恢复后抽样校验对象哈希、权限和业务引用。

### Elasticsearch

- 可以使用快照加速恢复，但权威恢复路径是从 Course/Content 数据和事件重建。

### Redis 与 RabbitMQ

- Redis 不是核心事实唯一来源；恢复后重建缓存和会话策略需明确。
- RabbitMQ 持久化和队列定义需要备份/声明；恢复后核对 Outbox、Inbox 和未确认消息。

RTO、RPO 和备份保留时间为 `【待决策】`，没有恢复演练证据前不能声称达标。

## 12. 日常运维

- 每日关注服务健康、5xx、支付异常、队列积压、死信、磁盘和备份结果。
- 每周核对慢查询、对账差异、索引失败、通知失败和容量趋势。
- 每次发布后检查关键业务合成用例。
- 密钥按策略轮换，轮换过程支持新旧密钥短期并存。
- 账号、权限和配置变更定期审计。
- 定时任务具有唯一执行或幂等设计，多副本不能重复产生副作用。

## 13. 运维手册最小模板

每个告警或故障 Runbook 包含：

1. 告警含义和影响范围。
2. 仪表盘、日志和追踪入口。
3. 安全的只读诊断步骤。
4. 可逆缓解动作及权限要求。
5. 补偿、重放或恢复步骤。
6. 验证业务一致性的命令或查询。
7. 升级联系人和停止条件。

不得把“重启服务”写成所有问题的默认解决方案。
