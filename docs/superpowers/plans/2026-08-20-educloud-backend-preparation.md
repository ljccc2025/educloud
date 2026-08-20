# EduCloud 后端准备阶段实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在不触碰当前主检出中前端未提交修改、也不提前生成 M01～M13 业务代码的前提下，建立可重复验证的后端隔离工作区、执行契约、父 Maven 基线、Rocky Linux 8.9 环境检查和本地依赖编排基线。

**架构：** 准备阶段只提供“开始 M01 所需的跑道”：父 POM 不声明尚未实现的服务模块，Docker Compose 只承载共享依赖，11 个业务数据库按独立账号初始化。所有 Linux 操作由 Bash 脚本和 Rocky Linux 运行手册描述；Windows 当前机器只能做静态与构建验证，不能替代 Rocky Linux 8.9 和真实 Docker 运行证据。

**技术栈：** Git worktree、JDK 17 或 21、Java 17 字节码目标、Maven 3.9+、Rocky Linux 8.9、Bash、Docker Engine、Docker Compose 插件（`docker compose`，主版本 2+）、MySQL 8.0.36、Redis 7.2、RabbitMQ 3.13、Nacos 2.3.2、MinIO、Elasticsearch 8.14、Zipkin、Prometheus、Grafana。

**执行状态（2026-08-20）：** 本地构建、脚本测试、契约测试和范围审查已通过；目标 Rocky Linux 8.9 前置检查、Java 21 Maven 构建、Compose `config`、九个容器 `up --wait`、HTTP/Redis/RabbitMQ 探针及 MySQL 数据库和账号验证均已通过。共享基础设施保持运行以供 M01 使用，停止环境的 `docker compose down` 尚未执行。

---

## 范围边界

- 准备阶段不创建 `educloud-common`、`educloud-gateway` 或任何业务服务的启动类、Controller、Entity、Mapper 和占位接口。
- 准备阶段不把前端 Mock、通知本地数据、支付沙箱、媒体直播或 AI 描述为真实后端能力。
- `M01` 从 `educloud-common` 开始；只有本计划全部完成并通过可执行门禁后才允许进入。
- 旧路线图 Task 1 中“一次创建全部空应用”的步骤由本计划覆盖；服务模块改为 M01～M13 按顺序增量加入父 POM。

## 文件结构

- `docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md`：锁定准备阶段边界、M01～M13 顺序和逐模块门禁。
- `docs/superpowers/specs/2026-08-18-educloud-backend-index.md`：把新执行规格和准备计划加入文档地图。
- `educloud-backend/pom.xml`：只定义父工程坐标、Java 17 编译目标、依赖版本和构建插件；准备阶段不声明子模块。
- `educloud-backend/README.md`：记录当前完成度、模块增量加入规则和验证命令。
- `deploy/scripts/check-prerequisites.sh`：在 Rocky Linux 8.9 上检查 OS、Java、Maven、Git、Docker 和 Compose。
- `deploy/scripts/generate-local-env.sh`：在目标机本地生成不进入 Git 的随机 Compose 凭据。
- `deploy/tests/check-prerequisites-tests.sh`：使用临时命令桩验证成功、错误 Java、缺失 Docker 和错误系统版本。
- `deploy/runbooks/rocky-linux-8.9-bootstrap.md`：给出从干净 Rocky Linux 8.9 安装依赖并运行检查的 Bash 流程。
- `deploy/docker-compose/compose.yml`：共享基础设施编排，不包含后端业务容器。
- `deploy/docker-compose/.env.example`：仅保存开发示例值和必须更换的本地凭据，不保存真实 Secret。
- `deploy/docker-compose/mysql/init/001-create-databases.sh`：创建 11 个逻辑数据库及各自最小权限账号。
- `deploy/tests/compose-contract-tests.sh`：验证 Compose 服务集合、固定版本、健康检查、卷和数据库初始化挂载。

### 任务 1：锁定准备阶段和 M01～M13 执行契约

**文件：**

- 创建：`docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md`
- 修改：`docs/superpowers/specs/2026-08-18-educloud-backend-index.md`

- [x] **步骤 1：写入明确边界和顺序**

新规格必须完整列出：

```text
准备阶段：隔离、父 POM、Rocky 前置检查、共享依赖 Compose
M01 educloud-common
M02 educloud-gateway
M03 educloud-user
M04 educloud-file
M05 educloud-course
M06 educloud-content
M07 educloud-order
M08 educloud-payment
M09 educloud-notification
M10 educloud-live
M11 educloud-search
M12 educloud-analytics
M13 educloud-recommendation
生产就绪：可观测性、完整 Compose、Kubernetes/Helm、安全、性能、恢复
```

同时写明：Gateway 在 M02 只完成安全入口基线，M03 完成后再次联调真实登录；M06 内部按章节课件、学习进度、作业、考试、社区顺序交付。

- [x] **步骤 2：更新总索引**

在文档地图中新增“模块执行顺序与准备门禁”和“后端准备阶段实现计划”，并说明 2026-08-20 的执行顺序覆盖旧路线图的批量空服务步骤，但不覆盖领域、安全和数据契约。

- [x] **步骤 3：验证文档契约**

运行：

```powershell
$spec = Get-Content -Raw docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md
1..13 | ForEach-Object {
  $id = 'M{0:D2}' -f $_
  if ($spec -notmatch $id) { throw "missing $id" }
}
if ($spec -notmatch '不提前创建') { throw 'missing no-placeholder boundary' }
git diff --check
```

预期：13 个编号均存在，边界声明存在，`git diff --check` 无输出并返回 0。

- [x] **步骤 4：提交**

```bash
git add -- docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md docs/superpowers/specs/2026-08-18-educloud-backend-index.md
git commit -m "docs(backend): lock module execution order"
```

### 任务 2：建立无业务模块的父 Maven 基线

**文件：**

- 创建：`educloud-backend/pom.xml`
- 创建：`educloud-backend/README.md`

- [x] **步骤 1：运行红灯命令**

运行：

```bash
mvn -f educloud-backend/pom.xml help:effective-pom
```

预期：失败，原因是 `educloud-backend/pom.xml` 不存在。

- [x] **步骤 2：创建最小父 POM**

父 POM必须包含以下不可变约束：

```xml
<groupId>com.educloud</groupId>
<artifactId>educloud-backend</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>
<properties>
  <java.version>17</java.version>
  <maven.compiler.release>17</maven.compiler.release>
  <spring-boot.version>3.2.5</spring-boot.version>
  <spring-cloud.version>2023.0.3</spring-cloud.version>
  <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
</properties>
```

使用 Spring Boot、Spring Cloud 和 Spring Cloud Alibaba BOM；启用 Maven Enforcer，要求 Maven 3.9+、构建 JDK 为 17 或 21；配置 Compiler Plugin 使用 `release=17`、Surefire 使用 JUnit Platform。准备阶段不得出现 `<modules>` 或任何服务 POM。

- [x] **步骤 3：创建 README**

README 明确写出当前状态为“准备阶段”，记录 M01～M13 的增量加入规则，并提供：

```bash
mvn -f educloud-backend/pom.xml help:effective-pom
mvn -f educloud-backend/pom.xml verify
```

- [x] **步骤 4：运行绿灯验证**

运行：

```bash
mvn -f educloud-backend/pom.xml help:effective-pom
mvn -f educloud-backend/pom.xml verify
```

预期：两个命令返回 0；输出只包含父项目，不包含尚未实现的服务模块。

- [x] **步骤 5：提交**

```bash
git add -- educloud-backend/pom.xml educloud-backend/README.md
git commit -m "chore(backend): establish parent build"
```

### 任务 3：增加 Rocky Linux 8.9 前置检查和安装手册

**文件：**

- 创建：`deploy/tests/check-prerequisites-tests.sh`
- 创建：`deploy/scripts/check-prerequisites.sh`
- 创建：`deploy/runbooks/rocky-linux-8.9-bootstrap.md`

- [x] **步骤 1：编写失败测试**

测试使用 `mktemp -d` 建立 Java、Maven、Git、Docker 命令桩，并通过 `EDUCLOUD_OS_RELEASE_FILE` 注入系统信息，至少覆盖：

```text
Rocky Linux 8.9 + Java 17 或 21 + Maven 3.9 + Docker + Compose 插件 2+ => 退出 0
Java 18 => 退出非 0，并输出 "Java 17 or 21 is required"
缺少 docker => 退出非 0，并输出 "docker command not found"
Rocky Linux 9 => 退出非 0，并输出 "Rocky Linux 8.9 is required"
```

运行：

```bash
bash deploy/tests/check-prerequisites-tests.sh
```

预期：失败，原因是被测脚本尚不存在。

- [x] **步骤 2：实现检查脚本**

脚本使用 `set -euo pipefail`，依次检查：

```text
/etc/os-release 的 ID=rocky 且 VERSION_ID=8.9
java 主版本等于 17 或 21
Maven 主版本至少为 3，次版本至少为 9
git 可执行
docker 可执行且 daemon 可连接
docker compose version 可执行且主版本至少为 2
```

允许测试通过 `EDUCLOUD_OS_RELEASE_FILE` 替换 `/etc/os-release`、通过 `EDUCLOUD_SKIP_DOCKER_DAEMON=1` 跳过 daemon 探测；生产运行手册不得设置跳过变量。

- [x] **步骤 3：运行测试验证通过**

运行：

```bash
bash deploy/tests/check-prerequisites-tests.sh
```

预期：4 个场景全部通过，退出 0。

- [x] **步骤 4：编写 Rocky Linux 运行手册**

只引用 Rocky Linux、Spring Boot、Docker 和 Maven 官方来源；手册依次给出：系统确认、Java 17/21、Maven 3.9、Docker 官方仓库、`docker compose` 插件、当前用户 Docker 权限、服务启动、版本检查和前置脚本运行命令。所有命令使用 Bash，不包含 PowerShell。

- [x] **步骤 5：提交**

```bash
git add -- deploy/scripts/check-prerequisites.sh deploy/tests/check-prerequisites-tests.sh deploy/runbooks/rocky-linux-8.9-bootstrap.md
git commit -m "chore(ops): add rocky linux preflight"
```

### 任务 4：建立共享依赖 Compose 基线

**文件：**

- 创建：`deploy/docker-compose/compose.yml`
- 创建：`deploy/docker-compose/.env.example`
- 创建：`deploy/docker-compose/mysql/init/001-create-databases.sh`
- 创建：`deploy/tests/compose-contract-tests.sh`

- [x] **步骤 1：编写失败契约测试**

测试必须断言 Compose 文本包含且仅在共享依赖层出现以下服务：

```text
mysql redis rabbitmq nacos minio elasticsearch zipkin prometheus grafana
```

同时断言 MySQL、Redis、RabbitMQ、Nacos、MinIO、Elasticsearch 有健康检查，持久化组件有命名卷，业务服务名和 `educloud-*-service` 不存在。

运行：

```bash
bash deploy/tests/compose-contract-tests.sh
```

预期：失败，原因是 `compose.yml` 尚不存在。

- [x] **步骤 2：创建环境变量示例和数据库初始化**

`.env.example` 提供本地开发示例值，至少包含 MySQL root 密码、11 个服务账号密码、RabbitMQ 管理账号、MinIO 管理账号和 Nacos 鉴权值；文件顶部注明不得用于共享或生产环境。

初始化脚本使用固定数据库与账号映射：

```text
educloud_user/user_app
educloud_course/course_app
educloud_content/content_app
educloud_order/order_app
educloud_payment/payment_app
educloud_live/live_app
educloud_file/file_app
educloud_notification/notification_app
educloud_analytics/analytics_app
educloud_search/search_app
educloud_recommendation/recommendation_app
```

每个账号只获得自己数据库的权限，不授予 `*.*`。

- [x] **步骤 3：创建 Compose 配置**

使用路线图锁定的主版本或精确版本；所有端口通过环境变量映射，所有持久化数据使用项目命名卷，所有容器加入 `educloud-infra` 网络。Compose 不包含后端业务镜像，也不声明功能已经可用。

- [x] **步骤 4：运行静态和 Compose 验证**

运行：

```bash
bash deploy/tests/compose-contract-tests.sh
docker compose --env-file deploy/docker-compose/.env.example -f deploy/docker-compose/compose.yml config
```

预期：契约测试通过；具备 `docker compose` 插件（主版本 2+）的环境中 `config` 返回 0。当前机器没有 Docker CLI 时，只记录第二条未验证，不以文本检查替代。

状态：契约测试已通过；目标 Rocky Linux 8.9 主机上的 `docker compose config --quiet` 已返回成功。

- [x] **步骤 5：提交**

```bash
git add -- deploy/docker-compose/compose.yml deploy/docker-compose/.env.example deploy/docker-compose/mysql/init/001-create-databases.sh deploy/tests/compose-contract-tests.sh
git commit -m "chore(deploy): add shared infrastructure baseline"
```

### 任务 5：准备阶段总体验证和 M01 入口门禁

**文件：**

- 不创建新的生产文件；只验证前四项提交。

- [x] **步骤 1：验证工作区和提交边界**

运行：

```bash
git status --short
git log --oneline --max-count=5
git diff HEAD~4..HEAD --name-only
```

预期：工作区干净；变更仅位于本计划列出的后端文档、`educloud-backend` 和 `deploy` 路径，没有任何 `educloud-frontend` 变更。

- [x] **步骤 2：运行可在当前环境执行的验证**

运行：

```bash
mvn -f educloud-backend/pom.xml verify
bash deploy/tests/check-prerequisites-tests.sh
bash deploy/tests/compose-contract-tests.sh
git diff --check HEAD~4..HEAD
```

预期：全部返回 0。

- [ ] **步骤 3：运行 Rocky Linux 8.9 验证**

在目标机运行：

```bash
bash deploy/scripts/check-prerequisites.sh
docker compose --env-file deploy/docker-compose/.env.example -f deploy/docker-compose/compose.yml config
docker compose --env-file deploy/docker-compose/.env.example -f deploy/docker-compose/compose.yml up -d --wait
docker compose --env-file deploy/docker-compose/.env.example -f deploy/docker-compose/compose.yml ps
docker compose --env-file deploy/docker-compose/.env.example -f deploy/docker-compose/compose.yml down
```

预期：前置检查通过，所有共享依赖健康，`down` 不使用 `-v`。如果没有目标机访问能力，必须逐条标记为“未执行”，准备阶段不能宣称 Rocky/Docker 运行验证通过。

状态：目标机前置检查、`config`、`up -d --wait` 和 `ps` 已通过，九个容器均为 healthy。Nacos、MinIO、Elasticsearch、Zipkin、Prometheus、Grafana HTTP 探针通过，Redis 返回 `PONG`，RabbitMQ 返回 `Ping succeeded`；MySQL 验证为 11 个业务库、11 个应用账号、11 个迁移账号和 0 个应用账号库级授权。为保留 M01 运行环境，`down` 有意暂缓，因此本步骤保持未勾选。

- [x] **步骤 4：两阶段审查**

先审查是否完全符合准备范围和 M01～M13 边界，再审查 Maven、Bash、Compose、安全和文档质量；任何问题修复并重新验证后才允许进入 M01。

## 准备阶段完成定义

只有同时满足以下条件才允许开始 M01：

1. 隔离 worktree 和 `codex/educloud-backend-foundation` 分支存在，原主检出修改未被触碰。
2. 执行规格锁定 M01～M13，旧的批量空服务步骤被明确覆盖。
3. 父 Maven 构建通过，但仓库中仍没有服务业务代码。
4. Rocky 前置脚本测试通过；目标 Rocky 运行结果已通过，或被诚实标记为等待用户执行。
5. Compose 契约和 `config` 通过；真实容器健康检查已通过，或被诚实标记为等待目标环境。
6. 规格合规性审查与代码质量审查均无未解决问题。
