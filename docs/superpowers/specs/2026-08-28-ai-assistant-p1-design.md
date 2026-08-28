# EduCloud AI 助教 P1 设计规格（新建 educloud-ai 模块）

日期：2026-08-28
范围：M15 AI 助教第一期（地基 + 真答）
关联：`docs/HANDOVER.md`、`docs/superpowers/specs/2026-08-28-educloud-exam-design.md`

## 1. 概述

学生端顶部导航的「AI 助教」目前**只有前端、没有服务端**：`pages/AiAssistant.tsx`（191 行）UI 完整，但 `features/engagement/assistantClient.ts` 在未配置 `VITE_AI_ASSISTANT_ENDPOINT` 时返回 4 段按关键词匹配的写死文案，配置时则让浏览器直连外部模型地址。后端没有任何 AI 模块、网关路由、密钥管理、鉴权、配额与会话存储。

P1 的目标是：**把 AI 助教变成真的能用，同时把后续两期要用的治理地基一次建好**——服务端接入 OpenAI 兼容模型、身份只认 JWT、调用留痕、配额熔断、会话持久化，并彻底移除前端假回复。

### 1.1 已确认决策（与用户逐项确认）

| # | 决策 | 内容 |
|---|---|---|
| 1 | 能力范围 | A（真能答）+ B（懂学生的上下文）+ C（学习流程工具）组合，**拆成三期分别交付**，每期独立走规格→计划→实现 |
| 2 | 模型协议 | OpenAI 兼容接口（base-url + model + api-key 三参数可配，不锁死单一厂商） |
| 3 | 流式 | **P1 非流式**，但接口与响应结构按流式兼容设计；P1 收到 `stream=true` 明确拒绝而非静默降级 |
| 4 | 服务边界 | **新建独立模块 `educloud-ai`**（而非并入 content）：独立的库、密钥、配额、审计与会话存储 |
| 5 | 供应商 | 硅基流动 `https://api.siliconflow.cn/v1`，模型 `Qwen/Qwen3.6-27B` |

### 1.2 分期路线

| 阶段 | 交付 | 依赖 |
|---|---|---|
| **P1（本规格）** | educloud-ai 服务、网关路由、鉴权、provider 抽象、超时/重试/降级、配额、审计、会话与消息落库；前端接真接口 + 历史会话 + 错误态 | 无 |
| P2 | 「讲解我的错题」：拉取该学生作业扣分点与考试错题（`exam_attempt.answers_json`）装配上下文 | P1 的上下文注入通道 |
| P3 | 全量学习画像（报名课程、章节进度、时长、证书）+ 回答引用溯源；教师端高频问题视图 | P2 的装配框架 |

## 2. 模块总览

### 2.1 服务定位

- 新模块 `educloud-backend/educloud-ai`，业务端口 **8105**、管理端口 **8106**（沿用双端口约定；实施前先在 VM 确认端口空闲）
- 注册 Nacos，经网关暴露 `/api/v1/ai/**`
- 数据库：新库 `educloud_ai`，应用账号 `ai_app`（照 `content_app` 授权模式）
- **P1 不读取任何其他域的数据**，纯问答 + 会话历史。这是选独立模块的关键收益：P2 才引入 content 内部接口

### 2.2 复用清单（零新基建）

| 能力 | 复用 |
|---|---|
| 统一响应体 / 错误码 | `common` 的 `ApiResponse`、`BusinessException`、`ErrorCode` 约定 |
| 身份 | 网关 JWT + `common` 的安全上下文（**只认 `sub`，不接受前端传 studentId**） |
| 雪花 ID 字符串化 | `common` 的 ID 生成与序列化约定 |
| 配置中心 | Nacos（非敏感项）+ `deploy/docker-compose/.env`（密钥） |
| 迁移 | `deploy/sql/ai/` + `run-migrations.sh` |
| 契约测试 | `deploy/tests/*.sh` 风格 + Java 侧响应字段契约测试 |

## 3. 数据模型（`educloud_ai` 库，2 张表）

### 3.1 `ai_conversation` 会话表

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 雪花 ID，对外字符串化 |
| `student_id` | BIGINT NOT NULL | 归属学生，取自 JWT |
| `title` | VARCHAR(120) | 首条学生提问截断生成 |
| `message_count` | INT | 消息数（含软删） |
| `last_message_at` | DATETIME(3) | 列表排序依据 |
| `deleted` | TINYINT | 软删标记 |
| `created_at` / `updated_at` | DATETIME(3) | |

索引：`idx_student_last (student_id, last_message_at)`。

### 3.2 `ai_message` 消息表（同时充当调用审计）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `conversation_id` | BIGINT NOT NULL | |
| `role` | VARCHAR(16) | `user` / `assistant` |
| `content` | TEXT | 答案正文（只存 `content`，不存 `reasoning_content`） |
| `provider` / `model` | VARCHAR(64) | 实际调用的供应商与模型 |
| `prompt_tokens` / `completion_tokens` | INT | usage，成本核算依据 |
| `latency_ms` | INT | 外部调用耗时 |
| `finish_reason` | VARCHAR(16) | `stop` / `length` / `error` |
| `status` | VARCHAR(16) | `OK` / `TRUNCATED` / `FAILED` |
| `error_code` | VARCHAR(64) | 失败时的错误码 |
| `created_at` | DATETIME(3) | |

索引：`idx_conversation_created (conversation_id, created_at)`。

行语义约定：

- 一次提问写两行——`role=user` 行存学生原文，`provider/model/latency_ms/finish_reason` 等调用列置 `NULL`、`status=OK`；`role=assistant` 行存答案与调用元信息
- 外部调用失败时**仍然写入 assistant 行**，`content` 为空、`status=FAILED`、`error_code` 记错误码，保证审计完整
- `status=TRUNCATED` 表示 `finish_reason=length`，内容有效但被截断

**配额不建表**：Redis 计数器 `educloud:ai:quota:{studentId}:{yyyyMMdd}`，TTL 到当日 24 点。全局熔断计数 `educloud:ai:quota:daily-tokens`。

## 4. API 设计（`/api/v1/ai/**`，需 STUDENT 角色）

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| POST | `/api/v1/ai/chat` | `{conversationId?, question, stream?}` | `{conversationId, messageId, content, finishReason, usage, degraded}` |
| GET | `/api/v1/ai/conversations` | `?page=&size=` | 分页会话列表，按 `last_message_at` 倒序 |
| GET | `/api/v1/ai/conversations/{id}/messages` | — | 该会话消息，时间升序 |
| DELETE | `/api/v1/ai/conversations/{id}` | — | 软删 |

约定：

- `conversationId` 省略时服务端新建会话并返回其 id
- 访问他人会话一律 `403 AI_CONVERSATION_NOT_OWNED`
- `stream=true` 返回 `400 AI_STREAM_NOT_SUPPORTED`（**不静默按非流式处理**，避免前端误判流式已生效）
- `question` 长度上限 1000 字符，超出 `400 AI_QUESTION_TOO_LONG`
- `DELETE` 成功返回 `204`，无响应体；重复删除已软删的会话返回 `404 AI_CONVERSATION_NOT_FOUND`

### 4.1 错误码

| 码 | HTTP | 含义 |
|---|---|---|
| `AI_PROVIDER_UNAVAILABLE` | 503 | 外部模型不可用/超时 |
| `AI_QUOTA_EXCEEDED` | 429 | 超过当日个人次数上限 |
| `AI_GLOBAL_BUDGET_EXCEEDED` | 429 | 触发全局 token 熔断 |
| `AI_CONVERSATION_NOT_FOUND` | 404 | 会话不存在或已删除 |
| `AI_CONVERSATION_NOT_OWNED` | 403 | 越权访问他人会话 |
| `AI_STREAM_NOT_SUPPORTED` | 400 | P1 不支持流式 |
| `AI_QUESTION_TOO_LONG` | 400 | 提问超长 |

## 5. LLM 调用链与治理

### 5.1 Provider 抽象

接口 `ChatProvider { ChatResult chat(List<Message> messages, ChatOptions options); }`，P1 唯一实现 `OpenAiCompatibleProvider`。响应解析同时读取 `content` 与 `reasoning_content`，但**只把 `content` 作为答案落库与返回**。

### 5.2 实测依据（2026-08-28 VM 与本地直连同一 key/模型）

| 请求写法 | 延迟 | content | reasoning | completion tokens |
|---|---|---|---|---|
| 顶层 `enable_thinking:false` | 1.3s | 77 字 | 0 | 36 |
| `thinking:{"type":"disabled"}` + `max_tokens=512` | 5.1s | 64 字 | 0 | 27 |
| 真实助教提问（关思考） | 10.5s | 629 字 | 0 | 291 |
| 真实助教提问（**默认开思考**） | 48.4s | 1597 字 | 4460 字 | 2037 |
| `max_tokens=64`（开思考） | 8.1s | **0 字** | 237 字 | 64（`finish_reason=length`） |

由此定死四条实现要求：

1. **必须显式关思考**，默认参数写顶层 `enable_thinking:false`（官方文档口径）。`chat_template_kwargs.enable_thinking` 实测**无效**（仍吐 3355 字推理），不得使用
2. **不能只看 HTTP 状态码**：`max_tokens` 过小时 HTTP 200 而 `content` 为空，必须结合 `finish_reason` 判断
3. 默认 `max_tokens=1024`；`finish_reason=length` 时消息照常落库但标记 `status=TRUNCATED`，前端提示"回答被截断，可追问继续"
4. 成本基准：一次典型问答约 370 tokens（入 79 / 出 291）

### 5.3 超时、重试与降级

- 连接超时 5s，读超时 25s；网关该路由 response-timeout 35s
- **超时不重试**（超时意味着模型正在长时间生成，重试只会再等 25 秒并翻倍成本）；仅对连接失败与**上游**返回的 429/5xx 重试 1 次，退避 1s。本平台自身的 `429 AI_QUOTA_EXCEEDED` 不在此列，直接返回不重试
- 外部不可用返回 `503 AI_PROVIDER_UNAVAILABLE`，**绝不回退假文案**。同时删除前端 `assistantClient` 的 4 段关键词模板与 `VITE_AI_ASSISTANT_ENDPOINT` 直连逻辑（模型地址与密钥不得出现在前端）
- 响应保留 `degraded` 字段，P1 恒为 `false`

### 5.4 配额与熔断

- 每人每日 50 次（≈1.85 万 tokens/人/日），超出 `429 AI_QUOTA_EXCEEDED`
- 全局每日 token 上限熔断（默认 200 万），触发返回 `429 AI_GLOBAL_BUDGET_EXCEEDED`，防 key 被盗刷
- 计数在**调用成功后**累加；失败调用不计次但照常落 `ai_message`（`status=FAILED`）留痕

### 5.5 上下文装配

- system prompt 固定写在服务端（含"用中文分步讲解、纯文本编号、不要使用 markdown 标记、不得编造平台数据"）
- 学生输入只进 `user` 角色，不拼接进 system → 无提示注入越权面（P1 无工具调用与数据读取）
- 发给模型的 messages = system + 该会话**最近 10 条历史消息** + 本次提问（共最多 11 条），按 prompt token 预算 ≤ 3000 裁剪，超出从最旧的历史消息开始丢弃（system 与本次提问不裁）

### 5.6 配置项与密钥

| 配置 | 位置 |
|---|---|
| `ai.provider`、`ai.base-url`、`ai.model`、`ai.thinking-enabled`、`ai.max-tokens`、`ai.timeout.*`、`ai.quota.*`、`ai.context.*` | Nacos 配置中心 |
| `AI_PROVIDER_API_KEY` | VM 的 `deploy/docker-compose/.env`，仓库仅保留 `.env.example` 占位 |

密钥**不进代码、不进测试、不进日志、不进 git**。日志只记长度、消息条数、usage、latency、finish_reason。

## 6. 前端改动（`student-portal`）

- `assistantClient` 重写为调用本站 `/api/v1/ai/chat`，走网关带 JWT；移除 mock 与外部直连
- 会话归属服务端：前端只保存 `conversationId`；左栏新增「历史会话」列表（`GET /conversations`），支持切换与删除
- 「清空会话」改为「新建会话」语义（历史已落库，不能假装删除）
- 顶部「演示模式 / 已连接服务」徽标移除
- 输出渲染：P1 不引入 react-markdown（避免新增 XSS 面），改为 system prompt 约束纯文本 + 前端约 15 行的行内加粗渲染（React 文本节点天然转义）
- 错误态可操作：429 → "今日提问次数已用完，明天再来"；503 → "AI 服务暂时不可用，点击重试"；请求期间禁用输入与发送

## 7. 测试与门禁

| 层级 | 内容 |
|---|---|
| 单元测试 | 上下文裁剪（10 条 / 3000 token）、配额计数与熔断、`finish_reason=length` → TRUNCATED、超长提问拒绝 |
| Provider 测试 | MockRestServiceServer 断言：请求体含 `enable_thinking:false`、只取 `content`、超时不重试、429 重试 1 次、失败落 `status=FAILED` |
| 响应契约测试 | Java 侧锁定 `/api/v1/ai/chat` 字段名与类型（同 `ExamApiContractTest` 路子） |
| 集成测试 | Testcontainers：会话落库、消息升序、软删、越权访问 403 |
| 脚本契约 | `deploy/tests/ai-contract-tests.sh`：表结构 + 网关路由 + Nacos 配置存在性 |
| 前端测试 | **前置工作**：student-portal 当前 `test` 脚本仅为 `tsc --noEmit`，无 vitest；P1 顺带照 `teacher-portal` 的配置接入 vitest + testing-library，然后覆盖 429/503 分支**不回退假文案**。若该前置接入被砍，这些分支改由第 8 节的 Playwright E2E 覆盖，不可留空 |
| 真实 key | **绝不进测试代码与 CI**；仅在 VM 部署后做一次性人工冒烟 |

## 8. 部署与验证（VM 192.168.100.136）

1. `deploy/sql/ai/V001__ai.sql` 建库建表 + `ai_app` 授权，执行 `run-migrations.sh`
2. Nacos 新增 `educloud-ai` 配置；`.env` 注入 `AI_PROVIDER_API_KEY`
3. 网关新增 `/api/v1/ai/**` 路由与 35s response-timeout
4. `start-dev.sh` 增加 educloud-ai 启动项（8105/8106）
5. 冒烟：curl 走网关发一次真实提问 → 断言 200、`content` 非空、延迟 < 25s、`ai_message` 落库、配额 +1
6. 浏览器 E2E（Playwright，跨角色对照）：学生提问 → 渲染 → **刷新页面历史仍在** → 新建/删除会话 → 路由拦截模拟 429/503 的错误态 → 未登录与教师账号访问被拒

## 9. 安全约束（不可回退项）

1. 身份只取 JWT `sub`，任何接口不接受前端传入的 `studentId`
2. 会话与消息按 `student_id` 强隔离，越权一律 403
3. api-key 只在服务端环境变量中，不出现在前端产物、日志、测试与 git
4. 外部调用失败不得静默降级为假数据（与考试模块同一纪律）
5. 配额 + 全局熔断双保险，防单点滥用与 key 盗刷

## 10. 非目标（P1 明确不做）

- 流式输出（接口预留，P1 拒绝）
- 教师端任何视图
- 错题讲解、学习画像、引用溯源（P2/P3）
- 向量检索/RAG 知识库
- 图片与多模态输入
- 模型微调与自选模型 UI
