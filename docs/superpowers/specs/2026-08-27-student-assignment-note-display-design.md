# 教师端批改作业展示学生留言与附件设计规格

## 1. 背景与目标
在学员端提交作业时，系统支持学员输入“向授课教师留言（选填）”以及上传“作业附件”。目前教师端作业批改详情页（`GradeSheet.tsx`）仅展示了作业正文（`content`），未展示学员留言（`note`）与附件（`files`）。
本设计的目的是在教师端作业批改界面完整打通并展示学员的留言与提交附件，提升师生交互体验。

---

## 2. 详细设计

### 2.1 数据层（类型定义与后端传递）
1. **教师端前端类型（`teacher-portal/src/types/index.ts`）**：
   - 更新 `Submission` 接口：
     ```typescript
     export interface SubmissionFile {
       name: string;
       size?: number;
       url?: string;
     }

     export interface Submission {
       id: string;
       assignmentId: string;
       studentId: string;
       studentName: string;
       studentAvatar: string;
       content: string;
       note?: string;
       files?: SubmissionFile[];
       submittedAt: string;
       score?: number;
       feedback?: string;
       status: SubmissionStatus;
     }
     ```

2. **后端数据返回（`educloud-content`）**：
   - `AssignmentService.java` 的 `getSubmissionsForAssignment` 与 `submitAssignment` 确保返回 `note` 与 `files` 字段，并在反查 Redis 提交哈希时保留这些属性。

### 2.2 UI 呈现层（`teacher-portal/src/components/GradeSheet.tsx`）
1. **学生留言区域（有留言时展示）**：
   - 位置：位于“学生信息栏”下方、“作业内容”上方；
   - 样式：采用高对比度淡琥珀色背景与边框（`bg-amber-50/70 border border-amber-200/80 rounded-xl p-4`），左侧带有 `MessageSquareQuote` 图标及“向老师留言”标签；
   - 内容：渲染 `selected.note` 文本内容；
   - 边界逻辑：当 `selected.note` 为空或仅包含空白字符时，自动收起隐藏，不占用屏幕空间。

2. **提交附件区域（有附件时展示）**：
   - 位置：位于“作业内容”下方；
   - 样式：展示文件列表卡片，显示文件名、大小（如有）及下载/预览链接图标；
   - 边界逻辑：当 `selected.files` 为空数组或无附件时自动隐藏。

---

## 3. 验证计划
1. 使用学员账号提交带有“向老师留言”与附件的作业；
2. 登录教师端查看该作业批改界面；
3. 验证留言卡片是否清晰展示在作业正文上方，附件是否展示在正文下方；
4. 验证未留言作业的自适应隐藏表现；
5. 使用 Playwright 进行截图与端到端功能验证。
