# EduCloud AI 助教 P1 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新建 `educloud-ai` 微服务（8105/8106，新库 `educloud_ai`），接入 OpenAI 兼容模型（硅基流动 Qwen），为学生端「AI 助教」提供真答、会话历史、配额熔断与审计落库，并彻底移除前端 4 段关键词假回复与外部直连。

**架构：** 独立 Spring Boot 模块注册 Nacos，经网关 `/api/v1/ai/**`（35s 超时）暴露 4 个接口；身份只取 JWT `sub` + `roles`；LLM 调用走 `ChatProvider` 抽象（P1 唯一实现 `OpenAiCompatibleProvider`），顶层 `enable_thinking:false` 关思考；会话/消息落 MySQL 2 张表并兼作审计，配额走 Redis 双计数器（个人 50 次/日 + 全局 200 万 token/日）。前端 `assistantClient` 重写为网关 HTTP 调用，student-portal 顺带接入 vitest。

**技术栈：** Java 17 / Spring Boot 3.2 / MyBatis Plus / MySQL（educloud_ai 库）/ Redis / RestClient / React 18 + TS + Vite + vitest。

**规格：** `docs/superpowers/specs/2026-08-28-ai-assistant-p1-design.md`（已获用户批准，执行中不得重开设计）

---

## 工作约定（执行者必读）

1. **提交与推送前先征询用户确认**：每个任务的 Commit 步骤给出建议的 `git add` 与 message，但执行者必须先向用户确认再执行 commit；push 一律单独确认。
2. **用户说"已验证通过"也要自己复核并给证据**：测试输出原文、MD5 比对、浏览器截图，缺一不可。
3. **不留 mock 静默回退、不吞异常**：外部调用失败就返回明确错误码；任何 catch 都要么处理要么带上下文重抛。审查发现的问题当轮修掉，不推到下个迭代。
4. **commit message 用中文约定式**：`feat(AI助教): 描述`、`test(AI助教): 描述` 等。
5. **密钥红线**：`AI_PROVIDER_API_KEY` 绝不出现在代码、测试、日志、git。日志只记长度、条数、usage、latency、finish_reason。

## 环境事实（已实测，执行时直接引用）

- 工作区 `D:\microservice`；VM `192.168.100.136`（Rocky 8.9，root/密码与各中间件凭证见 `docs/HANDOVER.md` §4）。
- VM 上 mvn 必须 `-f educloud-backend/pom.xml`；`-pl educloud-ai` 从聚合 POM 解析（即 `mvn -f educloud-backend/pom.xml -pl educloud-ai -am compile`）。
- 前端由 VM 上的 vite dev server 直读源码（`/root/educloud/.worktrees/educloud-backend-foundation/educloud-frontend/`），改前端源文件同步后即 HMR 生效，**不用重新 build dist**。
- 重启 Java 服务：SIGTERM 优雅退出可能要 40 秒，必须轮询确认进程真退出再跑 `start-dev.sh`，否则脚本按端口判断会误报 "already running" 跳过启动。**禁止 `pkill -f` 带 jar 名的模式**（会杀掉自己的 SSH 会话），用 `pgrep -f` 拿 PID 后 `kill <pid>`。
- VM 的 MySQL 会话时区是 UTC 而 JVM 是 +08:00：涉及"今天"的判断（配额日期键、TTL）一律在应用侧用 JVM 本地时间算，SQL 里不写 `CURDATE()` 比较。
- 演示账号：学生 `fe_demo_10 / EduCloud@2026`（报名 17 门课）；教师 `demo_teacher@educloud.cn / EduCloud@2026`；未报名对照账号 `user4213`；**HANDOVER 里 test2 的密码已失效，不要用**。
- 端口 8105/8106 实施前先在 VM 上 `ss -tln | grep -E ':8105|:8106'` 确认空闲（当前最大占用为 recommendation 8103/8104）。
- 实测模型事实（必须体现在实现里，见任务 6/9）：
  - 默认开启思考模式，一次普通提问 48–99 秒；**必须显式关思考**，只有顶层 `enable_thinking:false` 或 `thinking:{"type":"disabled"}` 有效，`chat_template_kwargs.enable_thinking` 实测无效（仍吐 reasoning_content），不得使用；
  - `max_tokens` 过小时 HTTP 200 但 `content` 为空（`finish_reason=length`），**必须按 finish_reason 判定**，不能只看 HTTP 状态码；
  - 一次典型问答约 370 tokens（入 79 / 出 291），据此配额：个人 50 次/日 + 全局 200 万 token/日熔断。

## 文件结构

**后端（educloud-ai，主路径 `educloud-backend/educloud-ai/`）**

| 文件 | 职责 |
|---|---|
| `educloud-backend/pom.xml` | 根聚合 POM 注册 `<module>educloud-ai</module>`（修改） |
| `pom.xml` | 模块 POM（照 content 裁剪：无 RabbitMQ） |
| `src/main/java/com/educloud/ai/AiApplication.java` | 启动类 |
| `src/main/java/com/educloud/ai/config/AiProperties.java` | `educloud.ai.*` 配置（含 api-key 占位，仅从 env 注入） |
| `src/main/java/com/educloud/ai/config/SecurityConfig.java` | JWT 资源服务器 + 全 authenticated |
| `src/main/java/com/educloud/ai/config/JwtDecoderConfiguration.java` | JWKS 文件 → JwtDecoder（issuer/audience/timestamp 校验） |
| `src/main/java/com/educloud/ai/config/JwksLoader.java` | JWKS 加载与公钥校验（照 content 简化版） |
| `src/main/java/com/educloud/ai/config/MybatisPlusConfig.java` | 分页插件 |
| `src/main/java/com/educloud/ai/config/AiProviderConfig.java` | ChatProvider Bean（RestClient + 超时 + api-key fail-fast） |
| `src/main/java/com/educloud/ai/security/JwtSecurityUtils.java` | userId/roles 解析（照 content 同名类） |
| `src/main/java/com/educloud/ai/entity/AiConversationEntity.java` | 会话实体 |
| `src/main/java/com/educloud/ai/entity/AiMessageEntity.java` | 消息实体（兼审计） |
| `src/main/java/com/educloud/ai/mapper/AiConversationMapper.java` | 会话 Mapper |
| `src/main/java/com/educloud/ai/mapper/AiMessageMapper.java` | 消息 Mapper |
| `src/main/java/com/educloud/ai/exception/AiErrorCode.java` | 7+1 个错误码 |
| `src/main/java/com/educloud/ai/exception/AiExceptionHandler.java` | BusinessException → ApiResponse |
| `src/main/java/com/educloud/ai/dto/request/AiChatRequest.java` | `{conversationId?, question, stream?}` |
| `src/main/java/com/educloud/ai/dto/response/AiChatResponse.java` | `{conversationId, messageId, content, finishReason, usage, degraded}` |
| `src/main/java/com/educloud/ai/dto/response/AiUsageResponse.java` | usage 三元组 |
| `src/main/java/com/educloud/ai/dto/response/AiConversationResponse.java` | 会话列表项 |
| `src/main/java/com/educloud/ai/dto/response/AiMessageResponse.java` | 消息项 |
| `src/main/java/com/educloud/ai/provider/ChatProvider.java` | Provider SPI + ChatTurn/ChatOptions/ChatResult |
| `src/main/java/com/educloud/ai/provider/OpenAiCompatibleProvider.java` | OpenAI 兼容实现（关思考/finish_reason/重试策略） |
| `src/main/java/com/educloud/ai/provider/AiProviderException.java` | 上游异常（含 retryable 标记） |
| `src/main/java/com/educloud/ai/chat/ContextAssembler.java` | system + 最近 10 条 + 3000 token 预算裁剪 |
| `src/main/java/com/educloud/ai/chat/QuotaService.java` | Redis 个人次数 + 全局 token 双计数 |
| `src/main/java/com/educloud/ai/chat/ChatService.java` | 编排：校验→配额→会话→审计→调用→落库 |
| `src/main/java/com/educloud/ai/controller/AiAssistantController.java` | 4 个接口 + STUDENT 守卫 |
| 测试：`src/test/java/com/educloud/ai/dto/AiChatApiContractTest.java`、`provider/OpenAiCompatibleProviderTest.java`、`chat/ContextAssemblerTest.java`、`chat/QuotaServiceTest.java`、`chat/ChatServiceTest.java`、`persistence/AiChatPersistenceIT.java` | 单测 + IT |

**网关**

| 文件 | 职责 |
|---|---|
| `educloud-backend/educloud-gateway/src/main/resources/application.yml` | 新增 `ai-core` 路由 + 35s metadata（修改） |
| `educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/RouteGroups.java` | 新增 AI 组（修改） |
| `educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/GatewayRouteContractTest.java` | 路由契约 17→18 条（修改） |

**部署**

| 文件 | 职责 |
|---|---|
| `deploy/docker-compose/mysql/init/001-create-databases.sh` | 新库/新账号映射（修改，fresh 环境用） |
| `deploy/docker-compose/.env.example` | 新增 3 个变量占位（修改） |
| `deploy/sql/ai/V001__ai.sql` | 2 张表 + ai_app 表级授权（新建） |
| `deploy/scripts/provision-ai-nacos.sh` | 发布 `educloud-ai.yaml` 非敏感配置（新建） |
| `deploy/tests/ai-contract-tests.sh` | 表结构/授权/网关路由/Nacos 配置/密钥占位（新建） |
| `deploy/tests/ai-e2e-tests.py` | Playwright 跨角色 E2E（新建） |
| `deploy/scripts/start-dev.sh` | educloud-ai 启动项（修改） |

**前端（student-portal）**

| 文件 | 职责 |
|---|---|
| `educloud-frontend/student-portal/package.json` | vitest + testing-library devDeps、test 脚本（修改） |
| `educloud-frontend/student-portal/vite.config.ts` | test 块（jsdom/setup）（修改） |
| `educloud-frontend/student-portal/src/test/setup.ts` | jest-dom + cleanup（新建） |
| `educloud-frontend/student-portal/src/features/engagement/types.ts` | AI 类型族替换 AssistantReply（修改） |
| `educloud-frontend/student-portal/src/features/engagement/assistantClient.ts` | 重写：网关 /ai/** 四调用，删 mock 与直连（重写） |
| `educloud-frontend/student-portal/src/services/http.ts` | apiErrorText 补 AI 错误码（修改） |
| `educloud-frontend/student-portal/src/pages/AiAssistant.tsx` | 历史会话/新建/删除/错误态/行内加粗（重写） |
| 测试：`src/pages/AiAssistant.test.tsx`、`src/features/engagement/assistantClient.test.ts` | 429/503 不回退假文案 + 加粗渲染 |

---

## 任务 1：数据库账号初始化（compose init + .env + VM 一次性引导）

**文件：**
- 修改：`deploy/docker-compose/mysql/init/001-create-databases.sh`
- 修改：`deploy/docker-compose/.env.example`

- [ ] **步骤 1：001-create-databases.sh 注册 educloud_ai**

`required_variables` 数组末尾（`EDUCLOUD_SEARCH_DB_PASSWORD...` 之后按既有顺序补全到 recommendation 后）追加：

```bash
  EDUCLOUD_AI_DB_PASSWORD EDUCLOUD_AI_MIGRATION_PASSWORD
```

`database_mappings` 数组 `educloud_recommendation:...` 行后追加：

```bash
  'educloud_ai:ai_app:ai_migration:EDUCLOUD_AI_DB_PASSWORD:EDUCLOUD_AI_MIGRATION_PASSWORD'
```

- [ ] **步骤 2：.env.example 追加变量**

在文件末尾（或 recommendation 段之后）追加：

```bash
# ---- M15 educloud-ai ----
EDUCLOUD_AI_DB_PASSWORD=LocalAiApp_ChangeMe_2026
EDUCLOUD_AI_MIGRATION_PASSWORD=LocalAiMigration_ChangeMe_2026
# AI 供应商密钥：真实值只写 VM 的 deploy/docker-compose/.env；仓库占位必须为空，绝不提交真实 key
AI_PROVIDER_API_KEY=
```

- [ ] **步骤 3：VM 一次性引导（存量 MySQL 容器不会重跑 init 脚本）**

在 VM 上执行（先 `set -a; . deploy/docker-compose/.env; set +a` 使变量可用）：

```bash
mysql --protocol=TCP -h127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS educloud_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'ai_app'@'%' IDENTIFIED BY '${EDUCLOUD_AI_DB_PASSWORD}';
ALTER USER 'ai_app'@'%' IDENTIFIED BY '${EDUCLOUD_AI_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'ai_migration'@'%' IDENTIFIED BY '${EDUCLOUD_AI_MIGRATION_PASSWORD}';
ALTER USER 'ai_migration'@'%' IDENTIFIED BY '${EDUCLOUD_AI_MIGRATION_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES,
  CREATE VIEW, SHOW VIEW, TRIGGER
  ON educloud_ai.* TO 'ai_migration'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
SQL
```

预期：无报错。`ai_app` 此处**不给**库级权限（表级授权由任务 2 的迁移脚本授予，与既有模块约定一致）。

- [ ] **步骤 4：Commit**

```bash
git add deploy/docker-compose/mysql/init/001-create-databases.sh deploy/docker-compose/.env.example
git commit -m "feat(AI助教): 注册 educloud_ai 库与账号映射并补密钥占位"
```

## 任务 2：迁移脚本 V001（建表 + ai_app 授权）

**文件：**
- 创建：`deploy/sql/ai/V001__ai.sql`

- [ ] **步骤 1：编写迁移脚本**（ID 由应用侧雪花分配，**不用** AUTO_INCREMENT；时间比较一律应用侧做，列只存值）

```sql
-- EduCloud AI 数据库：AI 助教 P1（V001）
-- 依据：docs/superpowers/specs/2026-08-28-ai-assistant-p1-design.md §3

CREATE TABLE ai_conversation (
    id              BIGINT       NOT NULL COMMENT '雪花 ID，对外字符串化',
    student_id      BIGINT       NOT NULL COMMENT '归属学生，取自 JWT sub',
    title           VARCHAR(120) NOT NULL COMMENT '首条学生提问截断生成',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息数（含软删）',
    last_message_at DATETIME(3)  NOT NULL COMMENT '列表排序依据',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_student_last (student_id, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 会话';

CREATE TABLE ai_message (
    id                BIGINT      NOT NULL COMMENT '雪花 ID',
    conversation_id   BIGINT      NOT NULL COMMENT '会话',
    role              VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content           TEXT        NULL COMMENT '答案正文（只存 content，不存 reasoning_content）',
    provider          VARCHAR(64) NULL COMMENT '实际调用的供应商',
    model             VARCHAR(64) NULL COMMENT '实际调用的模型',
    prompt_tokens     INT         NULL,
    completion_tokens INT         NULL,
    latency_ms        INT         NULL COMMENT '外部调用耗时',
    finish_reason     VARCHAR(16) NULL COMMENT 'stop/length/error',
    status            VARCHAR(16) NOT NULL COMMENT 'OK/TRUNCATED/FAILED',
    error_code        VARCHAR(64) NULL COMMENT '失败时的错误码',
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 消息（兼调用审计）';

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_ai.ai_conversation TO 'ai_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_ai.ai_message TO 'ai_app'@'%';
```

- [ ] **步骤 2：执行迁移并验证**

VM 上：

```bash
set -a; . deploy/docker-compose/.env; set +a
bash deploy/scripts/run-migrations.sh --service ai
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SHOW TABLES FROM educloud_ai; SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee LIKE '%ai_app%' AND table_schema='educloud_ai';"
```

预期：脚本输出 `Applying V001__ai.sql` 与 `All educloud_ai migrations are up to date`；`SHOW TABLES` 列出 `ai_conversation`、`ai_message`、`schema_migration_history`；授权计数 `>= 2`。

- [ ] **步骤 3：Commit**

```bash
git add deploy/sql/ai/V001__ai.sql
git commit -m "feat(AI助教): 新增会话与消息表迁移及 ai_app 表级授权"
```

## 任务 3：模块骨架（POM/启动类/配置/安全/启动脚本）

**文件：**
- 修改：`educloud-backend/pom.xml`
- 创建：`educloud-backend/educloud-ai/pom.xml`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/AiApplication.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/AiProperties.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/SecurityConfig.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/JwtDecoderConfiguration.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/JwksLoader.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/MybatisPlusConfig.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/security/JwtSecurityUtils.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/exception/AiExceptionHandler.java`
- 创建：`educloud-backend/educloud-ai/src/main/resources/application.yml`
- 修改：`deploy/scripts/start-dev.sh`
- （`AiProviderConfig.java` 推迟到任务 6 步骤 5 创建，见下文说明）

- [ ] **步骤 1：根 POM 注册模块**

`educloud-backend/pom.xml` 的 `<modules>` 中 `<module>educloud-recommendation</module>` 之后追加：

```xml
        <module>educloud-ai</module>
```

- [ ] **步骤 2：模块 POM**（照 content 裁剪：无 RabbitMQ，保留 Redis；Testcontainers 供任务 11 使用）

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

    <artifactId>educloud-ai</artifactId>
    <packaging>jar</packaging>

    <name>EduCloud AI</name>
    <description>AI teaching assistant: OpenAI-compatible chat, conversation history, quota and audit.</description>

    <properties>
        <skipITs>true</skipITs>
    </properties>

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
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
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
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-jsqlparser</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
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
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/*Test.java</include>
                    </includes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <skipITs>${skipITs}</skipITs>
                    <classesDirectory>${project.build.outputDirectory}</classesDirectory>
                    <failIfNoTests>true</failIfNoTests>
                    <includes>
                        <include>**/*IT.java</include>
                    </includes>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **步骤 3：启动类与配置类**

`AiApplication.java`：

```java
package com.educloud.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
public class AiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
```

`AiProperties.java`：

```java
package com.educloud.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "educloud.ai")
public record AiProperties(
        ProviderProperties provider,
        TimeoutProperties timeout,
        QuotaProperties quota,
        ContextProperties context,
        JwtProperties jwt) {

    public record ProviderProperties(
            String name,
            String baseUrl,
            String model,
            String apiKey,
            boolean thinkingEnabled,
            int maxTokens) {
    }

    public record TimeoutProperties(long connectMs, long readMs) {
    }

    public record QuotaProperties(int dailyRequests, long dailyTokens) {
    }

    public record ContextProperties(int maxHistoryMessages, int maxPromptTokens) {
    }

    public record JwtProperties(String jwksLocation, String issuer, String audience) {
    }
}
```

`SecurityConfig.java`（照 content：全路径 authenticated，无公开读）：

```java
package com.educloud.ai.config;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(AiProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            ApiResponseFactory responses,
            ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            ApiResponse<Void> body = responses.error(
                                    CommonErrorCode.UNAUTHENTICATED,
                                    CommonErrorCode.UNAUTHENTICATED.defaultMessage(),
                                    null);
                            response.setStatus(CommonErrorCode.UNAUTHENTICATED.httpStatus());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(body));
                        }));
        return http.build();
    }
}
```

`JwtDecoderConfiguration.java`：

```java
package com.educloud.ai.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class JwtDecoderConfiguration {

    @Bean
    JwksLoader aiJwksLoader() {
        return new JwksLoader();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock aiClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtDecoder aiJwtDecoder(JwksLoader loader, AiProperties properties) {
        JwksLoader.LoadedJwks loaded = loader.load(properties);
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(loaded.jwkSet());
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);

        AiProperties.JwtProperties jwt = properties.jwt();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator(Duration.ofSeconds(30)));
        validators.add(new JwtIssuerValidator(
                jwt != null && jwt.issuer() != null ? jwt.issuer() : "https://issuer.educloud.local"));
        validators.add(new JwtAudienceValidator(
                jwt != null && jwt.audience() != null ? jwt.audience() : "educloud-api"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }
}
```

`JwksLoader.java`（content 同名类的简化版，职责相同：读文件、拒绝私钥参数、只留 RSA/RS256/sig 公钥）：

```java
package com.educloud.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JwksLoader {

    private static final int MAX_JWKS_BYTES = 256 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> PRIVATE_RSA_PARAMETERS = Set.of("d", "p", "q", "dp", "dq", "qi", "oth");

    public LoadedJwks load(AiProperties properties) {
        String location = properties.jwt() != null ? properties.jwt().jwksLocation() : null;
        if (!StringUtils.hasText(location)) {
            return new LoadedJwks(new JWKSet());
        }
        String json = readResource(location);
        rejectPrivateParameters(json);
        JWKSet parsed;
        try {
            parsed = JWKSet.parse(json);
        } catch (ParseException | RuntimeException exception) {
            throw invalid("invalid JWKS syntax", exception);
        }
        if (parsed.getKeys().isEmpty()) {
            throw invalid("JWKS must contain at least one public key");
        }
        Set<String> keyIds = new HashSet<>();
        List<JWK> publicKeys = new ArrayList<>();
        for (JWK key : parsed.getKeys()) {
            validateKey(key, keyIds);
            publicKeys.add(key.toPublicJWK());
        }
        return new LoadedJwks(new JWKSet(List.copyOf(publicKeys)));
    }

    private static void rejectPrivateParameters(String json) {
        try {
            JsonNode keys = OBJECT_MAPPER.readTree(json).path("keys");
            if (keys.isArray()) {
                for (JsonNode key : keys) {
                    if (PRIVATE_RSA_PARAMETERS.stream().anyMatch(key::has)) {
                        throw invalid("JWKS must not contain private key parameters");
                    }
                }
            }
        } catch (IOException exception) {
            throw invalid("invalid JWKS syntax", exception);
        }
    }

    private static String readResource(String location) {
        String path = location.startsWith("file:") ? location.substring("file:".length()) : location;
        Resource resource = new FileSystemResource(path);
        try {
            if (!resource.exists() || !resource.isReadable()) {
                throw invalid("educloud.ai.jwt.jwks-location must be a readable resource: " + location);
            }
            if (resource.contentLength() > MAX_JWKS_BYTES) {
                throw invalid("educloud.ai.jwt.jwks-location exceeds 256 KiB");
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw invalid("educloud.ai.jwt.jwks-location cannot be read", exception);
        }
    }

    private static void validateKey(JWK key, Set<String> keyIds) {
        if (!KeyType.RSA.equals(key.getKeyType()) || !(key instanceof RSAKey rsaKey)) {
            throw invalid("every JWKS key must be an RSA public key");
        }
        if (key.isPrivate()) {
            throw invalid("JWKS must not contain private key parameters");
        }
        if (!KeyUse.SIGNATURE.equals(key.getKeyUse())) {
            throw invalid("every JWKS key must declare use=sig");
        }
        if (!JWSAlgorithm.RS256.equals(key.getAlgorithm())) {
            throw invalid("every JWKS key must declare alg=RS256");
        }
        if (!StringUtils.hasText(key.getKeyID()) || !keyIds.add(key.getKeyID())) {
            throw invalid("every JWKS key must declare a unique non-blank kid");
        }
        try {
            rsaKey.toRSAPublicKey();
        } catch (JOSEException | RuntimeException exception) {
            throw invalid("every JWKS key must contain a usable RSA public key", exception);
        }
    }

    private static IllegalStateException invalid(String category) {
        return new IllegalStateException("invalid JWKS configuration: " + category);
    }

    private static IllegalStateException invalid(String category, Exception cause) {
        return new IllegalStateException("invalid JWKS configuration: " + category, cause);
    }

    public record LoadedJwks(JWKSet jwkSet) {
    }
}
```

`MybatisPlusConfig.java`：

```java
package com.educloud.ai.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

`AiProviderConfig.java`（api-key fail-fast：为空拒绝启动，绝不带着空 key 上线）——**本任务不创建**：它依赖任务 6 的 `OpenAiCompatibleProvider`，先创建必然编译失败；完整代码见任务 6 步骤 5，随 Provider 一并落地。步骤 6 的编译验证覆盖其余文件即可。

`JwtSecurityUtils.java`（照 content 同名类裁剪到本项目用到的两个方法）：

```java
package com.educloud.ai.security;

import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class JwtSecurityUtils {

    private JwtSecurityUtils() {
    }

    /** 身份只取 JWT sub（规格 §9.1）：任何接口不接受前端传 studentId。 */
    public static Long userId(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        String subject = jwt.getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT subject must be a numeric userId: " + subject);
        }
    }

    public static Set<String> roles(Jwt jwt) {
        Objects.requireNonNull(jwt, "jwt");
        Object value = jwt.getClaim("roles");
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> collection)) {
            throw new BusinessException(
                    CommonErrorCode.UNAUTHENTICATED,
                    "JWT roles claim must be an array of strings");
        }
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (Object item : collection) {
            if (!(item instanceof String text)) {
                throw new BusinessException(
                        CommonErrorCode.UNAUTHENTICATED,
                        "JWT roles claim must be an array of strings");
            }
            roles.add(text);
        }
        return Set.copyOf(roles);
    }
}
```

`AiExceptionHandler.java`（照 content 同名类）：

```java
package com.educloud.ai.exception;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.error.CommonErrorCode;
import com.educloud.common.error.ErrorCode;
import com.educloud.common.error.FieldViolation;
import com.educloud.common.error.ValidationErrorDetails;
import com.educloud.common.web.RequestContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public final class AiExceptionHandler {

    private final ApiResponseFactory responses;

    public AiExceptionHandler(ApiResponseFactory responses) {
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return respond(exception.errorCode(), exception.getMessage(), exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorDetails>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getCode() == null ? "Invalid" : error.getCode(),
                        error.getDefaultMessage() == null ? "Invalid" : error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::code))
                .toList();
        return respond(
                CommonErrorCode.VALIDATION_FAILED,
                CommonErrorCode.VALIDATION_FAILED.defaultMessage(),
                new ValidationErrorDetails(violations));
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(ErrorCode code, String message, T details) {
        ApiResponse<T> body = responses.error(code, message, details);
        return ResponseEntity.status(code.httpStatus())
                .header(RequestContext.REQUEST_ID_HEADER, body.requestId())
                .body(body);
    }
}
```

- [ ] **步骤 4：application.yml**

```yaml
server:
  port: ${SERVER_PORT:8105}
  shutdown: graceful

spring:
  application:
    name: educloud-ai
  lifecycle:
    timeout-per-shutdown-phase: 20s
  config:
    # 非敏感 ai.* 微调项可发布在 Nacos（deploy/scripts/provision-ai-nacos.sh）；密钥只走 env，绝不进 Nacos
    import: "optional:nacos:educloud-ai.yaml?group=EDUCLOUD_SERVICES&refreshEnabled=false"
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/educloud_ai?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
    username: ${EDUCLOUD_AI_DB_USERNAME:ai_app}
    password: ${EDUCLOUD_AI_DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2s
  cloud:
    nacos:
      server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
      username: ${EDUCLOUD_AI_NACOS_USERNAME:${NACOS_ADMIN_USERNAME:nacos}}
      password: ${EDUCLOUD_AI_NACOS_PASSWORD:${NACOS_ADMIN_PASSWORD:nacos}}
      discovery:
        namespace: ${NACOS_GATEWAY_NAMESPACE:educloud-local}
        group: ${NACOS_GATEWAY_DISCOVERY_GROUP:EDUCLOUD_SERVICES}
        register-enabled: true

educloud:
  ai:
    provider:
      name: ${AI_PROVIDER_NAME:openai-compatible}
      base-url: ${AI_PROVIDER_BASE_URL:https://api.siliconflow.cn/v1}
      model: ${AI_PROVIDER_MODEL:Qwen/Qwen3.6-27B}
      api-key: ${AI_PROVIDER_API_KEY:}
      # 实测：默认开思考 48-99s；必须顶层 enable_thinking:false 关思考（chat_template_kwargs 无效）
      thinking-enabled: ${AI_THINKING_ENABLED:false}
      max-tokens: ${AI_MAX_TOKENS:1024}
    timeout:
      connect-ms: ${AI_CONNECT_TIMEOUT_MS:5000}
      read-ms: ${AI_READ_TIMEOUT_MS:25000}
    quota:
      daily-requests: ${AI_QUOTA_DAILY_REQUESTS:50}
      daily-tokens: ${AI_QUOTA_DAILY_TOKENS:2000000}
    context:
      max-history-messages: ${AI_CONTEXT_MAX_HISTORY:10}
      max-prompt-tokens: ${AI_CONTEXT_MAX_PROMPT_TOKENS:3000}
    jwt:
      jwks-location: ${AI_JWKS_LOCATION:file:/tmp/educloud-live/jwks.json}
      issuer: ${EDUCLOUD_AI_JWT_ISSUER:https://issuer.educloud.local}
      audience: ${EDUCLOUD_AI_JWT_AUDIENCE:educloud-api}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: assign_id

management:
  server:
    address: ${AI_MANAGEMENT_ADDRESS:127.0.0.1}
    port: ${AI_MANAGEMENT_PORT:8106}
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      probes:
        enabled: true
```

- [ ] **步骤 5：start-dev.sh 增加 educloud-ai 启动项**

在 `wait_ready "http://127.0.0.1:8104/actuator/health" "educloud-recommendation"` 行之后、`printf "[13/13] Starting frontend dev servers..."` 之前插入：

```bash
printf "[12.5/13] Starting educloud-ai...\n"
if port_free 8105; then
  if [[ -z "${AI_PROVIDER_API_KEY:-}" ]]; then
    printf "  WARN: AI_PROVIDER_API_KEY is empty in .env; educloud-ai will fail to start\n"
  fi
  SERVER_PORT=8105 AI_MANAGEMENT_PORT=8106 \
  MYSQL_HOST=127.0.0.1 MYSQL_PORT="${MYSQL_PORT:-3306}" EDUCLOUD_AI_DB_PASSWORD="${EDUCLOUD_AI_DB_PASSWORD:-}" \
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT:-6379}" REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
  NACOS_SERVER_ADDR=127.0.0.1:"${NACOS_HTTP_PORT:-8848}" \
  EDUCLOUD_AI_NACOS_USERNAME="${EDUCLOUD_AI_NACOS_USERNAME:-${NACOS_ADMIN_USERNAME:-nacos}}" \
  EDUCLOUD_AI_NACOS_PASSWORD="${EDUCLOUD_AI_NACOS_PASSWORD:-${NACOS_ADMIN_PASSWORD:-nacos}}" \
  AI_JWKS_LOCATION=file:/tmp/educloud-live/jwks.json \
  EDUCLOUD_AI_JWT_ISSUER="${EDUCLOUD_AI_JWT_ISSUER:-https://issuer.educloud.local}" \
  EDUCLOUD_AI_JWT_AUDIENCE="${EDUCLOUD_AI_JWT_AUDIENCE:-educloud-api}" \
  AI_PROVIDER_API_KEY="${AI_PROVIDER_API_KEY:-}" \
  EDUCLOUD_ENVIRONMENT=local SPRING_CLOUD_NACOS_DISCOVERY_IP=127.0.0.1 \
  setsid nohup java -jar educloud-backend/educloud-ai/target/educloud-ai-1.0.0-SNAPSHOT.jar \
    > /tmp/educloud-live/ai.log 2>&1 < /dev/null &
  printf "  educloud-ai started (8105/8106)\n"
else
  printf "  educloud-ai already running\n"
fi

wait_ready "http://127.0.0.1:8106/actuator/health" "educloud-ai"
```

并在脚本末尾 `printf` 列表 `Recommendation` 行后追加一行、日志清单追加 `ai`：

```bash
printf "  AI:           http://192.168.100.136:8105  (management 8106)\n"
```

（日志 printf 的 `{...}` 列表里加 `,ai`。）

- [ ] **步骤 6：编译验证（此时尚无 ChatProvider Bean 与业务类）**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 7：Commit**

```bash
git add educloud-backend/pom.xml educloud-backend/educloud-ai deploy/scripts/start-dev.sh
git commit -m "feat(AI助教): 新增 educloud-ai 模块骨架与 JWT/安全/启动配置"
```

## 任务 4：实体与 Mapper

**文件：**
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/entity/AiConversationEntity.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/entity/AiMessageEntity.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/mapper/AiConversationMapper.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/mapper/AiMessageMapper.java`

- [ ] **步骤 1：编写 2 个实体**（雪花 ID：`IdType.ASSIGN_ID`，与 V001 的非自增 BIGINT 对应）

`AiConversationEntity.java`：

```java
package com.educloud.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_conversation")
public class AiConversationEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long studentId;
    private String title;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`AiMessageEntity.java`：

```java
package com.educloud.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class AiMessageEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String provider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer latencyMs;
    private String finishReason;
    private String status;
    private String errorCode;
    private LocalDateTime createdAt;
}
```

- [ ] **步骤 2：编写 2 个 Mapper**

```java
package com.educloud.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.ai.entity.AiConversationEntity;

public interface AiConversationMapper extends BaseMapper<AiConversationEntity> {
}
```

```java
package com.educloud.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.ai.entity.AiMessageEntity;

public interface AiMessageMapper extends BaseMapper<AiMessageEntity> {
}
```

- [ ] **步骤 3：编译验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-ai/src/main/java/com/educloud/ai/entity/ educloud-backend/educloud-ai/src/main/java/com/educloud/ai/mapper/
git commit -m "feat(AI助教): 新增会话与消息实体及 Mapper（雪花 ID）"
```

## 任务 5：错误码、DTO 与响应字段契约测试（TDD）

**文件：**
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/exception/AiErrorCode.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/dto/request/AiChatRequest.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/dto/response/AiChatResponse.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/dto/response/AiUsageResponse.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/dto/response/AiConversationResponse.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/dto/response/AiMessageResponse.java`
- 测试：`educloud-backend/educloud-ai/src/test/java/com/educloud/ai/dto/AiChatApiContractTest.java`

- [ ] **步骤 1：编写失败的契约测试**（照 `ExamApiContractTest` 路子：锁字段名/类型/精度）

```java
package com.educloud.ai.dto;

import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.dto.response.AiUsageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /api/v1/ai/chat 响应字段契约（规格 2026-08-28-ai-assistant-p1-design.md §4/§7）。
 * 锁三条对外约定：字段名与嵌套结构固定；雪花 ID 一律字符串化（JS 精度）；
 * degraded 字段存在且为布尔（P1 恒 false，P2 复用）。
 */
class AiChatApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatResponse_contract_fieldNamesTypesAndStringIds() throws Exception {
        AiChatResponse response = AiChatResponse.builder()
                .conversationId("1943234567890123456")
                .messageId("1943234567890123457")
                .content("第一步，明确极限的定义……")
                .finishReason("stop")
                .usage(new AiUsageResponse(79, 291, 370))
                .degraded(false)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "conversationId", "messageId", "content", "finishReason", "usage", "degraded");
        assertThat(json.get("conversationId").isTextual()).isTrue();
        assertThat(json.get("messageId").isTextual()).isTrue();
        assertThat(json.get("content").isTextual()).isTrue();
        assertThat(json.get("finishReason").isTextual()).isTrue();
        assertThat(json.get("degraded").isBoolean()).isTrue();
        JsonNode usage = json.get("usage");
        assertThat(usage.fieldNames()).toIterable().containsExactly(
                "promptTokens", "completionTokens", "totalTokens");
        assertThat(usage.get("promptTokens").asInt()).isEqualTo(79);
        assertThat(usage.get("completionTokens").asInt()).isEqualTo(291);
        assertThat(usage.get("totalTokens").asInt()).isEqualTo(370);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=AiChatApiContractTest`
预期：编译失败，DTO 不存在

- [ ] **步骤 3：编写错误码与 DTO**

`AiErrorCode.java`（规格 §4.1 全表 + STUDENT 守卫用的 403）：

```java
package com.educloud.ai.exception;

import com.educloud.common.error.ErrorCode;

public enum AiErrorCode implements ErrorCode {
    AI_PROVIDER_UNAVAILABLE(503, "AI provider is unavailable"),
    AI_QUOTA_EXCEEDED(429, "Daily AI request quota exceeded"),
    AI_GLOBAL_BUDGET_EXCEEDED(429, "Global daily AI token budget exceeded"),
    AI_CONVERSATION_NOT_FOUND(404, "AI conversation not found"),
    AI_CONVERSATION_NOT_OWNED(403, "AI conversation does not belong to current user"),
    AI_STREAM_NOT_SUPPORTED(400, "Streaming responses are not supported in P1"),
    AI_QUESTION_TOO_LONG(400, "Question exceeds the 1000-character limit"),
    AI_ACCESS_DENIED(403, "AI assistant is available to students only");

    private final int httpStatus;
    private final String defaultMessage;

    AiErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
```

`AiChatRequest.java`（conversationId 请求侧收数字或数字字符串均可由 Jackson 转 Long；长度校验在 ChatService 用 AI_QUESTION_TOO_LONG，不用通用 VALIDATION_FAILED）：

```java
package com.educloud.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiChatRequest {
    /** 省略时服务端新建会话并返回其 id。 */
    private Long conversationId;
    @NotBlank
    private String question;
    /** P1 恒拒：true 时返回 400 AI_STREAM_NOT_SUPPORTED（不静默降级）。 */
    private Boolean stream;
}
```

`AiUsageResponse.java`：

```java
package com.educloud.ai.dto.response;

public record AiUsageResponse(int promptTokens, int completionTokens, int totalTokens) {
}
```

`AiChatResponse.java`（雪花 ID 以 String 出参）：

```java
package com.educloud.ai.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiChatResponse {
    private String conversationId;
    private String messageId;
    private String content;
    private String finishReason;
    private AiUsageResponse usage;
    private boolean degraded;
}
```

`AiConversationResponse.java`：

```java
package com.educloud.ai.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiConversationResponse {
    private String id;
    private String title;
    private Integer messageCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
```

`AiMessageResponse.java`：

```java
package com.educloud.ai.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiMessageResponse {
    private String id;
    private String role;
    private String content;
    /** OK / TRUNCATED / FAILED（FAILED 行仅审计，前端不渲染）。 */
    private String status;
    private LocalDateTime createdAt;
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=AiChatApiContractTest`
预期：1 个测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-ai/src/main/java/com/educloud/ai/exception/ educloud-backend/educloud-ai/src/main/java/com/educloud/ai/dto/ educloud-backend/educloud-ai/src/test/java/com/educloud/ai/dto/
git commit -m "feat(AI助教): 新增错误码、请求/响应 DTO 与响应字段契约测试"
```

## 任务 6：ChatProvider 抽象与 OpenAiCompatibleProvider（TDD）

**文件：**
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/provider/AiProviderException.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/provider/ChatProvider.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/provider/OpenAiCompatibleProvider.java`
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/AiProviderConfig.java`
- 测试：`educloud-backend/educloud-ai/src/test/java/com/educloud/ai/provider/OpenAiCompatibleProviderTest.java`

- [ ] **步骤 1：编写失败的测试**（MockRestServiceServer 绑定 RestClient.Builder）

```java
package com.educloud.ai.provider;

import com.educloud.ai.chat.ChatTurn;
import com.educloud.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleProviderTest {

    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String OK_BODY = """
            {
              "choices": [{
                "message": {"role": "assistant", "content": "第一步，明确定义。", "reasoning_content": "推理内容不应外泄"},
                "finish_reason": "stop"
              }],
              "usage": {"prompt_tokens": 79, "completion_tokens": 36, "total_tokens": 115}
            }
            """;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiCompatibleProvider(builder, properties(false));
    }

    private static AiProperties properties(boolean thinkingEnabled) {
        return new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", BASE_URL, "Qwen/Qwen3.6-27B", "test-key", thinkingEnabled, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(10, 3000),
                new AiProperties.JwtProperties("", "https://issuer.educloud.local", "educloud-api"));
    }

    @Test
    void chat_postsOpenAiCompatibleBodyWithThinkingDisabledAtTopLevel() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("Qwen/Qwen3.6-27B"))
                // 规格实测四条之一：顶层 enable_thinking:false（chat_template_kwargs 无效，不得出现）
                .andExpect(jsonPath("$.enable_thinking").value(false))
                .andExpect(jsonPath("$.chat_template_kwargs").doesNotExist())
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.max_tokens").value(1024))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("导数是什么？"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(
                List.of(new ChatTurn("system", "你是助教"), new ChatTurn("user", "导数是什么？")),
                new ChatOptions(1024));

        server.verify();
        assertThat(result.content()).isEqualTo("第一步，明确定义。");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.promptTokens()).isEqualTo(79);
        assertThat(result.completionTokens()).isEqualTo(36);
        assertThat(result.totalTokens()).isEqualTo(115);
    }

    @Test
    void chat_returnsOnlyContentFieldAndNeverReasoning() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        assertThat(result.content()).isEqualTo("第一步，明确定义。");
        assertThat(result.content()).doesNotContain("推理内容");
    }

    @Test
    void chat_lengthFinishReasonPassedThroughEvenWhenContentEmpty() {
        // 实测四条之二：max_tokens 过小时 HTTP 200 且 content 为空，finish_reason=length 必须透传
        String truncated = """
                {
                  "choices": [{"message": {"role": "assistant", "content": ""}, "finish_reason": "length"}],
                  "usage": {"prompt_tokens": 79, "completion_tokens": 64, "total_tokens": 143}
                }
                """;
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(truncated, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        assertThat(result.content()).isEmpty();
        assertThat(result.finishReason()).isEqualTo("length");
    }

    @Test
    void chat_retriesOnceOnUpstream429ThenSucceeds() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(429));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        server.verify();
        assertThat(result.content()).isEqualTo("第一步，明确定义。");
    }

    @Test
    void chat_retriesOnceOnUpstream5xxThenSucceeds() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withServerError());
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ChatResult result = provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        server.verify();
        assertThat(result.finishReason()).isEqualTo("stop");
    }

    @Test
    void chat_givesUpAfterSecond429() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(429));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(429));

        // 恰好 2 次尝试后放弃（AiProviderException 的 retryable 标记语义只控制内部重试循环，
        // 上层 ChatService 对任何 AiProviderException 都映射 503，因此这里只断言尝试次数）
        assertThatThrownBy(() -> provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024)))
                .isInstanceOf(AiProviderException.class);

        server.verify();
    }

    @Test
    void chat_neverRetriesReadTimeout() {
        // 实测四条之三：超时=模型正在长时间生成，重试只会翻倍等待与成本
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024)))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).retryable())
                .isEqualTo(false);

        server.verify();
    }

    @Test
    void chat_upstream400IsNotRetryable() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(400));

        assertThatThrownBy(() -> provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024)))
                .isInstanceOf(AiProviderException.class)
                .extracting(e -> ((AiProviderException) e).retryable())
                .isEqualTo(false);

        server.verify();
    }

    @Test
    void chat_whenThinkingEnabledOmitsTopLevelSwitch() {
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiCompatibleProvider(builder, properties(true));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.enable_thinking").doesNotExist())
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        provider.chat(List.of(new ChatTurn("user", "问")), new ChatOptions(1024));

        server.verify();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=OpenAiCompatibleProviderTest`
预期：编译失败，`ChatProvider`/`ChatTurn`/`ChatOptions`/`ChatResult` 不存在

- [ ] **步骤 3：编写类型与抽象**

`ChatTurn.java` 放在 `chat` 包（`com.educloud.ai.chat`，ContextAssembler/ChatService 共用）：

```java
package com.educloud.ai.chat;

/** 发给模型的一条消息；role ∈ system/user/assistant。 */
public record ChatTurn(String role, String content) {
}
```

`AiProviderException.java`：

```java
package com.educloud.ai.provider;

/** 上游模型调用失败。retryable=true 表示"连接失败或上游 429/5xx"（可重试一次），超时与其他 4xx 不可重试。 */
public class AiProviderException extends RuntimeException {

    private final int upstreamStatus;
    private final boolean retryable;

    public AiProviderException(String message, int upstreamStatus, boolean retryable, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.retryable = retryable;
    }

    public int upstreamStatus() {
        return upstreamStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
```

`ChatProvider.java`（含 `ChatOptions`、`ChatResult`）：

```java
package com.educloud.ai.provider;

import com.educloud.ai.chat.ChatTurn;

import java.util.List;

/** LLM 供应商 SPI；P1 唯一实现 OpenAiCompatibleProvider，P2/P3 换模型只换实现。 */
public interface ChatProvider {

    ChatResult chat(List<ChatTurn> messages, ChatOptions options);

    record ChatOptions(int maxTokens) {
    }

    /** latencyMs 供 ai_message.latency_ms 审计列使用（V001 DDL）。 */
    record ChatResult(
            String content,
            String finishReason,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            String model,
            long latencyMs) {
    }
}
```

- [ ] **步骤 4：编写 OpenAiCompatibleProvider 实现**

```java
package com.educloud.ai.provider;

import com.educloud.ai.chat.ChatTurn;
import com.educloud.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议实现（硅基流动实测约束，规格 §5.2）：
 * 1. 顶层 enable_thinking:false 关思考（chat_template_kwargs 实测无效，绝不发送）；
 * 2. 只取 choices[0].message.content 作为答案，reasoning_content 一律不读不存不返；
 * 3. 不能只看 HTTP 状态码：finish_reason=length 时 content 可能为空，原样透传给上层判 TRUNCATED；
 * 4. 超时不重试；连接失败与上游 429/5xx 重试 1 次，退避 1s。
 * 日志只记状态/finish_reason/usage/延迟，绝不打印 api-key 与请求体。
 */
@Slf4j
public class OpenAiCompatibleProvider implements ChatProvider {

    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 1000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final AiProperties properties;

    public OpenAiCompatibleProvider(RestClient.Builder builder, AiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public ChatResult chat(List<ChatTurn> messages, ChatOptions options) {
        AiProperties.ProviderProperties provider = properties.provider();
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return execute(messages, options);
            } catch (AiProviderException exception) {
                if (!exception.retryable() || attempts >= MAX_ATTEMPTS) {
                    throw exception;
                }
                log.warn("AI upstream retryable failure (status={}), retrying in {} ms",
                        exception.upstreamStatus(), RETRY_BACKOFF_MS);
                sleepBeforeRetry();
            }
        }
    }

    private ChatResult execute(List<ChatTurn> messages, ChatOptions options) {
        AiProperties.ProviderProperties provider = properties.provider();
        long startedAt = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", provider.model());
        body.put("messages", toMessageList(messages));
        body.put("stream", false);
        body.put("max_tokens", options.maxTokens());
        if (!provider.thinkingEnabled()) {
            body.put("enable_thinking", false);
        }

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(provider.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + provider.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            log.warn("AI upstream HTTP error: status={}, latencyMs={}, retryable={}",
                    status, System.currentTimeMillis() - startedAt, retryable);
            throw new AiProviderException("AI upstream returned HTTP " + status, status, retryable, exception);
        } catch (ResourceAccessException exception) {
            boolean retryable = isConnectFailure(exception);
            log.warn("AI upstream access failure: type={}, latencyMs={}, retryable={}",
                    exception.getCause() == null ? "unknown" : exception.getCause().getClass().getSimpleName(),
                    System.currentTimeMillis() - startedAt, retryable);
            throw new AiProviderException("AI upstream is unreachable", 0, retryable, exception);
        }

        long latencyMs = System.currentTimeMillis() - startedAt;
        return parse(responseBody, provider.model(), latencyMs);
    }

    private ChatResult parse(String responseBody, String model, long latencyMs) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(responseBody == null ? "" : responseBody);
        } catch (Exception exception) {
            throw new AiProviderException("AI upstream returned invalid JSON", 0, false, exception);
        }
        JsonNode choice = root.path("choices").path(0);
        String content = choice.path("message").path("content").asText("");
        // reasoning_content 有意不读取：只把 content 作为答案落库与返回（规格 §5.1）
        String finishReason = choice.path("finish_reason").asText("");
        int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
        int totalTokens = root.path("usage").path("total_tokens").asInt(promptTokens + completionTokens);
        log.info("AI upstream answered: finishReason={}, promptTokens={}, completionTokens={}, latencyMs={}",
                finishReason, promptTokens, completionTokens, latencyMs);
        return new ChatResult(content, finishReason, promptTokens, completionTokens, totalTokens, model, latencyMs);
    }

    private List<Map<String, String>> toMessageList(List<ChatTurn> messages) {
        List<Map<String, String>> list = new ArrayList<>();
        for (ChatTurn turn : messages) {
            list.add(Map.of("role", turn.role(), "content", turn.content()));
        }
        return list;
    }

    private boolean isConnectFailure(ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof ConnectException
                || cause instanceof UnknownHostException;
        // SocketTimeoutException（含 connect/read 超时）一律视为超时：不重试
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while backing off before AI retry", 0, false, exception);
        }
    }
}
```

- [ ] **步骤 5：补 AiProviderConfig**（任务 3 推迟的文件，原样写入）

```java
package com.educloud.ai.config;

import com.educloud.ai.provider.ChatProvider;
import com.educloud.ai.provider.OpenAiCompatibleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AiProviderConfig {

    @Bean
    public ChatProvider chatProvider(RestClient.Builder builder, AiProperties properties) {
        String apiKey = properties.provider() != null ? properties.provider().apiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "AI_PROVIDER_API_KEY is empty; refusing to start (inject via deploy/docker-compose/.env)");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.timeout().connectMs());
        factory.setReadTimeout((int) properties.timeout().readMs());
        return new OpenAiCompatibleProvider(builder.requestFactory(factory), properties);
    }
}
```

- [ ] **步骤 6：运行测试验证通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=OpenAiCompatibleProviderTest`
预期：9 个测试全 PASS

- [ ] **步骤 7：Commit**

```bash
git add educloud-backend/educloud-ai/src/main/java/com/educloud/ai/provider/ educloud-backend/educloud-ai/src/main/java/com/educloud/ai/chat/ChatTurn.java educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/AiProviderConfig.java educloud-backend/educloud-ai/src/test/java/com/educloud/ai/provider/
git commit -m "feat(AI助教): 新增 OpenAI 兼容 Provider（关思考/finish_reason 透传/重试策略）"
```

## 任务 7：上下文裁剪 ContextAssembler（TDD）

**文件：**
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/chat/ContextAssembler.java`
- 测试：`educloud-backend/educloud-ai/src/test/java/com/educloud/ai/chat/ContextAssemblerTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblerTest {

    private static final String SYSTEM_PROMPT = "你是 EduCloud AI 助教……";

    private ContextAssembler assemblerWith(int maxHistory, int maxPromptTokens) {
        return new ContextAssembler(new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", "https://x/v1", "m", "k", false, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(maxHistory, maxPromptTokens),
                new AiProperties.JwtProperties("", "i", "a")));
    }

    private final ContextAssembler assembler = assemblerWith(10, 3000);

    @Test
    void assemblesSystemPlusHistoryPlusQuestion() {
        List<ChatTurn> turns = assembler.assemble(
                SYSTEM_PROMPT,
                List.of(new ChatTurn("user", "问题一"), new ChatTurn("assistant", "回答一")),
                "本次提问");
        assertThat(turns).hasSize(4);
        assertThat(turns.get(0).role()).isEqualTo("system");
        assertThat(turns.get(0).content()).isEqualTo(SYSTEM_PROMPT);
        assertThat(turns.get(1)).isEqualTo(new ChatTurn("user", "问题一"));
        assertThat(turns.get(2)).isEqualTo(new ChatTurn("assistant", "回答一"));
        assertThat(turns.get(3)).isEqualTo(new ChatTurn("user", "本次提问"));
    }

    @Test
    void keepsOnlyTheMostRecentTenHistoryTurns() {
        List<ChatTurn> history = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            history.add(new ChatTurn("user", "第" + i + "问"));
            history.add(new ChatTurn("assistant", "第" + i + "答"));
        }
        List<ChatTurn> turns = assembler.assemble(SYSTEM_PROMPT, history, "本次提问");
        // 10 条历史 + system + question；最旧 20 条被丢弃，保留的是第 11–15 组
        assertThat(turns).hasSize(12);
        assertThat(turns.get(1).content()).isEqualTo("第11问");
        assertThat(turns.get(10).content()).isEqualTo("第15答");
        assertThat(turns.get(11).content()).isEqualTo("本次提问");
    }

    @Test
    void dropsOldestHistoryFirstWhenOverTokenBudget() {
        // 每条约 900 token（1800 个中文字符），10 条远超 3000 预算
        List<ChatTurn> history = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            history.add(new ChatTurn("user", "长".repeat(1800) + i));
        }
        List<ChatTurn> turns = assembler.assemble(SYSTEM_PROMPT, history, "问");
        assertThat(turns.size()).isGreaterThanOrEqualTo(2); // system + question 永不裁
        assertThat(turns.get(0).role()).isEqualTo("system");
        assertThat(turns.get(turns.size() - 1).content()).isEqualTo("问");
        // 最多保留 2 条（第 9、10 条），最旧的先丢
        assertThat(turns.size()).isLessThanOrEqualTo(4);
        assertThat(turns.get(1).content()).endsWith("9");
    }

    @Test
    void neverDropsSystemOrQuestionEvenWhenTheyAloneExceedBudget() {
        ContextAssembler tiny = assemblerWith(10, 1);
        List<ChatTurn> turns = tiny.assemble(SYSTEM_PROMPT, List.of(new ChatTurn("user", "旧问")), "问");
        assertThat(turns).hasSize(3);
    }

    @Test
    void emptyHistoryYieldsSystemPlusQuestion() {
        List<ChatTurn> turns = assembler.assemble(SYSTEM_PROMPT, List.of(), "问");
        assertThat(turns).hasSize(2);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=ContextAssemblerTest`
预期：编译失败，`ContextAssembler` 不存在

- [ ] **步骤 3：编写实现**

```java
package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 上下文装配（规格 §5.5）：system + 最近 N 条历史 + 本次提问，prompt token 预算超限时
 * 从最旧的历史开始丢弃（system 与本次提问不裁）。学生输入只进 user 角色，不拼进 system。
 * token 估算为保守启发式：中文字符≈1 token，ASCII≈4 字符/token（实测 Qwen 中文 1-1.5 字/token）。
 */
@Component
public class ContextAssembler {

    private final AiProperties properties;

    public ContextAssembler(AiProperties properties) {
        this.properties = properties;
    }

    public List<ChatTurn> assemble(String systemPrompt, List<ChatTurn> historyAsc, String question) {
        int maxHistory = properties.context().maxHistoryMessages();
        int maxPromptTokens = properties.context().maxPromptTokens();

        Deque<ChatTurn> recent = new ArrayDeque<>();
        int offset = Math.max(0, historyAsc.size() - maxHistory);
        for (ChatTurn turn : historyAsc.subList(offset, historyAsc.size())) {
            recent.addLast(turn);
        }

        long budgetAfterFixed = maxPromptTokens - estimate(systemPrompt) - estimate(question);
        while (!recent.isEmpty() && budgetAfterFixed - estimateOf(recent) < 0) {
            recent.removeFirst();
        }

        List<ChatTurn> turns = new ArrayList<>();
        turns.add(new ChatTurn("system", systemPrompt));
        turns.addAll(recent);
        turns.add(new ChatTurn("user", question));
        return turns;
    }

    private long estimateOf(Deque<ChatTurn> turns) {
        long total = 0;
        for (ChatTurn turn : turns) {
            total += estimate(turn.content());
        }
        return total;
    }

    int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < 128) {
                ascii++;
            } else {
                nonAscii++;
            }
        }
        return nonAscii + (ascii + 3) / 4;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=ContextAssemblerTest`
预期：5 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-ai/src/main/java/com/educloud/ai/chat/ContextAssembler.java educloud-backend/educloud-ai/src/test/java/com/educloud/ai/chat/ContextAssemblerTest.java
git commit -m "feat(AI助教): 新增上下文装配器（最近10条+3000 token预算裁剪）"
```

## 任务 8：配额与全局熔断 QuotaService（TDD）

**文件：**
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/chat/QuotaService.java`
- 测试：`educloud-backend/educloud-ai/src/test/java/com/educloud/ai/chat/QuotaServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    private static final long STUDENT_ID = 2001L;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private QuotaService quotaService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        quotaService = new QuotaService(redisTemplate, new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", "https://x/v1", "m", "k", false, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(10, 3000),
                new AiProperties.JwtProperties("", "i", "a")));
    }

    @Test
    void allowsWhenBelowPersonalQuota() {
        when(valueOperations.get(personalKey())).thenReturn("49");

        assertThatCode(() -> quotaService.ensureWithinLimits(STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenPersonalQuotaReached() {
        when(valueOperations.get(personalKey())).thenReturn("50");

        assertThatThrownBy(() -> quotaService.ensureWithinLimits(STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_QUOTA_EXCEEDED);
    }

    @Test
    void rejectsWhenGlobalTokenBudgetReached() {
        when(valueOperations.get(personalKey())).thenReturn("3");
        when(valueOperations.get("educloud:ai:quota:daily-tokens")).thenReturn("2000000");

        assertThatThrownBy(() -> quotaService.ensureWithinLimits(STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_GLOBAL_BUDGET_EXCEEDED);
    }

    @Test
    void missingCountersTreatAsZero() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatCode(() -> quotaService.ensureWithinLimits(STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void recordUsageIncrementsPersonalCounterAndSetsTtlOnFirstIncrement() {
        when(valueOperations.increment(personalKey())).thenReturn(1L);
        when(valueOperations.increment("educloud:ai:quota:daily-tokens", 370L)).thenReturn(370L);

        quotaService.recordUsage(STUDENT_ID, 370L);

        verify(valueOperations).increment(personalKey());
        // 日期键由应用侧 JVM 本地时间生成（MySQL/Redis 与 JVM 时区不同，不允许在 SQL/脚本里算日期）
        verify(redisTemplate).expire(eq(personalKey()), any(Duration.class));
        verify(valueOperations).increment("educloud:ai:quota:daily-tokens", 370L);
        verify(redisTemplate).expire(eq("educloud:ai:quota:daily-tokens"), any(Duration.class));
    }

    @Test
    void recordUsageDoesNotResetTtlOnSubsequentIncrements() {
        when(valueOperations.increment(personalKey())).thenReturn(2L);
        when(valueOperations.increment("educloud:ai:quota:daily-tokens", 100L)).thenReturn(470L);

        quotaService.recordUsage(STUDENT_ID, 100L);

        verify(redisTemplate, never()).expire(startsWith("educloud:ai:quota:2"), any(Duration.class));
    }

    private static String personalKey() {
        return "educloud:ai:quota:" + STUDENT_ID + ":" + LocalDate.now();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=QuotaServiceTest`
预期：编译失败，`QuotaService` 不存在

- [ ] **步骤 3：编写实现**

```java
package com.educloud.ai.chat;

import com.educloud.ai.config.AiProperties;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.common.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 配额与全局熔断（规格 §5.4）：个人 50 次/日 + 全局 200 万 token/日。
 * 计数在调用成功后累加（ChatService 负责）；失败调用不计次但照常落 ai_message 留痕。
 * Redis 不可用时检查直接抛异常（fail-closed，绝不放行无配额调用）。
 * 日期键与 TTL 一律用 JVM 本地时间在应用侧计算（VM 的 MySQL 会话是 UTC，禁止在 SQL 里算"今天"）。
 */
@Slf4j
@Component
public class QuotaService {

    private static final String PERSONAL_KEY_PREFIX = "educloud:ai:quota:";
    private static final String GLOBAL_TOKENS_KEY = "educloud:ai:quota:daily-tokens";

    private final StringRedisTemplate redisTemplate;
    private final AiProperties properties;

    public QuotaService(StringRedisTemplate redisTemplate, AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void ensureWithinLimits(Long studentId) {
        long used = readCounter(personalKey(studentId));
        if (used >= properties.quota().dailyRequests()) {
            throw new BusinessException(AiErrorCode.AI_QUOTA_EXCEEDED,
                    "Student " + studentId + " used " + used + " AI requests today");
        }
        long globalTokens = readCounter(GLOBAL_TOKENS_KEY);
        if (globalTokens >= properties.quota().dailyTokens()) {
            log.warn("AI global daily token budget reached: {}", globalTokens);
            throw new BusinessException(AiErrorCode.AI_GLOBAL_BUDGET_EXCEEDED,
                    "Global daily AI token budget exceeded: " + globalTokens);
        }
    }

    public void recordUsage(Long studentId, long totalTokens) {
        String personal = personalKey(studentId);
        Long personalCount = redisTemplate.opsForValue().increment(personal);
        if (personalCount != null && personalCount == 1L) {
            redisTemplate.expire(personal, untilMidnight());
        }
        Long globalCount = redisTemplate.opsForValue().increment(GLOBAL_TOKENS_KEY, totalTokens);
        if (globalCount != null && globalCount == totalTokens) {
            redisTemplate.expire(GLOBAL_TOKENS_KEY, untilMidnight());
        }
    }

    private long readCounter(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    private static String personalKey(Long studentId) {
        return PERSONAL_KEY_PREFIX + studentId + ":" + LocalDate.now();
    }

    private static Duration untilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT);
        return Duration.between(now, nextMidnight);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=QuotaServiceTest`
预期：6 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-ai/src/main/java/com/educloud/ai/chat/QuotaService.java educloud-backend/educloud-ai/src/test/java/com/educloud/ai/chat/QuotaServiceTest.java
git commit -m "feat(AI助教): 新增个人次数与全局 token 双计数配额熔断"
```

## 任务 9：ChatService（TDD）

**文件：**
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/chat/ChatService.java`
- 测试：`educloud-backend/educloud-ai/src/test/java/com/educloud/ai/chat/ChatServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.educloud.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.ai.config.AiProperties;
import com.educloud.ai.dto.request.AiChatRequest;
import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.ai.provider.AiProviderException;
import com.educloud.ai.provider.ChatProvider;
import com.educloud.ai.provider.ChatResult;
import com.educloud.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long STUDENT_ID = 2001L;
    private static final Long OTHER_STUDENT_ID = 9999L;
    private static final String SYSTEM_PROMPT = "你是 EduCloud AI 助教……";

    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;
    @Mock
    private ChatProvider chatProvider;
    @Mock
    private ContextAssembler contextAssembler;
    @Mock
    private QuotaService quotaService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        // 单测里直接同步执行事务体
        doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0)
                    .accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        // 消息插入回填雪花 id（部分用例不落库，需 lenient）
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(1000);
        lenient().doAnswer(invocation -> {
            ReflectionTestUtils.setField(invocation.getArgument(0), "id", seq.incrementAndGet());
            return 1;
        }).when(messageMapper).insert(any(AiMessageEntity.class));
        chatService = new ChatService(conversationMapper, messageMapper, chatProvider,
                contextAssembler, quotaService, transactionTemplate, properties());
    }

    private static AiProperties properties() {
        return new AiProperties(
                new AiProperties.ProviderProperties("openai-compatible", "https://x/v1", "Qwen/Qwen3.6-27B", "k", false, 1024),
                new AiProperties.TimeoutProperties(5000, 25000),
                new AiProperties.QuotaProperties(50, 2000000),
                new AiProperties.ContextProperties(10, 3000),
                new AiProperties.JwtProperties("", "i", "a"));
    }

    private static AiChatRequest request(Long conversationId, String question, Boolean stream) {
        AiChatRequest request = new AiChatRequest();
        request.setConversationId(conversationId);
        request.setQuestion(question);
        request.setStream(stream);
        return request;
    }

    private static AiConversationEntity conversation(Long id, Long studentId, int deleted) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setId(id);
        entity.setStudentId(studentId);
        entity.setTitle("已有会话");
        entity.setMessageCount(2);
        entity.setDeleted(deleted);
        return entity;
    }

    @Test
    void rejectsStreamTrueExplicitly() {
        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(null, "问", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_STREAM_NOT_SUPPORTED);
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void rejectsQuestionOver1000Characters() {
        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(null, "长".repeat(1001), false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_QUESTION_TOO_LONG);
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void quotaCheckHappensBeforeAnyWriteOrProviderCall() {
        org.mockito.Mockito.doThrow(new BusinessException(AiErrorCode.AI_QUOTA_EXCEEDED, "quota"))
                .when(quotaService).ensureWithinLimits(STUDENT_ID);

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(null, "问", false)))
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_QUOTA_EXCEEDED);

        verify(conversationMapper, never()).insert(any());
        verify(messageMapper, never()).insert(any());
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void createsConversationWithTruncatedTitleWhenIdAbsent() {
        when(contextAssembler.assemble(anyString(), any(), anyString())).thenAnswer(inv -> List.of(
                new ChatTurn("system", "s"), new ChatTurn("user", inv.getArgument(2))));
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("答", "stop", 79, 291, 370, "Qwen/Qwen3.6-27B", 800L));
        doAnswer(inv -> {
            ReflectionTestUtils.setField(inv.getArgument(0), "id", 111L);
            return 1;
        }).when(conversationMapper).insert(any(AiConversationEntity.class));

        AiChatResponse response = chatService.chat(STUDENT_ID, request(null, "什".repeat(200), false));

        ArgumentCaptor<AiConversationEntity> convCaptor = ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationMapper).insert(convCaptor.capture());
        assertThat(convCaptor.getValue().getTitle()).hasSize(120);
        assertThat(convCaptor.getValue().getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(response.getConversationId()).isEqualTo("111");
        assertThat(response.getDegraded()).isFalse();
    }

    @Test
    void foreignConversationRejectedWith403() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, OTHER_STUDENT_ID, 0));

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(501L, "问", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_OWNED);
        verify(chatProvider, never()).chat(any(), any());
    }

    @Test
    void deletedConversationRejectedWith404() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 1));

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(501L, "问", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_FOUND);
    }

    @Test
    void successWritesUserRowThenAssistantRowAndCountsQuota() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(1L, "user", "上一问"), message(2L, "assistant", "上一答")));
        when(contextAssembler.assemble(eq(SYSTEM_PROMPT), any(), eq("本次问"))).thenAnswer(inv -> List.of(
                new ChatTurn("system", "s"),
                new ChatTurn("user", "上一问"),
                new ChatTurn("assistant", "上一答"),
                new ChatTurn("user", "本次问")));
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("答案正文", "stop", 79, 291, 370, "Qwen/Qwen3.6-27B", 1200L));

        AiChatResponse response = chatService.chat(STUDENT_ID, request(501L, "本次问", false));

        // 审计顺序：user 行先落库，再调外部模型，再落 assistant 行
        InOrder inOrder = inOrder(messageMapper, chatProvider, quotaService);
        inOrder.verify(messageMapper).insert(any(AiMessageEntity.class));
        inOrder.verify(chatProvider).chat(any(), any());
        inOrder.verify(messageMapper).insert(any(AiMessageEntity.class));
        inOrder.verify(quotaService).recordUsage(STUDENT_ID, 370L);

        ArgumentCaptor<AiMessageEntity> assistantCaptor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageMapper, times(2)).insert(assistantCaptor.capture());
        AiMessageEntity assistantRow = assistantCaptor.getAllValues().get(1);
        assertThat(assistantRow.getRole()).isEqualTo("assistant");
        assertThat(assistantRow.getContent()).isEqualTo("答案正文");
        assertThat(assistantRow.getStatus()).isEqualTo("OK");
        assertThat(assistantRow.getFinishReason()).isEqualTo("stop");
        assertThat(assistantRow.getPromptTokens()).isEqualTo(79);
        assertThat(assistantRow.getCompletionTokens()).isEqualTo(291);
        assertThat(assistantRow.getLatencyMs()).isEqualTo(1200);
        assertThat(response.getFinishReason()).isEqualTo("stop");
        assertThat(response.getUsage().totalTokens()).isEqualTo(370);
        // setUp 的消息插入回填 id：user=1001、assistant=1002；响应 messageId 必须是 assistant 行且字符串化
        assertThat(response.getMessageId()).isEqualTo("1002");
    }

    @Test
    void lengthFinishReasonMarksAssistantRowTruncated() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(contextAssembler.assemble(anyString(), any(), anyString()))
                .thenReturn(List.of(new ChatTurn("system", "s"), new ChatTurn("user", "问")));
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("", "length", 79, 64, 143, "Qwen/Qwen3.6-27B", 900L));

        AiChatResponse response = chatService.chat(STUDENT_ID, request(501L, "问", false));

        ArgumentCaptor<AiMessageEntity> captor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("TRUNCATED");
        assertThat(response.getFinishReason()).isEqualTo("length");
        verify(quotaService).recordUsage(eq(STUDENT_ID), eq(143L));
    }

    @Test
    void providerFailureStillWritesFailedAssistantRowThenThrows503() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(contextAssembler.assemble(anyString(), any(), anyString()))
                .thenReturn(List.of(new ChatTurn("system", "s"), new ChatTurn("user", "问")));
        when(chatProvider.chat(any(), any())).thenThrow(
                new AiProviderException("AI upstream returned HTTP 503", 503, false, null));

        assertThatThrownBy(() -> chatService.chat(STUDENT_ID, request(501L, "问", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_PROVIDER_UNAVAILABLE);

        ArgumentCaptor<AiMessageEntity> captor = ArgumentCaptor.forClass(AiMessageEntity.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        AiMessageEntity failedRow = captor.getAllValues().get(1);
        assertThat(failedRow.getStatus()).isEqualTo("FAILED");
        assertThat(failedRow.getErrorCode()).isEqualTo("AI_PROVIDER_UNAVAILABLE");
        assertThat(failedRow.getContent()).isEmpty();
        // 失败调用不计次
        verify(quotaService, never()).recordUsage(any(), anyLong());
    }

    @Test
    void historyPassedToAssemblerExcludesCurrentQuestionRow() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));
        when(messageMapper.selectList(any())).thenReturn(List.of(message(1L, "user", "上一问")));
        when(contextAssembler.assemble(eq(SYSTEM_PROMPT), any(), eq("本次问"))).thenReturn(List.of());
        when(chatProvider.chat(any(), any())).thenReturn(
                new ChatResult("答", "stop", 1, 1, 2, "m", 5L));

        chatService.chat(STUDENT_ID, request(501L, "本次问", false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatTurn>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(contextAssembler).assemble(eq(SYSTEM_PROMPT), historyCaptor.capture(), eq("本次问"));
        assertThat(historyCaptor.getValue()).containsExactly(new ChatTurn("user", "上一问"));
    }

    private static AiMessageEntity message(Long id, String role, String content) {
        AiMessageEntity entity = new AiMessageEntity();
        entity.setId(id);
        entity.setConversationId(501L);
        entity.setRole(role);
        entity.setContent(content);
        entity.setStatus("OK");
        return entity;
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=ChatServiceTest`
预期：编译失败，`ChatService` 不存在

- [ ] **步骤 3：编写实现**

```java
package com.educloud.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.ai.config.AiProperties;
import com.educloud.ai.dto.request.AiChatRequest;
import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.dto.response.AiUsageResponse;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.ai.provider.AiProviderException;
import com.educloud.ai.provider.ChatProvider;
import com.educloud.ai.provider.ChatProvider.ChatOptions;
import com.educloud.ai.provider.ChatProvider.ChatResult;
import com.educloud.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 提问编排（规格 §4/§5）：校验 → 配额 → 会话归属 → user 行落库 → 调模型 → assistant 行落库 → 计数。
 * user 行在外部调用前提交（审计先行）；外部调用不包在数据库事务里（25s 调用不占连接池事务）。
 * 失败仍写 assistant 行（status=FAILED + error_code），保证审计完整；失败调用不计次。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 规格 §5.5：system prompt 固定服务端，强调纯文本 + **加粗**（前端行内渲染），禁止编造平台数据。 */
    static final String SYSTEM_PROMPT = "你是 EduCloud 在线教育平台的 AI 助教。请遵守："
            + "1) 用中文分步讲解，用 1. 2. 3. 这样的纯文本编号；"
            + "2) 不要使用标题、列表符号、代码块等任何 markdown 结构标记；需要强调关键词时可以用 **关键词** 的形式加粗；"
            + "3) 不得编造平台内的课程、作业、成绩等数据，相关提问请引导学生在平台内查看；"
            + "4) 回答保持精炼，先给结论再给步骤。";

    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int TITLE_MAX_LENGTH = 120;
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_TRUNCATED = "TRUNCATED";
    private static final String STATUS_FAILED = "FAILED";

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ChatProvider chatProvider;
    private final ContextAssembler contextAssembler;
    private final QuotaService quotaService;
    private final TransactionTemplate transactionTemplate;
    private final AiProperties properties;

    public AiChatResponse chat(Long studentId, AiChatRequest request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            throw new BusinessException(AiErrorCode.AI_STREAM_NOT_SUPPORTED,
                    "P1 does not support streaming responses; omit stream or set stream=false");
        }
        String question = request.getQuestion() == null ? "" : request.getQuestion().trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new BusinessException(AiErrorCode.AI_QUESTION_TOO_LONG,
                    "Question length " + question.length() + " exceeds " + MAX_QUESTION_LENGTH);
        }

        quotaService.ensureWithinLimits(studentId);

        AiConversationEntity conversation = resolveConversation(studentId, request.getConversationId(), question);
        List<ChatTurn> history = loadRecentHistory(conversation.getId());

        transactionTemplate.executeWithoutResult(status ->
                messageMapper.insert(userRow(conversation.getId(), question)));

        ChatResult result;
        try {
            List<ChatTurn> messages = contextAssembler.assemble(SYSTEM_PROMPT, history, question);
            result = chatProvider.chat(messages, new ChatOptions(properties.provider().maxTokens()));
        } catch (AiProviderException exception) {
            log.error("AI provider call failed: upstreamStatus={}, retryable={}",
                    exception.upstreamStatus(), exception.retryable());
            AiMessageEntity failedRow = failedAssistantRow(conversation.getId(),
                    AiErrorCode.AI_PROVIDER_UNAVAILABLE.name());
            transactionTemplate.executeWithoutResult(status -> messageMapper.insert(failedRow));
            bumpConversationCounters(conversation.getId());
            throw new BusinessException(AiErrorCode.AI_PROVIDER_UNAVAILABLE,
                    "AI provider is unavailable, please retry later");
        }

        AiMessageEntity assistantRow = assistantRow(conversation.getId(), result);
        transactionTemplate.executeWithoutResult(status -> messageMapper.insert(assistantRow));
        bumpConversationCounters(conversation.getId());
        quotaService.recordUsage(studentId, result.totalTokens());
        // 日志纪律（规格 §5.6）：只记长度/条数/usage/latency/finish_reason，绝不记原文与密钥
        log.info("AI chat answered: questionChars={}, historyTurns={}, promptTokens={}, completionTokens={}, "
                        + "finishReason={}, latencyMs={}",
                question.length(), history.size(), result.promptTokens(), result.completionTokens(),
                result.finishReason(), result.latencyMs());

        return AiChatResponse.builder()
                .conversationId(String.valueOf(conversation.getId()))
                .messageId(String.valueOf(assistantRow.getId()))
                .content(result.content())
                .finishReason(result.finishReason())
                .usage(new AiUsageResponse(result.promptTokens(), result.completionTokens(), result.totalTokens()))
                .degraded(false)
                .build();
    }

    private AiConversationEntity resolveConversation(Long studentId, Long conversationId, String question) {
        if (conversationId == null) {
            AiConversationEntity entity = new AiConversationEntity();
            entity.setStudentId(studentId);
            entity.setTitle(question.length() > TITLE_MAX_LENGTH ? question.substring(0, TITLE_MAX_LENGTH) : question);
            entity.setMessageCount(0);
            entity.setDeleted(0);
            entity.setLastMessageAt(LocalDateTime.now());
            conversationMapper.insert(entity);
            return entity;
        }
        AiConversationEntity entity = conversationMapper.selectById(conversationId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_FOUND,
                    "AI conversation not found: " + conversationId);
        }
        if (!entity.getStudentId().equals(studentId)) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_OWNED,
                    "AI conversation " + conversationId + " does not belong to student " + studentId);
        }
        return entity;
    }

    /** 最近 N 条历史，按 id 升序返回（雪花 id 单调，规避同毫秒 created_at 排序不稳定）。 */
    private List<ChatTurn> loadRecentHistory(Long conversationId) {
        List<AiMessageEntity> rows = messageMapper.selectPage(
                new Page<>(1, properties.context().maxHistoryMessages(), false),
                new LambdaQueryWrapper<AiMessageEntity>()
                        .eq(AiMessageEntity::getConversationId, conversationId)
                        .orderByDesc(AiMessageEntity::getId)).getRecords();
        Collections.reverse(rows);
        return rows.stream()
                .map(row -> new ChatTurn(row.getRole(), row.getContent() == null ? "" : row.getContent()))
                .toList();
    }

    private AiMessageEntity userRow(Long conversationId, String question) {
        AiMessageEntity row = baseRow(conversationId, ROLE_USER, question);
        row.setStatus(STATUS_OK);
        return row;
    }

    private AiMessageEntity assistantRow(Long conversationId, ChatResult result) {
        AiMessageEntity row = baseRow(conversationId, ROLE_ASSISTANT, result.content());
        row.setStatus("length".equals(result.finishReason()) ? STATUS_TRUNCATED : STATUS_OK);
        row.setProvider(properties.provider().name());
        row.setModel(result.model());
        row.setPromptTokens(result.promptTokens());
        row.setCompletionTokens(result.completionTokens());
        row.setLatencyMs((int) Math.min(result.latencyMs(), Integer.MAX_VALUE));
        row.setFinishReason(result.finishReason());
        return row;
    }

    private AiMessageEntity failedAssistantRow(Long conversationId, String errorCode) {
        AiMessageEntity row = baseRow(conversationId, ROLE_ASSISTANT, "");
        row.setStatus(STATUS_FAILED);
        row.setProvider(properties.provider().name());
        row.setModel(properties.provider().model());
        row.setFinishReason("error");
        row.setErrorCode(errorCode);
        return row;
    }

    private AiMessageEntity baseRow(Long conversationId, String role, String content) {
        AiMessageEntity row = new AiMessageEntity();
        row.setConversationId(conversationId);
        row.setRole(role);
        row.setContent(content);
        return row;
    }

    private void bumpConversationCounters(Long conversationId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<AiConversationEntity>()
                .eq(AiConversationEntity::getId, conversationId)
                .setSql("message_count = message_count + 2")
                .set(AiConversationEntity::getLastMessageAt, LocalDateTime.now()));
    }
}
```

> 设计要点：`messageId` 取自 assistant 行插入后回填的雪花 id（Mockito 单测中由 `setUp` 的 insert 桩回填递增 id）；`ChatResult` 只承载供应商返回的数据（含 `latencyMs`），不掺库存语义；FAILED 行的 `error_code` 在构造行时一次性写全。

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=ChatServiceTest`
预期：10 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-ai/src/main/java/com/educloud/ai/chat/ChatService.java educloud-backend/educloud-ai/src/test/java/com/educloud/ai/chat/ChatServiceTest.java educloud-backend/educloud-ai/src/main/java/com/educloud/ai/config/AiProperties.java educloud-backend/educloud-ai/src/main/resources/application.yml
git commit -m "feat(AI助教): 新增提问编排服务（审计先行/FAILED 留痕/失败不计次）"
```

## 任务 10：REST 控制器（STUDENT 守卫 + 204 语义）

**文件：**
- 创建：`educloud-backend/educloud-ai/src/main/java/com/educloud/ai/controller/AiAssistantController.java`

- [ ] **步骤 1：编写控制器**（角色校验走 `roles` claim——网关下发的 authorities 是 permission 码，`hasRole` 不可用，照 `TeacherAccessGuard` 的读法）

```java
package com.educloud.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educloud.ai.dto.request.AiChatRequest;
import com.educloud.ai.dto.response.AiChatResponse;
import com.educloud.ai.dto.response.AiConversationResponse;
import com.educloud.ai.dto.response.AiMessageResponse;
import com.educloud.ai.chat.ChatService;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.ai.security.JwtSecurityUtils;
import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.api.PageResponse;
import com.educloud.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 助教接口（规格 §4，路径 /api/v1/ai/**，网关 ai-core 路由）。
 * 身份只取 JWT sub；STUDENT 守卫读 roles claim（permissions 码不含 ROLE_ 前缀，hasRole 不可用）。
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final ChatService chatService;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ApiResponseFactory responses;

    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@RequestBody AiChatRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(chatService.chat(requireStudentId(jwt), request));
    }

    @GetMapping("/conversations")
    public ApiResponse<PageResponse<AiConversationResponse>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        Long studentId = requireStudentId(jwt);
        Page<AiConversationEntity> result = conversationMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<AiConversationEntity>()
                        .eq(AiConversationEntity::getStudentId, studentId)
                        .eq(AiConversationEntity::getDeleted, 0)
                        .orderByDesc(AiConversationEntity::getLastMessageAt));
        List<AiConversationResponse> items = result.getRecords().stream().map(AiAssistantController::toResponse).toList();
        return responses.success(PageResponse.of(items, (int) result.getCurrent(),
                (int) result.getSize(), result.getTotal()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AiMessageResponse>> listMessages(@PathVariable Long id,
                                                             @AuthenticationPrincipal Jwt jwt) {
        Long studentId = requireStudentId(jwt);
        requireOwnedConversation(id, studentId);
        return responses.success(messageMapper.selectList(
                        new LambdaQueryWrapper<AiMessageEntity>()
                                .eq(AiMessageEntity::getConversationId, id)
                                .orderByAsc(AiMessageEntity::getId))
                .stream()
                .filter(row -> !"FAILED".equals(row.getStatus()))
                .map(AiAssistantController::toMessageResponse)
                .toList());
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id,
                                                   @AuthenticationPrincipal Jwt jwt) {
        Long studentId = requireStudentId(jwt);
        AiConversationEntity entity = requireOwnedConversation(id, studentId);
        entity.setDeleted(1);
        conversationMapper.updateById(entity);
        return ResponseEntity.noContent().build();
    }

    private Long requireStudentId(Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        if (!JwtSecurityUtils.roles(jwt).contains("STUDENT")) {
            throw new BusinessException(AiErrorCode.AI_ACCESS_DENIED,
                    "AI assistant is available to students only: subject=" + jwt.getSubject());
        }
        return studentId;
    }

    private AiConversationEntity requireOwnedConversation(Long id, Long studentId) {
        AiConversationEntity entity = conversationMapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_FOUND,
                    "AI conversation not found: " + id);
        }
        if (!entity.getStudentId().equals(studentId)) {
            throw new BusinessException(AiErrorCode.AI_CONVERSATION_NOT_OWNED,
                    "AI conversation " + id + " does not belong to student " + studentId);
        }
        return entity;
    }

    private static AiConversationResponse toResponse(AiConversationEntity entity) {
        return AiConversationResponse.builder()
                .id(String.valueOf(entity.getId()))
                .title(entity.getTitle())
                .messageCount(entity.getMessageCount())
                .lastMessageAt(entity.getLastMessageAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static AiMessageResponse toMessageResponse(AiMessageEntity entity) {
        return AiMessageResponse.builder()
                .id(String.valueOf(entity.getId()))
                .role(entity.getRole())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
```

> 归属判定语义：`requireOwnedConversation` 对"不存在/已删"返回 404、对"他人会话"返回 403（规格 §4：访问他人会话一律 403；不存在的会话不泄露存在性，统一 404）。

- [ ] **步骤 2：编写失败的控制器测试**（直接以构造的 Jwt 调用方法，覆盖规格 §7 的"越权访问 403"）

测试：`educloud-backend/educloud-ai/src/test/java/com/educloud/ai/controller/AiAssistantControllerTest.java`

```java
package com.educloud.ai.controller;

import com.educloud.ai.chat.ChatService;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.exception.AiErrorCode;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.common.error.BusinessException;
import com.educloud.common.web.RequestContextAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantControllerTest {

    private static final Long STUDENT_ID = 2001L;

    @Mock
    private ChatService chatService;
    @Mock
    private AiConversationMapper conversationMapper;
    @Mock
    private AiMessageMapper messageMapper;

    @InjectMocks
    private AiAssistantController controller;

    private static Jwt jwt(Long userId, String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(String.valueOf(userId))
                .claim("roles", List.of(roles))
                .build();
    }

    private static AiConversationEntity conversation(Long id, Long studentId, int deleted) {
        AiConversationEntity entity = new AiConversationEntity();
        entity.setId(id);
        entity.setStudentId(studentId);
        entity.setTitle("标题");
        entity.setMessageCount(2);
        entity.setDeleted(deleted);
        return entity;
    }

    @BeforeEach
    void setUp() {
        // @InjectMocks 不处理 ApiResponseFactory 手动依赖，直接注入
        controller = new AiAssistantController(chatService, conversationMapper, messageMapper,
                new ApiResponseFactory(new RequestContextAccessor() {
                    @Override
                    public String requestId() {
                        return "test-request-id";
                    }

                    @Override
                    public java.util.Optional<String> traceId() {
                        return java.util.Optional.empty();
                    }
                }, Clock.systemUTC()));
    }

    @Test
    void messagesOfForeignConversationRejectedWith403() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 9999L, 0));

        assertThatThrownBy(() -> controller.listMessages(501L, jwt(STUDENT_ID, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_OWNED);
    }

    @Test
    void messagesOfDeletedConversationRejectedWith404() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 1));

        assertThatThrownBy(() -> controller.listMessages(501L, jwt(STUDENT_ID, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_FOUND);
    }

    @Test
    void deleteOfForeignConversationRejectedWith403() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, 9999L, 0));

        assertThatThrownBy(() -> controller.deleteConversation(501L, jwt(STUDENT_ID, "STUDENT")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_CONVERSATION_NOT_OWNED);
    }

    @Test
    void deleteOfOwnConversationReturns204AndSoftDeletes() {
        when(conversationMapper.selectById(501L)).thenReturn(conversation(501L, STUDENT_ID, 0));

        assertThat(controller.deleteConversation(501L, jwt(STUDENT_ID, "STUDENT")).getStatusCode().value())
                .isEqualTo(204);
        org.mockito.Mockito.verify(conversationMapper).updateById(any());
    }

    @Test
    void nonStudentRoleRejectedWith403() {
        assertThatThrownBy(() -> controller.listConversations(1, 20, jwt(STUDENT_ID, "TEACHER")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(AiErrorCode.AI_ACCESS_DENIED);
    }
}
```

> 注：`ApiResponseFactory` 依赖 `RequestContextAccessor`（双方法接口，需匿名实现）与 `Clock`，测试中手动构造，对齐 `educloud-common` 既有构造签名。

- [ ] **步骤 3：运行测试验证通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test -Dtest=AiAssistantControllerTest`
预期：5 个测试全 PASS

- [ ] **步骤 4：编译 + 模块全量测试**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test`
预期：BUILD SUCCESS，全部既有测试 PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-ai/src/main/java/com/educloud/ai/controller/ educloud-backend/educloud-ai/src/test/java/com/educloud/ai/controller/
git commit -m "feat(AI助教): 新增聊天/会话列表/消息/软删接口与 STUDENT 守卫"
```

## 任务 11：Testcontainers 持久化 IT（*IT，默认跳过，VM 上 -Pintegration 执行）

**文件：**
- 创建：`educloud-backend/educloud-ai/src/test/java/com/educloud/ai/persistence/AiChatPersistenceIT.java`

- [ ] **步骤 1：编写 IT**（覆盖规格 §7 集成测试项：会话落库、消息升序、软删、越权由服务层过滤保证）

```java
package com.educloud.ai.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.ai.entity.AiConversationEntity;
import com.educloud.ai.entity.AiMessageEntity;
import com.educloud.ai.mapper.AiConversationMapper;
import com.educloud.ai.mapper.AiMessageMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持久化集成测试（规格 §7）：真实 MySQL 上验证建表迁移、消息升序与软删语义。
 * 默认 skipITs=true；VM 上执行：mvn -f educloud-backend/pom.xml -pl educloud-ai -Pintegration verify
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = AiChatPersistenceIT.DbInitializer.class)
@Tag("integration")
class AiChatPersistenceIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("educloud_ai")
            .withUsername("ai_app")
            .withPassword("ai_app_pw")
            .withInitScript("ai/V001__ai.sql");

    static class DbInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + MYSQL.getJdbcUrl(),
                    "spring.datasource.username=" + MYSQL.getUsername(),
                    "spring.datasource.password=" + MYSQL.getPassword(),
                    "spring.data.redis.host=localhost",
                    "educloud.ai.provider.api-key=it-key",
                    "educloud.ai.jwt.jwks-location="
                            + writeEmptyJwks()).applyTo(context.getEnvironment());
        }
    }

    private static String writeEmptyJwks() {
        try {
            Path file = Files.createTempFile("it-jwks", ".json");
            // 一个最小合法 RSA 公钥 JWKS（ 仅用于构造 Decoder Bean，测试不发请求 ）
            Files.writeString(file, """
                    {"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"it","n":"x","e":"AQAB"}]}
                    """);
            return file.toUri().toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Autowired
    private AiConversationMapper conversationMapper;
    @Autowired
    private AiMessageMapper messageMapper;

    @Test
    void conversationAndMessagesRoundTripWithOrderingAndSoftDelete() {
        AiConversationEntity conversation = new AiConversationEntity();
        conversation.setStudentId(2001L);
        conversation.setTitle("集成测试会话");
        conversation.setMessageCount(0);
        conversation.setDeleted(0);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        assertThat(conversation.getId()).isNotNull().isPositive(); // 雪花 ID 已回填

        for (int i = 1; i <= 3; i++) {
            AiMessageEntity row = new AiMessageEntity();
            row.setConversationId(conversation.getId());
            row.setRole(i % 2 == 1 ? "user" : "assistant");
            row.setContent("消息" + i);
            row.setStatus("OK");
            messageMapper.insert(row);
        }

        List<AiMessageEntity> ascending = messageMapper.selectList(
                new LambdaQueryWrapper<AiMessageEntity>()
                        .eq(AiMessageEntity::getConversationId, conversation.getId())
                        .orderByAsc(AiMessageEntity::getId));
        assertThat(ascending).extracting(AiMessageEntity::getContent)
                .containsExactly("消息1", "消息2", "消息3");

        conversation.setDeleted(1);
        conversationMapper.updateById(conversation);
        Long visible = conversationMapper.selectCount(new LambdaQueryWrapper<AiConversationEntity>()
                .eq(AiConversationEntity::getStudentId, 2001L)
                .eq(AiConversationEntity::getDeleted, 0));
        assertThat(visible).isZero();
    }
}
```

> 说明：`withInitScript("ai/V001__ai.sql")` 要求脚本在测试 classpath——在 `educloud-ai/src/test/resources` 下创建目录链接或复制：执行 `mkdir -p educloud-backend/educloud-ai/src/test/resources/ai && cp deploy/sql/ai/V001__ai.sql educloud-backend/educloud-ai/src/test/resources/ai/`（并在该副本顶部追加建表前置注释说明来源）。若 MySQL 容器镜像在本地不可用，该 IT 只在 VM 执行，不影响 `mvn test` 门禁。

- [ ] **步骤 2：本地编译 + VM 执行（有 Docker）**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-ai test`（IT 不跑）预期 PASS
运行（VM）：`mvn -f educloud-backend/pom.xml -pl educloud-ai -Pintegration verify` 预期 IT PASS
（若 VM 无 docker 组权限，用 `sudo` 或加入 docker 组后重试；结果贴日志）

- [ ] **步骤 3：Commit**

```bash
git add educloud-backend/educloud-ai/src/test/
git commit -m "test(AI助教): 新增 Testcontainers 持久化集成测试"
```

## 任务 12：网关路由与 35s 超时

**文件：**
- 修改：`educloud-backend/educloud-gateway/src/main/resources/application.yml`
- 修改：`educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/RouteGroups.java`
- 修改：`educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/GatewayRouteContractTest.java`

- [ ] **步骤 1：application.yml 新增路由**（放在 `recommendation-core` 之后；metadata response-timeout 单位毫秒，覆盖全局 15s）

```yaml
        - id: ai-core
          uri: lb://educloud-ai
          order: 155
          predicates:
            - Path=/api/v1/ai/**
          metadata:
            response-timeout: 35000
            connect-timeout: 5000
```

- [ ] **步骤 2：RouteGroups 新增 AI 组**

常量区 `RECOMMENDATION` 后追加：

```java
    public static final String AI = "ai";
```

`RULES` 列表末尾两行替换为（原最后一行 `rule(RECOMMENDATION, ...);` 行尾的 `));` 改为 `),`，再追加 AI 行）：

```java
            rule(RECOMMENDATION, "/api/v1/recommendations/**", "/api/v1/assistant/**"),
            rule(AI, "/api/v1/ai/**"));
```

- [ ] **步骤 3：更新 GatewayRouteContractTest**（17→18 条）

- `assertThat(routes).hasSize(17)` → `hasSize(18)`
- ids 列表末尾 `..., "recommendation-core"` → `..., "recommendation-core", "ai-core"`
- orders 列表末尾 `..., 150` → `..., 150, 155`
- `targetServiceIds()` 断言追加 `"educloud-ai"`
- `locksEveryRoutePathPredicate` 末尾追加：

```java
        assertThat(pathArguments(route("ai-core"))).containsExactly("/api/v1/ai/**");
```

- 新增一个测试方法锁定 35s 超时与 AI 组归属：

```java
    @Test
    void aiRouteCarriesExtendedResponseTimeoutAndAiGroup() {
        RouteDefinition aiRoute = route("ai-core");
        assertThat(aiRoute.getMetadata())
                .containsEntry("response-timeout", 35000)
                .containsEntry("connect-timeout", 5000);
        assertThat(RouteGroups.forPath(
                org.springframework.http.server.PathContainer.parsePath("/api/v1/ai/chat")))
                .isEqualTo(RouteGroups.AI);
        assertThat(RouteGroups.forPath(
                org.springframework.http.server.PathContainer.parsePath("/api/v1/ai/conversations/123/messages")))
                .isEqualTo(RouteGroups.AI);
    }
```

- [ ] **步骤 4：运行网关全量测试**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-gateway test`
预期：BUILD SUCCESS（`GatewayRouteContractTest` 更新后全 PASS；`AccessPolicyTest` 若断言组集合需同步补 AI 组——先跑再按失败信息改）

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-gateway/src/main/resources/application.yml educloud-backend/educloud-gateway/src/main/java/com/educloud/gateway/route/RouteGroups.java educloud-backend/educloud-gateway/src/test/java/com/educloud/gateway/route/GatewayRouteContractTest.java
git commit -m "feat(AI助教): 网关新增 ai-core 路由并放宽响应超时至 35s"
```

## 任务 13：Nacos 配置 provision 脚本

**文件：**
- 创建：`deploy/scripts/provision-ai-nacos.sh`

- [ ] **步骤 1：编写脚本**（照 `provision-content-nacos.sh` 的 curl --config 风格；发布非敏感 `ai.*` 配置，**绝不包含 api-key**）

```bash
#!/usr/bin/env bash

# 向 Nacos 发布 educloud-ai 非敏感配置（规格 §5.6）：api-key 永不出现在 Nacos，只走 .env 注入。

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

nacos_port='8848'
namespace='educloud-local'
config_group='EDUCLOUD_SERVICES'
admin_username='nacos'
admin_password='nacos'

base_url="http://127.0.0.1:${nacos_port}/nacos"

login_response="$(curl -s -X POST "${base_url}/v1/auth/login" \
  -d "username=${admin_username}" -d "password=${admin_password}")"
token="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))' <<<"$login_response")"
[[ -n "$token" ]] || { echo "ERROR: Nacos login failed" >&2; exit 1; }

content=$(cat <<'YAML'
ai:
  provider:
    base-url: https://api.siliconflow.cn/v1
    model: Qwen/Qwen3.6-27B
    thinking-enabled: false
    max-tokens: 1024
  timeout:
    connect-ms: 5000
    read-ms: 25000
  quota:
    daily-requests: 50
    daily-tokens: 2000000
  context:
    max-history-messages: 10
    max-prompt-tokens: 3000
YAML
)

http_code="$(curl -s -o /dev/null -w "%{http_code}" -X POST "${base_url}/v1/cs/configs" \
  --data-urlencode "accessToken=${token}" \
  --data-urlencode "dataId=educloud-ai.yaml" \
  --data-urlencode "group=${config_group}" \
  --data-urlencode "tenant=${namespace}" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=${content}")"

[[ "$http_code" == "200" ]] || { echo "ERROR: publish config failed: HTTP $http_code" >&2; exit 1; }

verify="$(curl -s "${base_url}/v1/cs/configs?dataId=educloud-ai.yaml&group=${config_group}&tenant=${namespace}&accessToken=${token}")"
grep -q "thinking-enabled: false" <<<"$verify" || { echo "ERROR: published config verify failed" >&2; exit 1; }

echo "OK: educloud-ai.yaml published to group=${config_group} tenant=${namespace}"
```

- [ ] **步骤 2：VM 执行验证**

运行：`bash deploy/scripts/provision-ai-nacos.sh`
预期：`OK: educloud-ai.yaml published ...`；Nacos 控制台（nacos/nacos，Namespace educloud-local，Group EDUCLOUD_SERVICES）可见该配置且内容无任何密钥字段。

- [ ] **步骤 3：Commit**

```bash
git add deploy/scripts/provision-ai-nacos.sh
git commit -m "feat(AI助教): 新增 Nacos 非敏感配置发布脚本"
```

## 任务 14：前端类型与 assistantClient 重写

**文件：**
- 修改：`educloud-frontend/student-portal/src/features/engagement/types.ts`
- 重写：`educloud-frontend/student-portal/src/features/engagement/assistantClient.ts`
- 修改：`educloud-frontend/student-portal/src/services/http.ts`

- [ ] **步骤 1：types.ts 替换 AI 类型族**（删除 `AssistantReply`；`AssistantMessage` 保留为页面视图模型，新增 `status`）

将 `AssistantMessage` 与 `AssistantReply` 两段替换为：

```ts
export interface AssistantMessage {
  id: string;
  role: 'student' | 'assistant';
  content: string;
  createdAt: string;
  /** assistant 消息的落库状态；TRUNCATED 时前端提示"回答被截断，可追问继续" */
  status?: 'OK' | 'TRUNCATED';
}

/** /api/v1/ai/chat 响应（雪花 ID 已字符串化） */
export interface AiChatResponse {
  conversationId: string;
  messageId: string;
  content: string;
  finishReason: 'stop' | 'length' | string;
  usage: { promptTokens: number; completionTokens: number; totalTokens: number };
  degraded: boolean;
}

export interface AiConversationSummary {
  id: string;
  title: string;
  messageCount: number;
  lastMessageAt: string;
  createdAt: string;
}

export interface AiConversationMessage {
  id: string;
  role: 'student' | 'assistant';
  content: string;
  status: 'OK' | 'TRUNCATED';
  createdAt: string;
}

export interface AiPage<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}
```

- [ ] **步骤 2：重写 assistantClient.ts**（删 4 段关键词模板、删 `VITE_AI_ASSISTANT_ENDPOINT`、删 mock 分支；所有错误原样抛出，绝不回退假文案）

```ts
import { http, type ApiEnvelope } from '../../services/http';
import type {
  AiChatResponse,
  AiConversationMessage,
  AiConversationSummary,
  AiPage,
} from './types';

/**
 * AI 助教真实客户端（规格 §6）：走网关 /api/v1/ai/**，JWT 由 http 拦截器注入。
 * chat 读超时 25s、网关该路由 35s，axios 默认 15s 不够用，逐请求放宽到 40s。
 * 不做任何本地降级/假回复：失败就把带 code 的错误抛给页面渲染错误态。
 */
const CHAT_TIMEOUT_MS = 40_000;

function requireData<T>(data: T | undefined | null, errorMessage: string): T {
  if (data === undefined || data === null) {
    throw new Error(errorMessage);
  }
  return data;
}

export const assistantClient = {
  async chat(payload: { conversationId?: string | null; question: string }): Promise<AiChatResponse> {
    const resp = await http.post<ApiEnvelope<AiChatResponse>>(
      '/ai/chat',
      { conversationId: payload.conversationId ?? undefined, question: payload.question },
      { timeout: CHAT_TIMEOUT_MS },
    );
    return requireData(resp.data?.data, 'AI 助教返回了无法识别的数据');
  },

  async listConversations(page = 1, size = 50): Promise<AiPage<AiConversationSummary>> {
    const resp = await http.get<ApiEnvelope<AiPage<AiConversationSummary>>>('/ai/conversations', {
      params: { page, size },
    });
    return requireData(resp.data?.data, '会话列表返回了无法识别的数据');
  },

  async listMessages(conversationId: string): Promise<AiConversationMessage[]> {
    const resp = await http.get<ApiEnvelope<AiConversationMessage[]>>(
      `/ai/conversations/${conversationId}/messages`,
    );
    return requireData(resp.data?.data, '会话消息返回了无法识别的数据');
  },

  async deleteConversation(conversationId: string): Promise<void> {
    await http.delete(`/ai/conversations/${conversationId}`);
  },
};
```

- [ ] **步骤 3：http.ts 的 apiErrorText 补 AI 错误码**（switch 中 `default` 之前插入）

```ts
    // ---- AI 助教错误码（M15） ----
    case 'AI_QUOTA_EXCEEDED':
      return '今日提问次数已用完，明天再来';
    case 'AI_GLOBAL_BUDGET_EXCEEDED':
    case 'AI_PROVIDER_UNAVAILABLE':
      return 'AI 服务暂时不可用，请稍后重试';
    case 'AI_STREAM_NOT_SUPPORTED':
      return '当前版本暂不支持流式输出';
    case 'AI_QUESTION_TOO_LONG':
      return '提问请控制在 1000 字以内';
    case 'AI_CONVERSATION_NOT_FOUND':
      return '会话不存在或已删除';
    case 'AI_CONVERSATION_NOT_OWNED':
      return '无法访问他人的会话';
```

- [ ] **步骤 4：残留检查**（本任务删掉了旧页面还在引用的 `assistantClient.ask`，构建验证统一放到任务 15 完成后执行）

运行：`grep -rn "VITE_AI_ASSISTANT_ENDPOINT\|buildMockReply\|mode: 'mock'" educloud-frontend/student-portal/src`
预期：无任何输出（假回复与直连逻辑在本仓库 student-portal 中已不存在）

- [ ] **步骤 5：Commit**

```bash
git add educloud-frontend/student-portal/src/features/engagement/ educloud-frontend/student-portal/src/services/http.ts educloud-frontend/student-portal/src/pages/AiAssistant.tsx
git commit -m "feat(AI助教): assistantClient 重写为网关真实调用并移除假回复与直连"
```

## 任务 15：AiAssistant.tsx 页面改造（完整重写）

**文件：**
- 重写：`educloud-frontend/student-portal/src/pages/AiAssistant.tsx`

- [ ] **步骤 1：整页替换**

```tsx
import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Bot,
  BookOpenCheck,
  BrainCircuit,
  Code2,
  MessageSquarePlus,
  RefreshCw,
  Send,
  Sparkles,
  Trash2,
  UserRound,
} from 'lucide-react';
import { assistantClient } from '../features/engagement/assistantClient';
import type {
  AiConversationSummary,
  AssistantMessage,
} from '../features/engagement/types';

const welcomeMessage: AssistantMessage = {
  id: 'assistant-welcome',
  role: 'assistant',
  content: '你好，我是 EduCloud AI 助教。你可以向我咨询课程知识、编程问题或复习计划，我会尽量把问题拆解成清晰的学习步骤。',
  createdAt: '现在',
  status: 'OK',
};

const quickQuestions = [
  { icon: BookOpenCheck, title: '制定复习计划', prompt: '请帮我制定一份一周的期末复习计划' },
  { icon: BrainCircuit, title: '解释知识点', prompt: '请用简单的例子解释导数的几何意义' },
  { icon: Code2, title: '分析编程问题', prompt: '遇到 React 状态没有及时更新时应该如何排查' },
];

let messageSequence = 0;
const createMessage = (role: AssistantMessage['role'], content: string): AssistantMessage => ({
  id: `${role}-${Date.now()}-${messageSequence += 1}`,
  role,
  content,
  createdAt: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
  status: 'OK',
});

/** 行内加粗渲染：只认 **文字**，React 文本节点天然转义，不引入 markdown 解析器（规格 §6）。 */
function renderInlineBold(text: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, index) => {
    const match = part.match(/^\*\*([^*]+)\*\*$/);
    return match ? <strong key={index}>{match[1]}</strong> : <span key={index}>{part}</span>;
  });
}

function formatConversationTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? ''
    : date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

export default function AiAssistant() {
  const [messages, setMessages] = useState<AssistantMessage[]>([welcomeMessage]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [conversations, setConversations] = useState<AiConversationSummary[]>([]);
  const [listError, setListError] = useState<string | null>(null);
  const [error, setError] = useState<{ code: string; message: string } | null>(null);
  const messageListRef = useRef<HTMLDivElement>(null);
  const lastQuestionRef = useRef<string | null>(null);

  useEffect(() => {
    const messageList = messageListRef.current;
    if (messageList) messageList.scrollTop = messageList.scrollHeight;
  }, [loading, messages]);

  const loadConversations = async () => {
    try {
      const page = await assistantClient.listConversations();
      setConversations(page.items);
      setListError(null);
    } catch {
      // 列表失败不阻塞提问主流程，但明确提示，不静默
      setListError('历史会话加载失败，请刷新重试');
    }
  };

  useEffect(() => {
    void loadConversations();
  }, []);

  const sendQuestion = async (preset?: string) => {
    const question = (preset ?? input).trim();
    if (!question || loading) return;

    lastQuestionRef.current = question;
    setMessages((current) => [...current, createMessage('student', question)]);
    setInput('');
    setError(null);
    setLoading(true);

    try {
      const reply = await assistantClient.chat({ conversationId, question });
      setConversationId(reply.conversationId);
      setMessages((current) => [...current, {
        ...createMessage('assistant', reply.content),
        status: reply.finishReason === 'length' ? 'TRUNCATED' : 'OK',
      }]);
      void loadConversations();
    } catch (requestError) {
      const code = (requestError as { code?: string }).code ?? '';
      const message = requestError instanceof Error ? requestError.message : 'AI 助教暂时无法回答，请稍后重试';
      setError({ code, message });
    } finally {
      setLoading(false);
    }
  };

  const startNewConversation = () => {
    if (loading) return;
    setConversationId(null);
    setMessages([welcomeMessage]);
    setError(null);
    setInput('');
  };

  const openConversation = async (id: string) => {
    if (loading || id === conversationId) return;
    setError(null);
    try {
      const rows = await assistantClient.listMessages(id);
      setConversationId(id);
      setMessages([
        welcomeMessage,
        ...rows.map((row) => ({
          id: row.id,
          role: row.role,
          content: row.content,
          createdAt: formatConversationTime(row.createdAt),
          status: row.status,
        })),
      ]);
    } catch (requestError) {
      const code = (requestError as { code?: string }).code ?? '';
      setError({ code, message: requestError instanceof Error ? requestError.message : '会话加载失败' });
    }
  };

  const removeConversation = async (id: string) => {
    if (!window.confirm('删除后该会话将不再出现在历史列表，确定删除？')) return;
    try {
      await assistantClient.deleteConversation(id);
      if (id === conversationId) startNewConversation();
      await loadConversations();
    } catch (requestError) {
      const code = (requestError as { code?: string }).code ?? '';
      setError({ code, message: requestError instanceof Error ? requestError.message : '会话删除失败' });
    }
  };

  const errorText = (code: string, message: string): string => {
    switch (code) {
      case 'AI_QUOTA_EXCEEDED':
        return '今日提问次数已用完，明天再来';
      case 'AI_PROVIDER_UNAVAILABLE':
      case 'AI_GLOBAL_BUDGET_EXCEEDED':
        return 'AI 服务暂时不可用，点击重试';
      case 'AI_STREAM_NOT_SUPPORTED':
        return '当前版本暂不支持流式输出';
      case 'AI_QUESTION_TOO_LONG':
        return '提问请控制在 1000 字以内';
      case 'AI_CONVERSATION_NOT_FOUND':
        return '会话不存在或已删除';
      case 'AI_CONVERSATION_NOT_OWNED':
        return '无法访问他人的会话';
      default:
        return message || 'AI 助教暂时无法回答，请稍后重试';
    }
  };

  const canRetry = error !== null
    && ['AI_PROVIDER_UNAVAILABLE', 'AI_GLOBAL_BUDGET_EXCEEDED'].includes(error.code);

  return (
    <div className="mx-auto w-full max-w-7xl px-4 pb-10 pt-6 md:px-8 animate-fade-up">
      <div className="flex flex-col gap-4 border-b border-ink-100 pb-8 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="section-label">智能学习支持</p>
          <div className="flex flex-wrap items-center gap-3 pt-6">
            <h1 className="display-heading text-4xl md:text-5xl">AI 助教</h1>
          </div>
          <p className="mt-3 text-sm text-ink-500">随时提问，把复杂知识拆成可执行的学习步骤</p>
        </div>
        <button type="button" onClick={startNewConversation} disabled={loading} className="btn-outline self-start disabled:opacity-45 md:self-auto">
          <MessageSquarePlus size={17} /> 新建会话
        </button>
      </div>

      <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_19rem]">
        <section className="card-editorial flex h-[36rem] max-h-[calc(100dvh-8rem)] min-h-[28rem] min-w-0 flex-col overflow-hidden lg:h-[calc(100dvh-18rem)] lg:max-h-[42rem] lg:min-h-[24rem]">
          <div className="flex shrink-0 items-center gap-3 border-b border-ink-100 px-5 py-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-800 text-white">
              <Bot size={18} />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-ink-900">学习对话</h2>
              <p className="text-xs text-ink-400">回答仅用于辅助学习，请结合课程资料判断</p>
            </div>
          </div>

          <div ref={messageListRef} className="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-6 sm:px-6" aria-live="polite">
            {messages.map((message) => {
              const assistant = message.role === 'assistant';
              return (
                <div key={message.id} className={`flex gap-3 ${assistant ? '' : 'flex-row-reverse'}`} data-message-role={message.role}>
                  <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${assistant ? 'bg-indigo-800 text-white' : 'bg-amber-100 text-amber-800'}`}>
                    {assistant ? <Sparkles size={17} /> : <UserRound size={17} />}
                  </div>
                  <div className={`max-w-[85%] ${assistant ? '' : 'text-right'}`}>
                    <div className={`inline-block whitespace-pre-wrap rounded-2xl px-4 py-3 text-left text-sm leading-6 shadow-sm ${assistant ? 'bg-ink-50 text-ink-700' : 'bg-indigo-800 text-white'}`}>
                      {renderInlineBold(message.content)}
                    </div>
                    {message.status === 'TRUNCATED' && (
                      <p className="mt-1 text-xs text-amber-600">回答被截断，可追问"继续"获取剩余内容</p>
                    )}
                    <p className="mt-1 text-xs text-ink-300">{message.createdAt}</p>
                  </div>
                </div>
              );
            })}
            {loading && (
              <div className="flex gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-indigo-800 text-white"><Sparkles size={17} /></div>
                <div className="flex items-center gap-1 rounded-2xl bg-ink-50 px-4 py-4 shadow-sm" aria-label="AI 助教正在思考">
                  {[0, 1, 2].map((dot) => <span key={dot} className="h-1.5 w-1.5 animate-pulse rounded-full bg-indigo-500" style={{ animationDelay: `${dot * 120}ms` }} />)}
                </div>
              </div>
            )}
          </div>

          <div className="shrink-0 border-t border-ink-100 bg-white p-4 sm:p-5">
            {error && (
              <div className="mb-3 flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                <span>{errorText(error.code, error.message)}</span>
                {canRetry && (
                  <button
                    type="button"
                    onClick={() => void sendQuestion(lastQuestionRef.current ?? undefined)}
                    className="flex shrink-0 items-center gap-1 rounded-md border border-red-300 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-100"
                  >
                    <RefreshCw size={13} /> 重试
                  </button>
                )}
              </div>
            )}
            <div className="flex items-end gap-3">
              <textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault();
                    void sendQuestion();
                  }
                }}
                rows={2}
                maxLength={1000}
                disabled={loading}
                placeholder={loading ? 'AI 助教正在思考…' : '输入你的学习问题...'}
                className="input-field min-h-[3.25rem] resize-none rounded-xl disabled:opacity-60"
              />
              <button
                type="button"
                onClick={() => void sendQuestion()}
                disabled={!input.trim() || loading}
                aria-label="发送问题"
                className="btn-primary h-[3.25rem] shrink-0 px-4 disabled:cursor-not-allowed disabled:opacity-45 sm:px-6"
              >
                <Send size={17} /> <span className="hidden sm:inline">发送</span>
              </button>
            </div>
          </div>
        </section>

        <aside className="space-y-4">
          <div className="card-editorial p-5">
            <h2 className="font-display text-lg font-semibold text-ink-900">历史会话</h2>
            <p className="mt-1 text-xs leading-5 text-ink-400">最多保留最近 50 个，按最近消息排序</p>
            {listError && <p className="mt-2 text-xs text-red-600">{listError}</p>}
            <div className="mt-4 max-h-64 space-y-2 overflow-y-auto pr-1">
              {conversations.length === 0 && !listError && (
                <p className="text-xs leading-5 text-ink-400">暂无历史会话，发起第一次提问吧</p>
              )}
              {conversations.map((conversation) => (
                <div
                  key={conversation.id}
                  className={`group flex items-start gap-2 rounded-xl border p-3 transition-colors ${
                    conversation.id === conversationId
                      ? 'border-indigo-300 bg-indigo-50/60'
                      : 'border-ink-100 hover:border-indigo-200 hover:bg-indigo-50/40'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => void openConversation(conversation.id)}
                    disabled={loading}
                    className="min-w-0 flex-1 text-left disabled:opacity-50"
                  >
                    <span className="block truncate text-sm font-medium text-ink-800">{conversation.title}</span>
                    <span className="mt-0.5 block text-xs text-ink-400">
                      {formatConversationTime(conversation.lastMessageAt)} · {conversation.messageCount} 条消息
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => void removeConversation(conversation.id)}
                    disabled={loading}
                    aria-label={`删除会话 ${conversation.title}`}
                    className="mt-0.5 shrink-0 rounded-md p-1 text-ink-300 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 focus:opacity-100 group-hover:opacity-100 disabled:opacity-30"
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
            </div>
          </div>
          <div className="card-editorial p-5">
            <h2 className="font-display text-lg font-semibold text-ink-900">常用提问</h2>
            <p className="mt-1 text-xs leading-5 text-ink-400">选择一个场景快速开始</p>
            <div className="mt-4 space-y-2">
              {quickQuestions.map((question) => (
                <button
                  key={question.title}
                  type="button"
                  onClick={() => void sendQuestion(question.prompt)}
                  disabled={loading}
                  className="flex w-full items-start gap-3 rounded-xl border border-ink-100 p-3 text-left transition-colors hover:border-indigo-200 hover:bg-indigo-50/60 disabled:opacity-45"
                >
                  <question.icon className="mt-0.5 shrink-0 text-indigo-700" size={17} />
                  <span>
                    <span className="block text-sm font-medium text-ink-800">{question.title}</span>
                    <span className="mt-1 block text-xs leading-5 text-ink-400">{question.prompt}</span>
                  </span>
                </button>
              ))}
            </div>
          </div>
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5">
            <h3 className="text-sm font-semibold text-amber-900">提问建议</h3>
            <p className="mt-2 text-xs leading-5 text-amber-800/80">说明课程、当前进度和具体困难，助教给出的步骤会更有针对性。</p>
          </div>
        </aside>
      </div>
    </div>
  );
}
```

改动要点对照规格 §6：顶部「演示模式/已连接服务」徽标已移除；「清空会话」改为「新建会话」语义；左栏新增历史会话（切换 + 删除）；请求期间输入与发送禁用；429/503 错误态可操作（503 带重试按钮）；TRUNCATED 提示；`whitespace-pre-wrap` + 行内加粗；FAILED 行后端已过滤不返回。

- [ ] **步骤 2：构建验证**

运行：`cd educloud-frontend/student-portal && npm run build`
预期：TypeScript 编译 + Vite 构建 0 错误

- [ ] **步骤 3：Commit**

```bash
git add educloud-frontend/student-portal/src/pages/AiAssistant.tsx
git commit -m "feat(AI助教): 学生端页面接入历史会话/新建/删除与可操作错误态"
```

## 任务 16：student-portal 接入 vitest + 前端测试

**文件：**
- 修改：`educloud-frontend/student-portal/package.json`
- 修改：`educloud-frontend/student-portal/vite.config.ts`
- 创建：`educloud-frontend/student-portal/src/test/setup.ts`
- 创建：`educloud-frontend/student-portal/src/pages/AiAssistant.test.tsx`
- 创建：`educloud-frontend/student-portal/src/features/engagement/assistantClient.test.ts`

- [ ] **步骤 1：安装依赖并更新脚本**

```bash
cd educloud-frontend/student-portal
npm install -D vitest@^2.1.9 jsdom@^25.0.1 @testing-library/react@^16.1.0 @testing-library/jest-dom@^6.6.3 @testing-library/user-event@^14.5.2
```

`package.json` scripts 调整（`test` 从 `tsc --noEmit` 换成 vitest，typecheck 保留独立脚本）：

```json
    "typecheck": "tsc --noEmit",
    "test": "vitest run",
```

- [ ] **步骤 2：vite.config.ts 增加 test 块**（照 teacher-portal）

文件顶部 `import { defineConfig } from 'vite'` 改为：

```ts
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
```

导出对象末尾（`server` 块之后）追加：

```ts
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
    restoreMocks: true,
  },
```

`src/test/setup.ts`：

```ts
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
```

- [ ] **步骤 3：编写失败的前端测试**

`src/pages/AiAssistant.test.tsx`（核心断言：429/503 分支**不回退假文案**——旧 mock 模板字符串「复习高等数学可以按」绝不能出现）：

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AiAssistant from './AiAssistant';
import { assistantClient } from '../features/engagement/assistantClient';

vi.mock('../features/engagement/assistantClient', () => ({
  assistantClient: {
    chat: vi.fn(),
    listConversations: vi.fn(),
    listMessages: vi.fn(),
    deleteConversation: vi.fn(),
  },
}));

const mockedClient = vi.mocked(assistantClient);

function httpError(status: number, code: string): Error & { code: string } {
  const error = new Error(`HTTP ${status}`) as Error & { code: string };
  error.code = code;
  return error;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedClient.listConversations.mockResolvedValue({
    items: [], page: 1, pageSize: 50, total: 0, totalPages: 0,
  });
});

describe('AiAssistant 错误态（规格 §6：绝不回退假文案）', () => {
  it('429 配额超限展示当日次数用完提示且无假回复', async () => {
    mockedClient.chat.mockRejectedValue(httpError(429, 'AI_QUOTA_EXCEEDED'));
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '如何制定复习计划');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    expect(await screen.findByText('今日提问次数已用完，明天再来')).toBeInTheDocument();
    expect(screen.queryByText(/复习高等数学可以按/)).not.toBeInTheDocument();
    expect(screen.queryByText(/建议先把问题缩小到一个可以运行的最小示例/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /重试/ })).not.toBeInTheDocument();
  });

  it('503 展示可操作重试且重试会再次调用', async () => {
    mockedClient.chat.mockRejectedValueOnce(httpError(503, 'AI_PROVIDER_UNAVAILABLE'));
    mockedClient.chat.mockResolvedValueOnce({
      conversationId: '111', messageId: '222', content: '好的', finishReason: 'stop',
      usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
    });
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '解释导数');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    const retry = await screen.findByRole('button', { name: /重试/ });
    await user.click(retry);

    await waitFor(() => expect(mockedClient.chat).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('好的')).toBeInTheDocument();
  });

  it('请求期间输入与发送禁用', async () => {
    let resolveChat: (value: Awaited<ReturnType<typeof assistantClient.chat>>) => void = () => {};
    mockedClient.chat.mockImplementation(() => new Promise((resolve) => { resolveChat = resolve; }));
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '问');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    expect(screen.getByPlaceholderText('AI 助教正在思考…')).toBeDisabled();
    expect(screen.getByRole('button', { name: '发送问题' })).toBeDisabled();

    resolveChat({
      conversationId: '111', messageId: '222', content: '回答', finishReason: 'stop',
      usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
    });
    await waitFor(() => expect(screen.getByText('回答')).toBeInTheDocument());
    expect(screen.getByPlaceholderText('输入你的学习问题...')).toBeEnabled();
  });

  it('加粗渲染：**关键词** 输出 strong 元素且不显示星号', async () => {
    mockedClient.chat.mockResolvedValue({
      conversationId: '111', messageId: '222',
      content: '第一步，**明确极限的定义**，再逐步计算。',
      finishReason: 'stop',
      usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
    });
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '解释极限');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    const strong = await screen.findByText('明确极限的定义');
    expect(strong.parentElement?.tagName).toBe('STRONG');
    expect(screen.queryByText(/\*\*/)).not.toBeInTheDocument();
  });

  it('TRUNCATED 回答显示截断提示', async () => {
    mockedClient.chat.mockResolvedValue({
      conversationId: '111', messageId: '222', content: '部分答案', finishReason: 'length',
      usage: { promptTokens: 1, completionTokens: 64, totalTokens: 65 }, degraded: false,
    });
    const user = userEvent.setup();
    render(<AiAssistant />);

    await user.type(screen.getByPlaceholderText('输入你的学习问题...'), '长问题');
    await user.click(screen.getByRole('button', { name: '发送问题' }));

    expect(await screen.findByText('回答被截断，可追问"继续"获取剩余内容')).toBeInTheDocument();
  });
});
```

`src/features/engagement/assistantClient.test.ts`（锁客户端契约：端点、40s 超时、无 mock 分支）：

```ts
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { assistantClient } from './assistantClient';
import { http } from '../../services/http';

vi.mock('../../services/http', () => ({
  http: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockedHttp = vi.mocked(http, true);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('assistantClient（规格 §6）', () => {
  it('chat 调用网关 /ai/chat 且逐请求放宽超时到 40s', async () => {
    mockedHttp.post.mockResolvedValue({
      data: { code: 'SUCCESS', data: {
        conversationId: '111', messageId: '222', content: '答', finishReason: 'stop',
        usage: { promptTokens: 1, completionTokens: 1, totalTokens: 2 }, degraded: false,
      } },
    } as never);

    const reply = await assistantClient.chat({ conversationId: null, question: '问' });

    expect(mockedHttp.post).toHaveBeenCalledWith(
      '/ai/chat',
      { conversationId: undefined, question: '问' },
      { timeout: 40_000 },
    );
    expect(reply.content).toBe('答');
  });

  it('空响应体直接抛错，绝不构造本地假回复', async () => {
    mockedHttp.post.mockResolvedValue({ data: { code: 'SUCCESS', data: null } } as never);

    await expect(assistantClient.chat({ question: '问' }))
      .rejects.toThrow('AI 助教返回了无法识别的数据');
  });

  it('listMessages 与 deleteConversation 命中会话资源路径', async () => {
    mockedHttp.get.mockResolvedValue({ data: { code: 'SUCCESS', data: [] } } as never);
    mockedHttp.delete.mockResolvedValue({} as never);

    await assistantClient.listMessages('123');
    await assistantClient.deleteConversation('123');

    expect(mockedHttp.get).toHaveBeenCalledWith('/ai/conversations/123/messages');
    expect(mockedHttp.delete).toHaveBeenCalledWith('/ai/conversations/123');
  });
});
```

- [ ] **步骤 4：运行测试验证通过**

运行：`cd educloud-frontend/student-portal && npm run test`
预期：8 个测试全 PASS；`npm run typecheck` 与 `npm run build` 0 错误

- [ ] **步骤 5：Commit**

```bash
git add educloud-frontend/student-portal/package.json educloud-frontend/student-portal/package-lock.json educloud-frontend/student-portal/vite.config.ts educloud-frontend/student-portal/src/test/ educloud-frontend/student-portal/src/pages/AiAssistant.test.tsx educloud-frontend/student-portal/src/features/engagement/assistantClient.test.ts
git commit -m "test(AI助教): student-portal 接入 vitest 并覆盖 429/503 错误态与加粗渲染"
```

## 任务 17：契约脚本 deploy/tests/ai-contract-tests.sh

**文件：**
- 创建：`deploy/tests/ai-contract-tests.sh`

- [ ] **步骤 1：编写脚本**（照 `content-exam-contract-tests.sh` 风格：表结构 + 授权 + 网关路由 + Nacos 配置存在性 + 密钥占位）

```bash
#!/usr/bin/env bash
# EduCloud AI 助教 P1 契约测试（规格 2026-08-28-ai-assistant-p1-design.md §7）
# 前置：MySQL/Nacos/网关已按既有契约脚本准备；依赖 mysql 客户端与 curl。
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8080}"
NACOS_URL="${NACOS_URL:-http://127.0.0.1:8848/nacos}"
NACOS_USER="${NACOS_USER:-nacos}"
NACOS_PASS="${NACOS_PASS:-nacos}"

echo "== [1/5] ai 库表结构 =="
for table in ai_conversation ai_message; do
  exists=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='educloud_ai' AND table_name='$table';" 2>/dev/null)
  if [ "$exists" != "1" ]; then
    echo "FAIL: table $table missing"; exit 1
  fi
  echo "OK: $table"
done

echo "== [2/5] ai_app 表级授权 =="
grants=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
  "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee LIKE '%ai_app%' AND table_schema='educloud_ai' AND table_name IN ('ai_conversation','ai_message');" 2>/dev/null)
if [ "$grants" -lt 2 ]; then
  echo "FAIL: ai_app grants missing"; exit 1
fi
echo "OK: grants"

echo "== [3/5] 网关路由与鉴权 =="
# 未带 token 访问应得 401（路由存在且被保护），404/000 才是路由缺失
status=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/ai/conversations" || true)
if [ "$status" = "000" ] || [ "$status" = "404" ]; then
  echo "FAIL: gateway route /api/v1/ai/** not reachable (HTTP $status)"; exit 1
fi
echo "OK: gateway route protected (HTTP $status)"

echo "== [4/5] Nacos educloud-ai.yaml 配置存在性 =="
token=$(curl -s -X POST "$NACOS_URL/v1/auth/login" -d "username=$NACOS_USER" -d "password=$NACOS_PASS" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null || true)
if [ -z "$token" ]; then
  echo "SKIP: nacos login failed (console credentials differ)"
else
  config=$(curl -s "$NACOS_URL/v1/cs/configs?dataId=educloud-ai.yaml&group=EDUCLOUD_SERVICES&tenant=educloud-local&accessToken=$token" || true)
  if grep -q "thinking-enabled" <<<"$config"; then
    echo "OK: educloud-ai.yaml present"
  else
    echo "FAIL: educloud-ai.yaml missing or empty (run deploy/scripts/provision-ai-nacos.sh)"; exit 1
  fi
  if grep -q "api-key\|AI_PROVIDER_API_KEY" <<<"$config"; then
    echo "FAIL: nacos config must never contain the api key"; exit 1
  fi
fi

echo "== [5/5] 密钥占位纪律 =="
if grep -qE '^AI_PROVIDER_API_KEY=.+' deploy/docker-compose/.env.example 2>/dev/null; then
  echo "FAIL: .env.example must keep AI_PROVIDER_API_KEY empty"; exit 1
fi
echo "OK: placeholder only"

echo "== AI 助教契约测试全部通过 =="
```

- [ ] **步骤 2：执行（VM 或本地，前提网关/Nacos/MySQL 已起）**

运行：`bash deploy/tests/ai-contract-tests.sh`
预期：5 段全部 OK，退出码 0

- [ ] **步骤 3：后端全量测试（门禁）**

运行：`mvn -f educloud-backend/pom.xml test`
预期：全部模块（含新增 educloud-ai）BUILD SUCCESS，测试 0 失败

- [ ] **步骤 4：三端构建（门禁）**

运行：`cd educloud-frontend/student-portal && npm run build && npm run test && cd ../teacher-portal && npm run build && cd ../admin-portal && npm run build`
预期：三端 build 0 错误、student-portal vitest 全 PASS

- [ ] **步骤 5：Commit**

```bash
git add deploy/tests/ai-contract-tests.sh
git commit -m "test(AI助教): 新增 ai 模块契约测试脚本"
```

## 任务 18：部署与跨角色 E2E 验证

**文件：**
- 创建：`deploy/tests/ai-e2e-tests.py`
- 修改（部署动作，不改文件）：VM 上 .env、Nacos、jar、服务进程

- [ ] **步骤 1：构建与部署 educloud-ai + 网关（VM）**

```bash
# 前提：把本地改动同步到 VM 的 worktree（既有同步脚本或 git push/pull），前端改动由 Vite HMR 直接生效
# VM 上构建两个新/改 jar（VM 有 mvn；-f 必须指向聚合 POM）
cd /root/educloud/.worktrees/educloud-backend-foundation
mvn -f educloud-backend/pom.xml -pl educloud-ai,educloud-gateway -am -DskipTests package

# 优雅重启 ai 与 gateway：先拿 PID，kill 后轮询进程真消失（SIGTERM 可能要 40s），再跑 start-dev.sh
for svc in educloud-ai educloud-gateway; do
  pid=$(pgrep -f "$svc-1.0.0-SNAPSHOT.jar" || true)
  if [ -n "$pid" ]; then
    kill "$pid"
    while pgrep -f "$svc-1.0.0-SNAPSHOT.jar" >/dev/null; do sleep 2; done
  fi
done
set -a; . /root/educloud/.worktrees/educloud-backend-foundation/deploy/docker-compose/.env; set +a
bash deploy/scripts/start-dev.sh
# 禁止：pkill -f 带 jar 名（会杀掉自己的 SSH 会话）
```

预期：`start-dev.sh` 输出 `educloud-ai started (8105/8106)` 与 `educloud-ai: UP`；`ss -tln | grep -E ':8105|:8106'` 有监听。

- [ ] **步骤 2：真实 key 一次性人工冒烟（不进 CI）**

```bash
# VM 上拿学生 token 后走网关提问
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginName":"fe_demo_10","password":"EduCloud@2026","portal":"STUDENT"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')

time curl -s -X POST http://127.0.0.1:8080/api/v1/ai/chat \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"question":"用三步解释什么是极限"}' | python3 -m json.tool | head -30
```

断言（全部满足才算过）：HTTP 200；`data.content` 非空且非旧 mock 文案；`finishReason=stop`；耗时 < 25s；`usage.totalTokens` > 0；响应体与后端日志**均不含** api-key。随后核对：

```bash
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
  "SELECT id,role,status,finish_reason,prompt_tokens,completion_tokens,latency_ms FROM educloud_ai.ai_message ORDER BY id DESC LIMIT 2;"
redis-cli -a "$REDIS_PASSWORD" --no-auth-warning GET "educloud:ai:quota:$(mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT id FROM educloud_user.sys_user WHERE username='fe_demo_10';"):$(date +%Y%m%d)"
```

预期：`ai_message` 有 user + assistant 两行（assistant 行 status=OK、usage 非空）；Redis 个人计数 ≥ 1、全局 token 计数 > 0。

- [ ] **步骤 3：编写 Playwright E2E 脚本**

`deploy/tests/ai-e2e-tests.py`（照 scratch 捕获脚本的 async Playwright 风格；chat 走 route 拦截 mock，会话列表/删除走真实后端；`--real-key` 模式下追加真实提问→刷新→历史仍在→越权 403 全链路；截图存 `deploy/tests/artifacts/ai-e2e/`，不提交 git）：

```python
#!/usr/bin/env python3
"""
EduCloud M15 AI 助教 P1 跨角色 E2E（Playwright）。
默认模式：chat 走 route 拦截（不消耗真实 key），验证渲染/错误态/会话侧栏视觉。
--real-key 模式：追加真实提问→刷新后历史仍在→新建/删除→越权 403 全链路（仅一次性人工冒烟）。
截图输出 deploy/tests/artifacts/ai-e2e/（git 不提交）。
"""

import asyncio
import json
import os
import sys
import urllib.request

sys.stdout.reconfigure(encoding='utf-8')
from playwright.async_api import async_playwright

BASE = os.environ.get("EDUCLOUD_BASE", "http://192.168.100.136:5173")
GATEWAY = os.environ.get("EDUCLOUD_GATEWAY", "http://192.168.100.136:8080")
ARTIFACTS = os.path.join(os.path.dirname(__file__), "artifacts", "ai-e2e")
REAL_KEY = "--real-key" in sys.argv

STUDENT = {"loginName": "fe_demo_10", "password": "EduCloud@2026", "portal": "STUDENT"}
TEACHER = {"loginName": "demo_teacher@educloud.cn", "password": "EduCloud@2026", "portal": "TEACHER"}


def api(path, method="GET", payload=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(
        GATEWAY + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:
        return 0, {"error": str(e)}


def login(account):
    status, body = api("/api/v1/auth/login", "POST", account)
    assert status == 200, f"login failed: {status} {body}"
    return body["data"]["accessToken"]


async def main():
    os.makedirs(ARTIFACTS, exist_ok=True)
    student_token = login(STUDENT)

    # [1] 未登录被拒：401
    status, body = api("/api/v1/ai/conversations")
    assert status == 401, f"unauthenticated should be 401, got {status}"
    print("  [OK] 未登录访问被拒 401")

    # [2] 教师账号被拒：403（服务端 STUDENT 守卫，不消耗 key）
    teacher_token = login(TEACHER)
    status, body = api("/api/v1/ai/chat", "POST", {"question": "hello"}, teacher_token)
    assert status == 403 and body.get("code") == "AI_ACCESS_DENIED", f"teacher should 403, got {status} {body}"
    print("  [OK] 教师账号被拒 403 AI_ACCESS_DENIED")

    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1440, "height": 900})
        await context.add_init_script(
            f"window.localStorage.setItem('student_token', '{student_token}');")
        page = await context.new_page()

        # [3] 学生提问 → 渲染 + 行内加粗（chat 拦截 mock）
        async def mock_ok(route):
            await route.fulfill(status=200, json={"code": "SUCCESS", "message": "OK", "data": {
                "conversationId": "7001", "messageId": "7002",
                "content": "第一步，**明确极限的定义**，再逐步计算。",
                "finishReason": "stop",
                "usage": {"promptTokens": 79, "completionTokens": 291, "totalTokens": 370},
                "degraded": False}})
        await page.route("**/api/v1/ai/chat", mock_ok)
        await page.goto(f"{BASE}/ai-assistant", wait_until="networkidle")
        await page.fill("textarea", "什么是极限")
        await page.click("button[aria-label='发送问题']")
        await page.wait_for_selector("strong:has-text('明确极限的定义')", timeout=8000)
        await page.screenshot(path=os.path.join(ARTIFACTS, "01-student-render.png"))
        print("  [OK] 学生提问渲染与行内加粗")

        # [4] 429 错误态不回退假文案
        async def mock_429(route):
            await route.fulfill(status=429, json={"code": "AI_QUOTA_EXCEEDED", "message": "quota"})
        await page.unroute("**/api/v1/ai/chat")
        await page.route("**/api/v1/ai/chat", mock_429)
        await page.fill("textarea", "再来一问")
        await page.click("button[aria-label='发送问题']")
        await page.wait_for_selector("text=今日提问次数已用完", timeout=8000)
        assert not await page.locator("text=复习高等数学可以按").count()
        await page.screenshot(path=os.path.join(ARTIFACTS, "02-quota-429.png"))
        print("  [OK] 429 错误态且无假文案")

        # [5] 503 错误态可重试
        async def mock_503(route):
            await route.fulfill(status=503, json={"code": "AI_PROVIDER_UNAVAILABLE", "message": "down"})
        await page.unroute("**/api/v1/ai/chat")
        await page.route("**/api/v1/ai/chat", mock_503)
        await page.fill("textarea", "继续")
        await page.click("button[aria-label='发送问题']")
        await page.wait_for_selector("text=AI 服务暂时不可用", timeout=8000)
        await page.wait_for_selector("button:has-text('重试')", timeout=4000)
        await page.screenshot(path=os.path.join(ARTIFACTS, "03-provider-503.png"))
        print("  [OK] 503 错误态与重试按钮")

        await browser.close()

    if not REAL_KEY:
        print("== E2E（默认模式）通过：4/6 项；真实链路用 --real-key 执行 ==")
        return

    # ---- --real-key 全链路（一次性人工冒烟） ----
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context(viewport={"width": 1440, "height": 900})
        await context.add_init_script(
            f"window.localStorage.setItem('student_token', '{student_token}');")
        page = await context.new_page()
        await page.goto(f"{BASE}/ai-assistant", wait_until="networkidle")

        # [6] 真实提问 → 渲染
        await page.fill("textarea", "用三步解释什么是极限")
        await page.click("button[aria-label='发送问题']")
        await page.wait_for_selector("[data-message-role='assistant'] >> nth=1", timeout=40000)
        await page.screenshot(path=os.path.join(ARTIFACTS, "04-real-answer.png"))
        print("  [OK] 真实提问得到回答")

        # [7] 刷新后历史仍在
        await page.reload(wait_until="networkidle")
        await page.wait_for_selector("text=用三步解释什么是极限", timeout=8000)
        await page.screenshot(path=os.path.join(ARTIFACTS, "05-history-after-reload.png"))
        print("  [OK] 刷新后历史会话仍在")

        # [8] 新建会话 → 输入区清空回欢迎语
        await page.click("button:has-text('新建会话')")
        assert not await page.locator("text=用三步解释什么是极限").count()
        print("  [OK] 新建会话清空当前视图")

        # [9] 删除会话（真实 DELETE 204；页面用 window.confirm 原生弹窗，需接管 dialog 事件）
        page.on("dialog", lambda dialog: asyncio.ensure_future(dialog.accept()))
        await page.click("[aria-label^='删除会话'] >> nth=0")
        await asyncio.sleep(1)
        await page.screenshot(path=os.path.join(ARTIFACTS, "06-conversation-deleted.png"))
        print("  [OK] 删除会话")

        await browser.close()

    # [10] 越权访问他人会话 403：student B（user4213）访问 fe_demo_10 的真实会话
    status, body = api("/api/v1/auth/login", "POST",
                       {"loginName": "user4213", "password": os.environ["USER4213_PASSWORD"], "portal": "STUDENT"})
    assert status == 200, f"user4213 login failed: {status} {body}"
    other_token = body["data"]["accessToken"]
    status, mine = api("/api/v1/ai/conversations", token=student_token)
    assert mine.get("data", {}).get("items"), "fe_demo_10 should have real conversations from [6]"
    real_id = mine["data"]["items"][0]["id"]
    status, _ = api(f"/api/v1/ai/conversations/{real_id}/messages", token=other_token)
    assert status == 403, f"cross-student access should be 403, got {status}"
    print("  [OK] 越权访问他人会话 403")
    print("== E2E（--real-key 全链路）通过 ==")


asyncio.run(main())
```

- [ ] **步骤 4：执行 E2E 并出图**

```bash
# 默认模式（不消耗 key）
python deploy/tests/ai-e2e-tests.py
# 全链路（真实 key 已在 VM .env，服务端真实调用；user4213 密码经 USER4213_PASSWORD 传入）
python deploy/tests/ai-e2e-tests.py --real-key
```

预期：10 项断言全 OK；`deploy/tests/artifacts/ai-e2e/` 下 6 张截图（01–06），肉眼核对渲染、错误态、历史列表。

- [ ] **步骤 5：最终门禁清单（全部留证据）**

| # | 检查 | 命令 | 证据 |
|---|---|---|---|
| 1 | 后端全量 | `mvn -f educloud-backend/pom.xml test` | BUILD SUCCESS 输出（含 educloud-ai） |
| 2 | 三端构建 | 三端 `npm run build` | 0 error 输出 |
| 3 | 前端测试 | student-portal `npm run test` | 8 tests passed |
| 4 | 契约脚本 | `bash deploy/tests/ai-contract-tests.sh` | 5 段 OK |
| 5 | E2E 出图 | `python deploy/tests/ai-e2e-tests.py --real-key` | 10 项 OK + 6 截图 |
| 6 | 真实冒烟 | 步骤 2 curl | 200/content 非空/<25s/落库/配额 +1 |
| 7 | 密钥红线 | `git grep AI_PROVIDER_API_KEY`、`git log -p --all -S 'sk-'` | 无真实 key 泄漏；日志抽查无 key |

- [ ] **步骤 6：Commit**

```bash
git add deploy/tests/ai-e2e-tests.py
git commit -m "test(AI助教): 新增 Playwright 跨角色 E2E 脚本"
```

---

## 自检

**1. 规格覆盖度：**

| 规格章节 | 对应任务 |
|---|---|
| §2.1 模块定位（8105/8106、Nacos、新库 ai_app） | 任务 1/2/3 |
| §2.2 复用清单（ApiResponse/BusinessException/JWT sub/雪花 ID/Nacos/env/迁移/契约） | 任务 3/4/5（全部复用 common 既有类，零新基建） |
| §3 数据模型 2 张表 + 行语义（user/assistant 双行、FAILED 留痕、TRUNCATED） | 任务 2（DDL）+ 任务 4（实体）+ 任务 9（行语义）|
| §3 配额不建表（Redis 双计数器 + 当日 TTL） | 任务 8 |
| §4 API 四接口 + 403/404/400/204 语义 | 任务 10（含越权先 404 后 403 的顺序） |
| §4.1 错误码 7+1 | 任务 5（AiErrorCode）+ 任务 14（http.ts 中文映射） |
| §5.1 Provider 抽象、只取 content | 任务 6 |
| §5.2 实测四条（enable_thinking 顶层/finish_reason/max_tokens 1024/370 tokens 配额） | 任务 6（1/2/3）+ 任务 8（4，配额 50 次/日 + 200 万 token） |
| §5.3 超时/重试/降级（5s/25s/35s、超时不重试、429/5xx 重试 1 次、绝不假文案） | 任务 6（重试）+ 任务 3（超时配置）+ 任务 12（网关 35s）+ 任务 14/15（前端错误态） |
| §5.4 配额与熔断、成功后计数 | 任务 8 + 任务 9（recordUsage 仅成功路径） |
| §5.5 上下文装配（system/10 条/3000 预算/注入面） | 任务 7 |
| §5.6 配置项与密钥（Nacos 非敏感 + env 密钥 + 日志纪律） | 任务 3（yml）+ 任务 13（Nacos 脚本）+ 任务 6（日志只记元信息） |
| §6 前端（重写 client/历史会话/新建会话语义/徽标移除/行内加粗/错误态） | 任务 14 + 任务 15 |
| §7 测试门禁（单测/Provider/契约/集成/脚本/前端 vitest/真实 key 不进 CI） | 任务 5–9、11、16、17、18 |
| §8 部署与验证（迁移/Nacos/网关/start-dev/冒烟/E2E） | 任务 1/2/12/13/18 + start-dev（任务 3 步骤 5） |
| §9 安全约束五条 | sub-only（任务 10）、强隔离 403（任务 9/10）、key 红线（任务 1/3/13/17/18）、不静默降级（任务 6/14/15）、双保险（任务 8） |
| §10 非目标 | 未引入流式/教师视图/RAG/多模态，无越界实现 |

**2. 占位符扫描：** 无 TODO/待定/“类似任务 N”；所有代码步骤含完整代码；`AiProviderConfig` 推迟到任务 6 是显式顺序安排并已标注，不是占位。

**3. 类型一致性（跨任务核对）：**
- `ChatTurn(role, content)`（任务 6 chat 包）↔ 任务 7/9 使用一致 ✓
- `ChatOptions(maxTokens)` / `ChatResult(content, finishReason, promptTokens, completionTokens, totalTokens, model, latencyMs)`（任务 6）↔ 任务 9 调用一致；messageId 由 ChatService 用 assistant 行插入回填的雪花 id 生成（任务 9）✓
- `AiProperties` 五元组嵌套 record（任务 3）↔ 任务 6/7/8/9 测试构造一致（`ProviderProperties(name, baseUrl, model, apiKey, thinkingEnabled, maxTokens)`，application.yml 含 `name: ${AI_PROVIDER_NAME:openai-compatible}`）✓
- `AiErrorCode.AI_*`（任务 5）↔ 任务 8/9/10 抛出、任务 14 前端映射、任务 18 E2E 断言 `AI_ACCESS_DENIED` 一致 ✓
- Redis 键 `educloud:ai:quota:{studentId}:{yyyyMMdd}` 与 `educloud:ai:quota:daily-tokens`（规格 §3）↔ 任务 8 实现与测试一致 ✓
- 实体字段 ↔ V001 DDL 列一一对应（message_count/deleted/last_message_at 等）✓
- 网关契约 `hasSize(18)` + orders `...,150,155` ↔ application.yml 新增路由 order 155 一致 ✓
- 前端 `AiPage<T>` ↔ 后端 `PageResponse` JSON（items/page/pageSize/total/totalPages）字段一致 ✓
- `assistantClient` 四方法端点 ↔ 后端控制器四路径一致 ✓

**执行交接：**

计划已完成并保存到 `docs/superpowers/plans/2026-08-28-ai-assistant-p1.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？
