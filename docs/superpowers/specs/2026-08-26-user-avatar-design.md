# 用户管理高质感单字头像与字体排版设计规范

## 1. 背景与问题分析
在管理端用户列表（`UserManage.tsx`）及全局头像展示中，之前采用第三方 DiceBear Initials 7.x 接口生成中文字符 SVG。由于全角汉字横向宽度较大且 DiceBear 缺少安全内边距约束，导致双字（如「张伟」「王芳」「李娜」）横向撑满圆圈，在圆形边框处产生严重的贴边、超框、裁切拥挤感。

用户明确要求：
- 采用 **方案 A（单字末字 + 雅致色板）**；
- 同步优化 **头像字体与字号排版**，彻底消除粗暴感，达到企业级精致视觉效果。

---

## 2. 详细设计规范

### 2.1 文字截取逻辑 (`getAvatarChar`)
- **中文名称**：提取名称的**最后一个汉字**（例如「张伟」→「伟」，「王芳」→「芳」，「李娜」→「娜」，「欧阳六一」→「一」），符合国内钉钉、飞书、企业微信等顶尖 SaaS 产品的设计惯例；
- **英文/数字账号**：提取**首字母大写**（例如「user1000」→「U」，「demo_admin」→「D」）；
- **空值兜底**：兜底显示「用」或「U」。

### 2.2 字体排版与视觉参数
- **字体族**：系统级优雅无衬线字体序列 `system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif`；
- **字号与字重**：
  - 40px (md) 容器：字号 `15px` (`text-[15px]`)，字重 `font-medium`（Medium 500），抗锯齿 `antialiased`；
  - 32px (sm) 容器：字号 `12px` (`text-xs`)，字重 `font-medium`；
  - 48px (lg) 容器：字号 `18px` (`text-lg`)，字重 `font-medium`；
- **空间占比**：字符与外框保持约 **40% 的优雅安全留白**，文字水平与垂直绝对居中，无任何溢出贴边风险；
- **字色与阴影**：纯白 `text-white`，搭配细腻的微妙投影 `drop-shadow-sm`。

### 2.3 雅致商务色板体系（基于哈希稳定取色）
基于用户名字符串通过 DJB2/MurmurHash 算法生成确定性哈希，映射到 8 组高质感商务低饱和微渐变色彩：
1. **Indigo（经典靛蓝）**：`bg-gradient-to-br from-indigo-500 to-indigo-600`
2. **Blue（暮光天蓝）**：`bg-gradient-to-br from-blue-500 to-blue-600`
3. **Emerald（翡翠黛绿）**：`bg-gradient-to-br from-emerald-500 to-emerald-600`
4. **Amber（暖阳琥珀）**：`bg-gradient-to-br from-amber-500 to-amber-600`
5. **Violet（高雅罗兰）**：`bg-gradient-to-br from-purple-500 to-purple-600`
6. **Rose（雅致石榴）**：`bg-gradient-to-br from-rose-500 to-rose-600`
7. **Teal（松石青蓝）**：`bg-gradient-to-br from-teal-500 to-teal-600`
8. **Slate（高级青灰）**：`bg-gradient-to-br from-slate-600 to-slate-700`

外圈辅以 `ring-1 ring-black/5 dark:ring-white/10` 与微阴影 `shadow-sm`，增强层次感。

---

## 3. 受影响组件与文件清单
1. **新增通用组件**：`admin-portal/src/components/UserAvatar.tsx`
   - 支持传入 `name`、`src`（真实图片优先）、`size`（sm/md/lg）、`className`；
   - 图片加载失败自动平滑无缝降级到单字头像。
2. **用户管理页面**：`admin-portal/src/pages/UserManage.tsx`
   - 替换原有的 `<img>` 为 `<UserAvatar name={u.username} src={u.avatarUrl} size="md" />`。
3. **全局布局组件**：`admin-portal/src/layouts/AdminLayout.tsx`
   - 顶部导航栏与侧边栏底部的管理员头像统一接入 `<UserAvatar name={admin?.displayName || admin?.username} />`。

---

## 4. 验证方案
1. 编译并部署至 VM 虚拟机；
2. 使用 Playwright 自动化截取用户管理列表（`UserManage.tsx`）及顶部管理员头像；
3. 核验：
   - 「张伟」显示单个圆润居中的「伟」；
   - 「王芳」显示「芳」；
   - 「李娜」显示「娜」；
   - 字体优雅细腻、留白舒适、绝无溢出或贴边现象。
