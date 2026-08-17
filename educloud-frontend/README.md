# EduCloud 前端应用

EduCloud 在线教育平台前端，包含三个独立的 React 应用。

## 设计风格

**Editorial Scholarly（学术编辑风）** — 顶级大学出版社的排版质感 × 现代 SaaS 仪表盘的功能效率。

- **字体**: Fraunces（衬线展示字体）+ DM Sans（无衬线正文字体）
- **配色**: 深靛蓝 `#1e1b4b`（主色）、暖琥珀 `#d97706`（强调色）、暖白 `#faf8f5`（背景）
- **特征**: 大号衬线数字编号、非对称编辑网格、直角按钮、薄边框、纸张纹理

## 技术栈

- React 18.3 + TypeScript 5.5 + Vite 5.4
- Tailwind CSS 3.4
- Zustand 5.0（状态管理）
- React Router 6.27（路由）
- Axios 1.7（HTTP 客户端）
- Recharts 2.12（管理后台图表）
- Lucide React（图标）

## 三个应用

| 应用 | 目录 | 端口 | 说明 |
|------|------|------|------|
| 学生端 | `student-portal/` | 5173 | 课程浏览、购买、学习、直播、作业考试 |
| 教师端 | `teacher-portal/` | 5174 | 课程管理、内容上传、直播、作业批改 |
| 管理后台 | `admin-portal/` | 5175 | 用户管理、审核、订单、财务、系统配置 |

## 快速开始

### 前置条件

- Node.js 20+（当前环境 v22.23.1）
- npm 或 pnpm

### 安装依赖

每个应用需要单独安装依赖：

```bash
cd student-portal && npm install
cd ../teacher-portal && npm install
cd ../admin-portal && npm install
```

### 启动开发服务器

```bash
# 学生端（终端1）
cd student-portal
npm run dev
# → http://localhost:5173

# 教师端（终端2）
cd teacher-portal
npm run dev
# → http://localhost:5174

# 管理后台（终端3）
cd admin-portal
npm run dev
# → http://localhost:5175
```

### 生产构建

```bash
cd student-portal && npm run build
cd ../teacher-portal && npm run build
cd ../admin-portal && npm run build
```

构建产物输出到各自的 `dist/` 目录。

### 演示账号

所有应用均使用 Mock 数据，无需后端即可运行。登录页已预填演示账号，直接点击登录即可。

## 页面结构

### 学生端（11 个页面）
- 首页（课程推荐、分类、统计）
- 课程列表（筛选、搜索、排序）
- 课程详情（章节、评价、购买）
- 我的课程（学习进度）
- 学习页面（视频播放、章节目录）
- 直播房间（实时聊天）
- 作业列表
- 考试中心
- 个人中心
- 订单管理
- 登录页

### 教师端（10 个页面）
- 工作台（统计、动态、直播预告）
- 课程管理
- 课程编辑（表单、章节、封面）
- 内容管理（章节课件编辑）
- 直播管理
- 作业批改
- 考试管理
- 学生列表
- 数据分析
- 登录页

### 管理后台（9 个页面）
- 数据看板（Recharts 图表）
- 用户管理
- 课程审核
- 内容审核
- 订单管理
- 财务管理
- 系统配置
- 操作日志
- 登录页
