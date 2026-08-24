# EduCloud 阶段交付与代码交接文档（M06 完结 · 面向下一任接力人员）

> **编制日期**：2026-08-24  
> **代码基线**：`main` 分支（Git Commit: `9d530f6`）  
> **当前状态**：M01～M06 全部微服务与前端三端已闭环，全链路在 VM 虚拟机中健康运行（`UP`）。

---

## 1. 项目概况与代码仓库

### 1.1 仓库与分支
- **GitHub 远程仓库**：`https://github.com/ljccc2025/educloud.git`
- **默认开发分支**：`main`
- **本地 Worktree 路径**：`d:\microservice\.worktrees\educloud-backend-foundation`
- **代码结构**：
  ```text
  educloud/
  ├── deploy/                  # 部署脚本、Docker Compose 与各微服务 SQL 初始化脚本
  │   ├── docker-compose/      # 中间件编排（MySQL、Redis、RabbitMQ、Nacos、MinIO、ES）
  │   ├── scripts/             # 一键启动脚本 start-dev.sh 与工具脚本
  │   └── sql/                 # user, file, course, content 等逻辑库的 Flyway/DDL 脚本
  ├── docs/superpowers/specs/  # 完整的架构设计规格、执行契约与模块设计文档
  ├── educloud-backend/        # Java 17 + Spring Boot 3.2.5 微服务聚合工程
  │   ├── educloud-common      # 公共底座（Result 信封、Snowflake、异常拦截、枚举）
  │   ├── educloud-gateway     # API 安全网关（端口 8080/8081）
  │   ├── educloud-user        # 用户与认证中心（端口 8082/8083）
  │   ├── educloud-file        # 文件与对象存储中心（端口 8087/8088）
  │   ├── educloud-course      # 课程与分类中心（端口 8089/8090）
  │   └── educloud-content     # 内容与学习中心 [M06 交付]（端口 8085/8086）
  └── educloud-frontend/       # React 18 + TypeScript + Vite + Tailwind 前端三端
      ├── student-portal       # 学生端门户（端口 5173）
      ├── teacher-portal       # 教师端工作台（端口 5174）
      └── admin-portal         # 运营管理后台（端口 5175）
  ```

---

## 2. 本次 M06 模块交付成果总结

### 2.1 后端：`educloud-content`（内容与学习微服务）
1. **章节大纲与多类型课件管理**：
   - 实现了课程章节树 CRUD 与课件管理（支持 `VIDEO` 视频、`DOCUMENT` 讲义/PDF、`EXTERNAL` 外部链接）；
   - 实现了免费试看标记（`freePreview`）与时长元数据维护。
2. **Presigned 安全播放与下载链路**：
   - 内部 Feign 调用 `educloud-file` 获取防盗链临时签名 URL（1 小时有效期）。
3. **防作弊学习进度心跳与完成度聚合**：
   - 上报端点 `POST /api/v1/learning/progress` 校验播放断点防篡改（`positionSeconds <= durationSeconds`）；
   - 自动聚合计算学员课程完成度，支持多端实时进度同步。
4. **高可用可观测性与健康检查**：
   - 实现了 `HealthIndicatorConfig`，聚合 MySQL、Redis、RabbitMQ、Nacos 四大依赖的 `readiness` 就绪检查；
   - 注册了 Snowflake `IdentifierConfig` 标识符生成 Bean。

### 2.2 前端三端交互升级
1. **学生端（`student-portal`）**：
   - **经典居中双栏卡片模式**：解决大屏贴边与巨型黑底，采用 `max-w-7xl` 1280px 黄金居中与 16:9 比例播放器；
   - **全链路真实数据对接**：对接真实章节树接口，支持上下节切换、视频/PDF 预览及防抖并发锁。
2. **管理端（`admin-portal`）**：
   - 修复了表格「状态」列（`已支付`、`待支付`、`已退款`）徽标被强制拆行的问题，添加全局 `whitespace-nowrap` 与列宽保护。
3. **教师端（`teacher-portal`）**：
   - 完善内容管理章节课件管理联动。

---

## 3. 运行环境与运维指令

### 3.1 部署环境信息
- **虚拟机 OS**：Rocky Linux 8.9（IP: `192.168.100.136`，SSH root / 密码 `1`）
- **JDK 版本**：Java 17（OpenJDK 17）
- **Node.js 版本**：Node 20+

### 3.2 常用运维与服务启动命令
- **启动/检查所有微服务与前端**：
  ```bash
  cd /root/educloud/.worktrees/educloud-backend-foundation
  bash deploy/scripts/start-dev.sh
  ```
- **健康就绪状态一键探测**：
  ```bash
  curl -s http://127.0.0.1:8081/actuator/health/readiness # Gateway
  curl -s http://127.0.0.1:8083/actuator/health/readiness # User
  curl -s http://127.0.0.1:8088/actuator/health/readiness # File
  curl -s http://127.0.0.1:8090/actuator/health/readiness # Course
  curl -s http://127.0.0.1:8086/actuator/health/readiness # Content (M06)
  ```
- **日志查看**：
  ```bash
  tail -f /tmp/educloud-live/content.log
  tail -f /tmp/educloud-live/gateway.log
  ```

---

## 4. 测试凭据与访问地址

| 门户 / 服务 | 访问地址 | 预置测试账号 / 密码 | 角色身份 |
| :--- | :--- | :--- | :--- |
| **学生端** | `http://192.168.100.136:5173` | `fe_demo_10` / `FeDemo@2026` | 普通学生（ID: `2091648316809035778`） |
| **教师端** | `http://192.168.100.136:5174` | `demo_teacher@educloud.cn` / `EduCloud@2026` | 讲师（ID: `9000000000000000001`） |
| **管理端** | `http://192.168.100.136:5175` | `demo_admin` / `EduCloud@2026` | 超级管理员（ID: `9000000000000000002`） |
| **测试课程学习页** | `http://192.168.100.136:5173/learn/9000000000000000110` | — | 前端开发入门实战 |

---

## 5. 核心架构契约与避坑指南（⚠️ 必读）

1. **Snowflake ID 严格字符串原则**：
   - 所有的业务 ID（`userId`, `courseId`, `chapterId`, `coursewareId`, `orderId` 等）均为 64 位雪花算法生成，在 Java 中为 `Long`，在 JSON/API/前端 TypeScript 中 **必须一律声明并传递为 `string` 类型**，**绝对禁止在前端使用 `Number(id)`**（会导致高位精度截断）。
2. **API 统一响应信封**：
   - 所有接口返回规范为 `Result<T>` 信封：`{ "code": 200, "message": "...", "data": T, "traceId": "..." }`，前端通过 `http.ts` 拦截器统一解包。
3. **数据库字符编码**：
   - 所有新增表及 SQL 脚本必须显式声明 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`，并在连接串中带上 `useUnicode=true&characterEncoding=utf-8`。

---

## 6. 下一阶段接力路线图：`M07 educloud-order`（订单中心）

接力人员可直接开始 **M07** 模块的开发，核心范围与要点如下：

1. **模块定位**：`educloud-backend/educloud-order`（端口业务 8091 / 管理 8092）
2. **核心业务流**：
   - 购物车管理与多课合并结算；
   - 订单生成（基于 Redis 分布式防重 Token 与 Snowflake 订单号生成）；
   - 订单状态机：`PENDING_PAYMENT`（待支付）→ `PAID`（已支付）/ `CANCELLED`（已超时关闭）/ `REFUNDED`（已退款）；
   - RabbitMQ 延迟队列（死信队列或插件）实现 15 分钟未支付订单自动取消。
3. **关联文档**：
   - 参考设计文档：[`docs/superpowers/specs/2026-08-18-educloud-services-and-domains.md`](file:///d:/microservice/.worktrees/educloud-backend-foundation/docs/superpowers/specs/2026-08-18-educloud-services-and-domains.md)
   - 契约规范：[`docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md`](file:///d:/microservice/.worktrees/educloud-backend-foundation/docs/superpowers/specs/2026-08-20-educloud-backend-module-execution.md)
