# M07 订单中心（educloud-order）交付与交接规格说明

> 日期：2026-08-24  
> 模块：M07 订单中心与交易服务（educloud-order）  
> 状态：COMPLETED / ALL PASS

---

## 1. 模块设计与交付总览

M07（educloud-order）作为 EduCloud 核心交易与订单管理中枢，覆盖从购物车、防重提单、延时自动关单、模拟支付流转、MQ 异步履约到前后端全链路联调。

### 1.1 核心服务边界与技术实现
- **购物车服务**：基于 Redis Hash 缓存与 MySQL 持久化双重支持，提供单课加购、勾选切换、单条删除与批量清空。
- **防重提单机制**：基于 Redis + Lua 脚本实现的 Idempotency Token 验证，防止网络抖动或用户重复点击导致重复下单。
- **订单生命周期管理**：
  - 提单后进入 `PENDING_PAYMENT`，启动 15 分钟失效倒计时；
  - 基于 RabbitMQ TTL（15分钟）+ 死信交换机（DLX）实现自动化延时关单，使用 CAS（`status = PENDING_PAYMENT -> CANCELLED`）安全更新；
  - 模拟支付（`POST /api/v1/orders/{id}/mock-pay`）直接流转至 `PAID`，发布 `OrderPaidEvent` 到 TopicExchange `educloud.order.exchange` (`order.paid`)；
  - `educloud-course` 监听 `order.paid` 事件，幂等完成学员课程报名（`ACTIVE`）与销量/学时统计更新。
- **管理端与内部快照服务**：
  - 提供内部 Feign 快照查询接口 `/internal/v1/orders/{id}/payable-snapshot` 与 `/internal/v1/orders/{id}/fulfillment-snapshot`；
  - 管理端分页查询 `/api/v1/admin/orders` 与详情查询 `/api/v1/admin/orders/{id}`（`@PreAuthorize("hasAuthority('order:view')")`）。
- **网关路由与安全配置**：
  - `educloud-gateway` 配置 `order-core` 路由组（双端口 8091 业务 / 8092 监控）；
  - `AccessPolicy.java` 配置学员端与管理端路径 RBAC 权限。

---

## 2. 交付物与自动化验收覆盖

### 2.1 自动化测试证据
1. **单元与控制器测试**：
   - `educloud-order`: 83/83 单元与集成测试全部通过
   - `educloud-course`: 289/289 单元与集成测试全部通过
   - `educloud-gateway`: 172/172 路由与权限测试全部通过
2. **微服务全量编译**：
   - `mvn clean test-compile` 全量 8 个模块 `BUILD SUCCESS`
3. **前端三端构建**：
   - `student-portal`: `npm run build` 0 错误
   - `teacher-portal`: `npm run build` 0 错误
   - `admin-portal`: `npm run build` 0 错误
4. **E2E 验收脚本**：
   - `scratch/test_order_e2e.py` 覆盖学员登录 -> 加购 -> 防重Token -> 提单 -> 模拟支付 -> 自动选课 -> 管理端审核全生命周期。
