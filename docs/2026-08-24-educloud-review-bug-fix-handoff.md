# EduCloud 项目 Review 问题交接文档

## 文档用途

本文档仅记录本次 Review 发现的问题，包括问题位置、现象和影响。

本文档不包含实现代码、测试代码、修复步骤、提交命令或具体技术方案。下一位工程师应结合当前工作区代码逐项确认后再实施修复。

## Review 范围

- 审查基线：分支 `codex/student-course-sort-dropdown`，HEAD `866ce1d`
- 审查对象：学生端、教师端、管理端三个 React 应用，以及相关设计文档
- 审查包含当前工作区中尚未提交的新文件和修改
- 当前项目以 MOCK 数据为主，真实支付和正式后端授权尚未接入

## 总体结论

当前工作区不建议直接合并。

虽然三个应用的类型检查和生产构建都能通过，但学生端购买闭环存在课程权益绕过、订单归属缺失和支付状态不收敛等问题；教师端存在路由未保护和评分错误写入风险；管理端存在首屏包体过大的性能问题。

---

## 1. 未购买课程可以通过学习地址直接访问

**严重级别：P0，必须修复**

**问题位置：**

- `educloud-frontend/student-portal/src/App.tsx:58`
- `educloud-frontend/student-portal/src/pages/Learning.tsx:19-40`
- `educloud-frontend/student-portal/src/services/api.ts:125-128`
- `educloud-frontend/student-portal/src/services/api.ts:299-329`
- `educloud-frontend/student-portal/src/pages/Checkout.tsx:69-71`

**问题现象：**

- `/learn/:courseId` 只检查用户是否登录，没有检查用户是否购买或加入了该课程。
- Learning 页面获取课程后直接渲染章节和课件。
- 登录用户手动输入学习地址，即可访问未购买课程。
- 种子数据把前六门课程全部标记为已选课，但对应订单中存在待支付和已退款状态。
- Checkout 只要发现课程的 `enrolled` 为真，就直接进入学习页。

**影响：**

- 购买流程可以被直接绕过。
- 待支付或已经退款的课程仍可能拥有学习权限。
- 课程详情、订单、我的课程和学习页之间的状态互相矛盾。
- 正式环境如果沿用这一判断方式，会形成课程内容越权访问风险。

---

## 2. 订单没有记录所属用户

**严重级别：P0，必须修复**

**问题位置：**

- `educloud-frontend/student-portal/src/types/index.ts:174-188`
- `educloud-frontend/student-portal/src/services/mockCheckoutApi.ts:126-129`
- `educloud-frontend/student-portal/src/pages/CheckoutSuccess.tsx:27-38`
- `docs/superpowers/specs/2026-08-19-student-course-purchase-flow-design.md:136-138`

**问题现象：**

- `Order` 类型没有 `userId`、`studentId` 或其他所有者字段。
- 订单查询只根据订单 ID 查找。
- 支付成功页虽然显示“订单不存在或无权访问”，但代码实际上无法判断当前用户是否为订单所有者。
- 支付查询、取消订单和恢复支付同样没有用户范围。

**影响：**

- 系统无法实现可靠的用户级订单隔离。
- 接入多个用户后，知道订单 ID 的登录用户可能读取其他用户的订单信息。
- 课程、金额、订单号和支付状态存在跨用户泄露风险。
- 当前实现不符合购买设计文档中的订单归属要求。

---

## 3. 教师端页面没有登录保护

**严重级别：P1，必须修复**

**问题位置：**

- `educloud-frontend/teacher-portal/src/App.tsx:19-31`
- `educloud-frontend/teacher-portal/src/layouts/TeacherLayout.tsx:51-53`
- `educloud-frontend/teacher-portal/src/stores/useAuthStore.ts:27-29`

**问题现象：**

- 教师端所有业务路由都直接挂载在 TeacherLayout 下，没有认证路由守卫。
- 未登录用户可以直接访问课程、作业、学生、分析和通知页面。
- 退出按钮只跳转到登录页，没有调用 Store 中已有的 `logout`。
- 跳转登录页后，内存中的 token 和用户状态可能仍然存在。

**影响：**

- 登录页只起到视觉入口作用，没有形成完整的访问控制。
- 退出登录的状态与页面表现不一致。
- 后续增加真实教师数据后，可能造成未登录访问或会话未清理问题。

---

## 4. 空评分会被保存为 0 分

**严重级别：P1，必须修复**

**问题位置：**

- `educloud-frontend/teacher-portal/src/components/GradeSheet.tsx:7-35`
- `educloud-frontend/teacher-portal/src/components/GradeSheet.tsx:120-170`
- `educloud-frontend/teacher-portal/src/pages/AssignmentGrade.tsx:114-119`
- `educloud-frontend/teacher-portal/src/services/api.ts:754-765`

**问题现象：**

- 未输入评分时，空字符串经过数字转换后会得到 0。
- 0 在当前范围校验中被认为是合法分数。
- 教师可能在没有输入分数的情况下，把待批作业保存成 0 分。
- `onGrade` 被声明为同步返回，但实际传入的是异步保存函数。
- GradeSheet 调用保存后立即显示“评分提交成功”，没有等待异步操作完成。
- MOCK API 没有再次校验分数是否为空、非数字、负数或超过作业满分。

**影响：**

- 学生成绩可能被错误写成 0 分。
- 保存失败时，页面仍可能短暂显示成功。
- UI 状态和实际数据状态不一致。
- 评分属于重要业务数据，这一问题存在明显的数据完整性风险。

---

## 5. 支付确认轮询没有结束边界

**严重级别：P1，必须修复**

**问题位置：**

- `educloud-frontend/student-portal/src/pages/Checkout.tsx:114-132`
- `educloud-frontend/student-portal/src/pages/CheckoutSuccess.tsx:27-68`

**问题现象：**

- Checkout 使用异步 `setInterval` 每 400 毫秒查询一次支付状态。
- 没有最大查询次数或总超时时间。
- 查询异常没有被捕获。
- 上一次查询未结束时，下一次 interval 仍可能开始，造成请求重叠。
- 如果支付状态一直是 ACTIVE，页面会无限轮询。
- CheckoutSuccess 已经使用限次查询，但 Checkout 的行为与其不一致。

**影响：**

- 页面可能永久停留在“确认中”。
- 网络异常可能产生未处理的 Promise rejection。
- 接入真实网络请求后可能形成重复请求和不必要的服务压力。
- 用户无法明确知道支付结果查询已经失败或超时。

---

## 6. pnpm 的 esbuild 配置仍是占位文本

**严重级别：P1，必须修复**

**问题位置：**

- `educloud-frontend/student-portal/pnpm-workspace.yaml:1-2`
- `educloud-frontend/student-portal/package.json:35`

**问题现象：**

- `pnpm-workspace.yaml` 中 esbuild 的配置值为 `set this to true or false`，不是有效的布尔决策。
- 学生端声明使用 pnpm 11，但安装配置没有明确允许 esbuild 执行构建脚本。
- 实际执行 pnpm 的 ignored-builds 检查时，esbuild 被列为自动忽略。
- 当前生产构建能够通过，是因为工作区已经存在可用的 node_modules，不能证明干净安装可复现。

**影响：**

- 新工程师或 CI 在干净环境安装依赖时，可能得到未正确构建的 esbuild。
- Vite 开发服务器或构建可能因为环境差异失败。
- 当前构建结果不具备可靠的安装可复现性。

---

## 7. 顶部课程搜索没有生效

**严重级别：P1，必须修复**

**问题位置：**

- `educloud-frontend/student-portal/src/components/Navbar.tsx:54-60`
- `educloud-frontend/student-portal/src/pages/CourseList.tsx:29-70`
- `educloud-frontend/student-portal/src/pages/CourseList.tsx:112-118`
- `educloud-frontend/student-portal/src/pages/CourseList.tsx:228-234`

**问题现象：**

- Navbar 搜索后跳转到 `/courses?keyword=搜索词`。
- CourseList 只读取 `category` 参数，没有读取 `keyword`。
- CourseList 内部搜索状态始终从空字符串开始。
- 用户从顶部搜索进入课程列表后，页面仍显示全部课程。
- 刷新、前进和后退也不能从 URL 恢复搜索条件。

**影响：**

- 顶部导航中的课程搜索功能实际上不可用。
- URL 无法作为可分享、可恢复的搜索状态。
- 页面上同时存在两套互不连通的搜索状态。

---

## 8. 支付与优惠文案超过实际能力

**严重级别：P1，面向用户交付前必须修复**

**问题位置：**

- `educloud-frontend/student-portal/src/services/paymentGateway.ts:13-28`
- `educloud-frontend/student-portal/src/components/checkout/PaymentMethodSelector.tsx:5-12`
- `educloud-frontend/student-portal/src/pages/Checkout.tsx:187-243`
- `educloud-frontend/student-portal/src/pages/Home.tsx:291-302`
- `docs/superpowers/specs/2026-08-19-student-course-purchase-flow-design.md:176-189`

**问题现象：**

- 当前支付实现固定使用 `MockPaymentGateway`。
- 页面却直接展示“支付宝”“微信支付”“安全支付”和“确认支付”，没有持续可见的 MOCK 提示。
- 首页宣传“首单立减 50 元”，但结算和订单逻辑没有实现首单优惠。
- 设计文档明确说明真实支付宝和微信尚未接通。

**影响：**

- 用户可能误以为页面会发起真实支付。
- 用户可能期待不存在的 50 元优惠。
- 产品文案与实际交付能力不一致。
- 对外演示时容易把 MOCK 流程误解成真实渠道集成。

---

## 9. 结算持久化状态没有结构校验，学生端缺少自动化测试

**严重级别：P2，建议修改**

**问题位置：**

- `educloud-frontend/student-portal/src/services/mockCheckoutApi.ts:40-52`
- `educloud-frontend/student-portal/package.json:6-10`
- `docs/superpowers/specs/2026-08-19-student-course-purchase-flow-design.md:264`
- `docs/superpowers/specs/2026-08-19-student-course-purchase-flow-design.md:275-315`

**问题现象：**

- localStorage 中的数据经过 JSON 解析后直接断言为 `PersistedState`。
- 代码只处理 JSON 语法错误，不检查 orders、payments 和 idempotency 的结构。
- 合法 JSON `{}`、旧版本缓存或被修改的缓存都可能在模块初始化阶段引发异常。
- localStorage 写入失败也没有明确处理。
- 学生端 package.json 没有测试脚本。
- 学生端源码中没有购买、支付、搜索或路由相关自动化测试。

**影响：**

- 缓存损坏或数据结构升级后，学生端可能整页无法启动。
- 购买状态机没有稳定的回归保护。
- 权益绕过、跨用户订单和无限轮询等问题不会被类型检查或生产构建发现。

---

## 10. 管理端首屏包体过大

**严重级别：P2，建议修改**

**问题位置：**

- `educloud-frontend/admin-portal/src/App.tsx:1-11`
- `educloud-frontend/admin-portal/src/App.tsx:31-64`

**问题现象：**

- App 在入口处静态导入全部管理页面。
- Dashboard、Finance 等包含图表依赖的页面与登录页进入同一个初始依赖图。
- 当前生产构建的主 JavaScript 文件约为 680.33 kB。
- Vite 已提示单个 chunk 大于 500 kB。

**影响：**

- 用户访问登录页时也要下载尚未使用的管理页面代码。
- 首次加载和弱网环境体验受到影响。
- 随着管理功能增加，入口包体还会继续增长。

---

## 已确认的正面情况

- 学生端、教师端和管理端的 TypeScript 类型检查均可通过。
- 三个应用的生产构建均可通过。
- 教师通知中心现有 3 个测试文件、16 项测试全部通过。
- 教师通知中心明确说明当前消息只保存在浏览器中。
- AI 助教未配置远端服务时会显示“演示模式”，没有把本地规则回复描述成真实 AI 服务。
- 登录重定向工具会拒绝外部 URL 和协议相对 URL。
- 作业发布弹窗包含焦点约束、焦点恢复和页面滚动锁定。

## 当前验证边界

- 本次 Review 没有修改或修复业务源码。
- 本次 Review 没有执行学生端浏览器 E2E。
- 学生端和管理端目前没有可运行的自动化测试套件。
- 当前真实支付宝、微信支付、后端回调验签、服务端订单授权和服务端课程权益均未接入。
- 类型检查和构建通过不能证明以上业务问题不存在。

## 建议处理顺序

1. 课程访问权绕过。
2. 订单用户归属。
3. 教师端路由与退出。
4. 评分错误写入。
5. 支付轮询。
6. pnpm/esbuild 安装配置。
7. 课程搜索。
8. 支付与优惠文案。
9. 持久化结构和学生端测试。
10. 管理端路由拆包。
