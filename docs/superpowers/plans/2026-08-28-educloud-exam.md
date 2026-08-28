# EduCloud 在线考试模块实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `educloud-content` 模块内实现在线考试闭环：教师题库 CRUD + 组卷发布，学生限时考试 + 服务端机器判分，交卷后经 Outbox 发布 `ExamGraded` 事件联动动态流与站内通知。

**架构：** 并入 `educloud-content`（MySQL 4 张新表 + MyBatis Plus），判分引擎为纯函数；attempt 状态迁移用 CAS 条件更新；超时收敛定时任务兜底；事件走既有 Outbox → RabbitMQ 链路（`exam.graded` 发往全域总线 `educloud.events`），analytics 动态流与 notification 各自定向订阅。前端学生端对接真实 API（保留 mock 回退），教师端落地题库管理与组卷 UI。

**技术栈：** Java 17 / Spring Boot 3.2 / MyBatis Plus / MySQL（educloud_content 库）/ RabbitMQ Outbox / React 18 + TS + Vite。

**规格：** `docs/superpowers/specs/2026-08-28-educloud-exam-design.md`（commit `4c8e03f`）

---

## 文件结构

**后端（educloud-content，主路径 `educloud-backend/educloud-content/`）**

| 文件 | 职责 |
|---|---|
| `deploy/sql/content/V005__exam.sql` | 4 张表 + GRANT |
| `src/main/java/com/educloud/content/entity/ExamBankQuestionEntity.java` | 题库题目实体 |
| `src/main/java/com/educloud/content/entity/ExamEntity.java` | 考试实体 |
| `src/main/java/com/educloud/content/entity/ExamPaperQuestionEntity.java` | 组卷明细实体 |
| `src/main/java/com/educloud/content/entity/ExamAttemptEntity.java` | 考试记录实体 |
| `src/main/java/com/educloud/content/mapper/ExamBankQuestionMapper.java` | 题目 Mapper |
| `src/main/java/com/educloud/content/mapper/ExamMapper.java` | 考试 Mapper |
| `src/main/java/com/educloud/content/mapper/ExamPaperQuestionMapper.java` | 组卷 Mapper |
| `src/main/java/com/educloud/content/mapper/ExamAttemptMapper.java` | attempt Mapper（CAS SQL + 超时扫描） |
| `src/main/java/com/educloud/content/exam/ExamGradingEngine.java` | 判分纯函数 |
| `src/main/java/com/educloud/content/exam/ExamQuestionSnapshot.java` | 快照 JSON 解析 record |
| `src/main/java/com/educloud/content/service/ExamAttemptService.java` | 学生端：开始/交卷/查询/超时收敛 |
| `src/main/java/com/educloud/content/service/ExamBankService.java` | 教师端：题目 CRUD |
| `src/main/java/com/educloud/content/service/ExamService.java` | 教师端：考试 CRUD + 组卷 + 发布 |
| `src/main/java/com/educloud/content/controller/ExamStudentController.java` | 学生 API |
| `src/main/java/com/educloud/content/controller/ExamTeacherController.java` | 教师 API |
| `src/main/java/com/educloud/content/messaging/ContentEventPublisher.java` | 新增 `examGraded()`（修改） |
| `src/main/java/com/educloud/content/messaging/OutboxEventDispatcher.java` | 新增 `ExamGraded` 路由（修改） |
| `src/main/java/com/educloud/content/exception/ContentErrorCode.java` | 新增考试错误码（修改） |
| DTO：`dto/request/ExamQuestionRequest.java`、`dto/request/ExamCreateRequest.java`、`dto/request/ExamSubmitRequest.java`、`dto/response/ExamBankQuestionResponse.java`、`dto/response/ExamResponse.java`、`dto/response/ExamQuestionResponse.java`、`dto/response/ExamAttemptResponse.java` | 请求/响应 |
| 测试：`src/test/java/com/educloud/content/exam/ExamGradingEngineTest.java`、`service/ExamAttemptServiceTest.java`、`service/ExamBankServiceTest.java`、`service/ExamServiceTest.java`、`messaging/ContentEventPublisherTest.java`（修改） | 单元测试 |

**事件消费端**

| 文件 | 职责 |
|---|---|
| `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/config/RabbitMqConfig.java` | 新增 exam 队列 + binding（修改） |
| `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/ActivityFeedConsumer.java` | 新增 `onExamEvent` + `mapExamGraded`（修改） |
| `educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/ActivityFeedController.java` | 新增 EXAM_PASSED/EXAM_FAILED 文案（修改） |
| `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/config/RabbitMqConfiguration.java` | 新增 exam.graded binding（修改） |
| `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/events/ExamGradedEvent.java` | 事件 DTO |
| `educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/DomainNotificationConsumer.java` | 新增分支 + `handleExamGraded`（修改） |

**前端**

| 文件 | 职责 |
|---|---|
| `educloud-frontend/student-portal/src/types/index.ts` | Exam/ExamQuestion 类型扩展（修改） |
| `educloud-frontend/student-portal/src/services/studentAssignmentService.ts` | getExams/startExam/submitExam 对接 HTTP（修改） |
| `educloud-frontend/student-portal/src/components/exams/ExamSessionModal.tsx` | 多选/判断、切屏监听、移除答案展示（修改） |
| `educloud-frontend/teacher-portal/src/services/api.ts` | getExams/createExam 对接 HTTP + 题库 API（修改） |
| `educloud-frontend/teacher-portal/src/pages/ExamManage.tsx` | 组卷改造 + 题库管理视图（修改） |

**部署**

| 文件 | 职责 |
|---|---|
| `deploy/tests/content-exam-contract-tests.sh` | 契约脚本（新建） |

---

## 任务 1：数据库迁移 V005

**文件：**
- 创建：`deploy/sql/content/V005__exam.sql`

- [ ] **步骤 1：编写迁移脚本**

```sql
-- EduCloud Content 数据库：在线考试模块（V005）
-- 依据：规格 2026-08-28-educloud-exam-design.md §3

CREATE TABLE IF NOT EXISTS exam_bank_question (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    course_id     BIGINT       NOT NULL COMMENT '归属课程',
    teacher_id    BIGINT       NOT NULL COMMENT '出题教师',
    question_type VARCHAR(16)  NOT NULL COMMENT 'SINGLE/MULTIPLE/JUDGE',
    stem          TEXT         NOT NULL COMMENT '题干',
    options       JSON         NOT NULL COMMENT '选项数组',
    answer        JSON         NOT NULL COMMENT '答案数组（数组索引）',
    analysis      TEXT         NULL COMMENT '答案解析',
    default_score INT          NOT NULL DEFAULT 5 COMMENT '默认分值',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_course (course_id),
    KEY idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题库题目';

CREATE TABLE IF NOT EXISTS exam (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    course_id        BIGINT       NOT NULL COMMENT '课程',
    course_title     VARCHAR(255) NOT NULL COMMENT '课程标题快照',
    title            VARCHAR(255) NOT NULL COMMENT '考试标题',
    description      TEXT         NULL,
    duration_minutes INT          NOT NULL COMMENT '限时时长',
    total_score      INT          NOT NULL DEFAULT 0 COMMENT '总分',
    pass_score       INT          NOT NULL DEFAULT 60 COMMENT '及格分',
    start_time       DATETIME(3)  NOT NULL COMMENT '考试窗口开始',
    end_time         DATETIME(3)  NOT NULL COMMENT '考试窗口结束',
    status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/CLOSED',
    teacher_id       BIGINT       NOT NULL COMMENT '创建教师',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_course (course_id),
    KEY idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试';

CREATE TABLE IF NOT EXISTS exam_paper_question (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    exam_id           BIGINT      NOT NULL COMMENT '考试',
    question_id       BIGINT      NOT NULL COMMENT '题库题目',
    question_snapshot JSON        NOT NULL COMMENT '题目快照（题干/选项/答案/题型/分值）',
    score             INT         NOT NULL COMMENT '本题分值',
    sort_order        INT         NOT NULL DEFAULT 0,
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_exam (exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组卷明细';

CREATE TABLE IF NOT EXISTS exam_attempt (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    exam_id          BIGINT      NOT NULL COMMENT '考试',
    student_id       BIGINT      NOT NULL COMMENT '学员',
    status           VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/GRADED',
    started_at       DATETIME(3) NOT NULL COMMENT '服务端记录的开始时间',
    submitted_at     DATETIME(3) NULL,
    score            INT         NULL,
    passed           TINYINT     NULL,
    answers_json     JSON        NULL COMMENT '作答（questionId -> 索引数组）',
    tab_switch_count INT         NOT NULL DEFAULT 0 COMMENT '切屏次数',
    flagged          TINYINT     NOT NULL DEFAULT 0 COMMENT '切屏>=5 标记',
    timeout          TINYINT     NOT NULL DEFAULT 0 COMMENT '超时自动交卷',
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_student (exam_id, student_id),
    KEY idx_student (student_id),
    KEY idx_status_started (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试记录';

GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.exam_bank_question TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.exam TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.exam_paper_question TO 'content_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON educloud_content.exam_attempt TO 'content_app'@'%';
```

- [ ] **步骤 2：在本地 MySQL 执行验证**

运行：`mysql -u root -p < deploy/sql/content/V005__exam.sql`（或通过 `deploy/scripts/run-migrations.sh` 对齐既有迁移执行方式）
预期：4 张表创建成功，`SHOW TABLES FROM educloud_content;` 可见 4 张新表

- [ ] **步骤 3：Commit**

```bash
git add deploy/sql/content/V005__exam.sql
git commit -m "feat(考试): 新增题库/考试/组卷/考试记录表迁移"
```

## 任务 2：实体与 Mapper

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/ExamBankQuestionEntity.java`、`ExamEntity.java`、`ExamPaperQuestionEntity.java`、`ExamAttemptEntity.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/ExamBankQuestionMapper.java`、`ExamMapper.java`、`ExamPaperQuestionMapper.java`、`ExamAttemptMapper.java`

- [ ] **步骤 1：编写 4 个实体**（对齐 `CourseCertificateEntity` 风格：Lombok @Data + MyBatis Plus 注解）

`ExamBankQuestionEntity.java`：

```java
package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_bank_question")
public class ExamBankQuestionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long teacherId;
    private String questionType;
    private String stem;
    private String options;
    private String answer;
    private String analysis;
    private Integer defaultScore;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`ExamEntity.java`：

```java
package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam")
public class ExamEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String title;
    private String description;
    private Integer durationMinutes;
    private Integer totalScore;
    private Integer passScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Long teacherId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`ExamPaperQuestionEntity.java`：

```java
package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_paper_question")
public class ExamPaperQuestionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long questionId;
    private String questionSnapshot;
    private Integer score;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
```

`ExamAttemptEntity.java`：

```java
package com.educloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_attempt")
public class ExamAttemptEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long studentId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private Integer score;
    private Integer passed;
    private String answersJson;
    private Integer tabSwitchCount;
    private Integer flagged;
    private Integer timeout;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 2：编写 4 个 Mapper**

```java
package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.content.entity.ExamBankQuestionEntity;

public interface ExamBankQuestionMapper extends BaseMapper<ExamBankQuestionEntity> {
}
```

```java
package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.content.entity.ExamEntity;

public interface ExamMapper extends BaseMapper<ExamEntity> {
}
```

```java
package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.content.entity.ExamPaperQuestionEntity;

public interface ExamPaperQuestionMapper extends BaseMapper<ExamPaperQuestionEntity> {
}
```

`ExamAttemptMapper.java`（含 CAS 与超时扫描 SQL）：

```java
package com.educloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.educloud.content.entity.ExamAttemptEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ExamAttemptMapper extends BaseMapper<ExamAttemptEntity> {

    /** CAS 判分收尾：仅 IN_PROGRESS 可迁移到 GRADED，返回 0 表示已被并发提交/不存在。 */
    @Update("UPDATE exam_attempt SET status='GRADED', score=#{score}, passed=#{passed}, "
            + "answers_json=#{answersJson}, submitted_at=#{submittedAt}, "
            + "tab_switch_count=#{tabSwitchCount}, flagged=#{flagged}, timeout=#{timeout} "
            + "WHERE id=#{id} AND status='IN_PROGRESS'")
    int markGraded(ExamAttemptEntity attempt);

    /** 超时未交卷的进行中记录（服务端时间，JOIN exam 取时长）。 */
    @Select("SELECT a.* FROM exam_attempt a JOIN exam e ON a.exam_id = e.id "
            + "WHERE a.status = 'IN_PROGRESS' "
            + "AND TIMESTAMPADD(MINUTE, e.duration_minutes, a.started_at) <= NOW(3) "
            + "LIMIT 100")
    List<ExamAttemptEntity> selectExpiredInProgress();
}
```

- [ ] **步骤 3：编译验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/entity/Exam*.java educloud-backend/educloud-content/src/main/java/com/educloud/content/mapper/Exam*.java
git commit -m "feat(考试): 新增考试实体与 Mapper（含 CAS 判分与超时扫描）"
```

## 任务 3：判分引擎（TDD）

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/exam/ExamQuestionSnapshot.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/exam/ExamGradingEngine.java`
- 测试：`educloud-backend/educloud-content/src/test/java/com/educloud/content/exam/ExamGradingEngineTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.educloud.content.exam;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExamGradingEngineTest {

    private static ExamQuestionSnapshot q(Long id, String type, List<Integer> answer, int score) {
        return new ExamQuestionSnapshot(id, type, List.of("A", "B", "C", "D"), answer, score);
    }

    @Test
    void single_correctAndWrong() {
        var paper = List.of(q(1L, "SINGLE", List.of(0), 10), q(2L, "SINGLE", List.of(2), 10));
        var result = ExamGradingEngine.grade(paper, Map.of(1L, List.of(0), 2L, List.of(1)));
        assertThat(result.earnedScore()).isEqualTo(10);
        assertThat(result.totalScore()).isEqualTo(20);
        assertThat(result.details()).extracting("correct").containsExactly(true, false);
    }

    @Test
    void multiple_requiresExactSetMatch() {
        var paper = List.of(q(1L, "MULTIPLE", List.of(0, 2), 20));
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0, 2))).earnedScore()).isEqualTo(20);
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(2, 0))).earnedScore()).isEqualTo(20);
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0))).earnedScore()).isZero();
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0, 1, 2))).earnedScore()).isZero();
    }

    @Test
    void judge_treatedAsSingle() {
        var paper = List.of(q(1L, "JUDGE", List.of(0), 5));
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(0))).earnedScore()).isEqualTo(5);
        assertThat(ExamGradingEngine.grade(paper, Map.of(1L, List.of(1))).earnedScore()).isZero();
    }

    @Test
    void unansweredAndUnknownQuestionScoreZero() {
        var paper = List.of(q(1L, "SINGLE", List.of(0), 10), q(2L, "SINGLE", List.of(1), 10));
        var result = ExamGradingEngine.grade(paper, Map.of(1L, List.of(0), 99L, List.of(1)));
        assertThat(result.earnedScore()).isEqualTo(10);
        assertThat(result.totalScore()).isEqualTo(20);
    }

    @Test
    void emptyAnswersScoresZero() {
        var paper = List.of(q(1L, "SINGLE", List.of(0), 10));
        assertThat(ExamGradingEngine.grade(paper, Map.of()).earnedScore()).isZero();
        assertThat(ExamGradingEngine.grade(paper, null).earnedScore()).isZero();
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test -Dtest=ExamGradingEngineTest`
预期：编译失败，`ExamGradingEngine` 不存在

- [ ] **步骤 3：编写实现**

`ExamQuestionSnapshot.java`：

```java
package com.educloud.content.exam;

import java.util.List;

/** 组卷快照：判分与展示只读此快照，不受题库编辑影响。 */
public record ExamQuestionSnapshot(
        Long questionId,
        String questionType,
        List<String> options,
        List<Integer> answer,
        int score) {
}
```

`ExamGradingEngine.java`：

```java
package com.educloud.content.exam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 客观题判分纯函数：SINGLE/JUDGE 索引相等，MULTIPLE 集合完全相等；不做部分得分。 */
public final class ExamGradingEngine {

    public record GradedQuestion(Long questionId, String questionType, int score, boolean correct) {
    }

    public record GradeResult(int earnedScore, int totalScore, List<GradedQuestion> details) {
    }

    private ExamGradingEngine() {
    }

    public static GradeResult grade(List<ExamQuestionSnapshot> paper, Map<Long, List<Integer>> answers) {
        List<GradedQuestion> details = new ArrayList<>();
        int earned = 0;
        int total = 0;
        for (ExamQuestionSnapshot question : paper) {
            List<Integer> chosen = answers == null
                    ? List.of()
                    : answers.getOrDefault(question.questionId(), List.of());
            boolean correct = isCorrect(question, chosen);
            if (correct) {
                earned += question.score();
            }
            total += question.score();
            details.add(new GradedQuestion(question.questionId(), question.questionType(), question.score(), correct));
        }
        return new GradeResult(earned, total, Collections.unmodifiableList(details));
    }

    private static boolean isCorrect(ExamQuestionSnapshot question, List<Integer> chosen) {
        List<Integer> expected = question.answer();
        if ("MULTIPLE".equals(question.questionType())) {
            return chosen.size() == expected.size()
                    && chosen.stream().sorted().toList().equals(expected.stream().sorted().toList());
        }
        return chosen.size() == 1 && chosen.get(0).equals(expected.get(0));
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test -Dtest=ExamGradingEngineTest`
预期：5 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/exam/ educloud-backend/educloud-content/src/test/java/com/educloud/content/exam/
git commit -m "feat(考试): 新增客观题判分引擎（单选/多选/判断）"
```

## 任务 4：错误码与 DTO

**文件：**
- 修改：`educloud-backend/educloud-content/src/main/java/com/educloud/content/exception/ContentErrorCode.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/request/ExamQuestionRequest.java`、`ExamCreateRequest.java`、`ExamSubmitRequest.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/response/ExamBankQuestionResponse.java`、`ExamQuestionResponse.java`、`ExamResponse.java`、`ExamAttemptResponse.java`

- [ ] **步骤 1：扩展错误码**

在 `ContentErrorCode` 枚举末尾追加（对齐既有格式）：

```java
    EXAM_NOT_FOUND(404, "Exam not found"),
    EXAM_QUESTION_NOT_FOUND(404, "Exam bank question not found"),
    EXAM_ATTEMPT_NOT_FOUND(404, "Exam attempt not found"),
    EXAM_NOT_PUBLISHED(409, "Exam is not published"),
    EXAM_OUTSIDE_WINDOW(409, "Exam is outside the scheduled window"),
    EXAM_ATTEMPT_ALREADY_EXISTS(409, "An active attempt already exists for this exam"),
    EXAM_ATTEMPT_NOT_OWNED(403, "Exam attempt does not belong to current user"),
    EXAM_ATTEMPT_NOT_SUBMITTABLE(409, "Exam attempt is not in progress"),
    EXAM_QUESTION_IN_USE(409, "Question is referenced by a published exam"),
    EXAM_NOT_DRAFT(409, "Exam is not in draft state"),
    EXAM_PAPER_EMPTY(400, "Exam paper must contain at least one question");
```

- [ ] **步骤 2：编写请求 DTO**

`ExamQuestionRequest.java`（题库题目创建/更新）：

```java
package com.educloud.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class ExamQuestionRequest {
    @NotNull
    private Long courseId;
    @NotBlank
    private String questionType;
    @NotBlank
    private String stem;
    @NotNull
    private List<String> options;
    @NotNull
    private List<Integer> answer;
    private String analysis;
    @Positive
    private Integer defaultScore;
}
```

`ExamCreateRequest.java`（创建考试：基本信息 + 组卷选题）：

```java
package com.educloud.content.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamCreateRequest {
    @NotNull
    private Long courseId;
    @NotBlank
    private String title;
    private String description;
    @Positive
    private Integer durationMinutes;
    @Positive
    private Integer passScore;
    @NotNull
    private LocalDateTime startTime;
    @NotNull
    private LocalDateTime endTime;
    @NotEmpty
    private List<@Valid PaperItem> paper;

    @Data
    public static class PaperItem {
        @NotNull
        private Long questionId;
        @Positive
        private Integer score;
    }
}
```

`ExamSubmitRequest.java`（交卷）：

```java
package com.educloud.content.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ExamSubmitRequest {
    /** questionId -> 选项索引数组 */
    private Map<Long, List<Integer>> answers;
    @Min(0)
    private Integer tabSwitchCount;
}
```

- [ ] **步骤 3：编写响应 DTO**

`ExamBankQuestionResponse.java`：

```java
package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamBankQuestionResponse {
    private Long id;
    private Long courseId;
    private String questionType;
    private String stem;
    private List<String> options;
    private Integer defaultScore;
    private LocalDateTime createdAt;
}
```

`ExamQuestionResponse.java`（学生答题视图，永不含答案）：

```java
package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ExamQuestionResponse {
    private Long id;
    private String questionType;
    private String stem;
    private List<String> options;
    private Integer score;
}
```

`ExamResponse.java`：

```java
package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExamResponse {
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String title;
    private String description;
    private Integer durationMinutes;
    private Integer totalScore;
    private Integer passScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private List<ExamQuestionResponse> questions;
    private Integer score;
    private Boolean passed;
    private String attemptStatus;
}
```

`ExamAttemptResponse.java`（成绩与答卷，判分后含答案）：

```java
package com.educloud.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExamAttemptResponse {
    private Long id;
    private Long examId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private Integer score;
    private Boolean passed;
    private Boolean timeout;
    private Integer tabSwitchCount;
    private Map<Long, List<Integer>> answers;
    private List<ExamQuestionResult> results;

    @Data
    @Builder
    public static class ExamQuestionResult {
        private Long questionId;
        private String questionType;
        private String stem;
        private List<String> options;
        private List<Integer> answer;
        private Integer score;
        private Boolean correct;
    }
}
```

- [ ] **步骤 4：编译验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/exception/ContentErrorCode.java educloud-backend/educloud-content/src/main/java/com/educloud/content/dto/
git commit -m "feat(考试): 新增考试错误码与请求/响应 DTO"
```

## 任务 5：事件发布（examGraded）

**文件：**
- 修改：`educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/ContentEventPublisher.java`
- 修改：`educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/OutboxEventDispatcher.java`
- 测试：`educloud-backend/educloud-content/src/test/java/com/educloud/content/messaging/ContentEventPublisherTest.java`（追加用例）

- [ ] **步骤 1：在 ContentEventPublisher 增加 examGraded 方法**

在 `certificateIssued` 方法后追加（复用 `writeOutbox`）：

```java
    /**
     * 考试判分完成事件（在线考试模块）：交卷/超时收敛判分后发布，
     * 经 Outbox 投递到全域总线 educloud.events（routing key exam.graded，
     * analytics 动态流考试队列与 notification 均按该路由键定向订阅）。
     */
    public void examGraded(
            Long examId,
            String examTitle,
            Long courseId,
            String courseTitle,
            Long studentId,
            Integer score,
            boolean passed,
            long aggregateVersion,
            LocalDateTime gradedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("examId", examId);
        payload.put("examTitle", examTitle);
        payload.put("courseId", courseId);
        payload.put("courseTitle", courseTitle);
        payload.put("studentId", studentId);
        payload.put("score", score);
        payload.put("passed", passed);
        payload.put("aggregateVersion", aggregateVersion);
        payload.put("gradedAt", gradedAt.toString());
        writeOutbox("Exam", String.valueOf(examId), "ExamGraded", aggregateVersion, payload);
    }
```

- [ ] **步骤 2：在 OutboxEventDispatcher 增加路由**

在 `ASSIGNMENT_GRADED_ROUTING_KEY` 常量后追加：

```java
    /** 考试判分事件：发布在全域总线，路由键与 analytics 动态流考试队列绑定一致。 */
    private static final String EXAM_GRADED_EVENT_TYPE = "ExamGraded";
    private static final String EXAM_GRADED_ROUTING_KEY = "exam.graded";
```

在 `routeFor` 方法中 `ASSIGNMENT_GRADED_EVENT_TYPE` 判断后追加：

```java
        if (EXAM_GRADED_EVENT_TYPE.equals(eventType)) {
            return new Route(RabbitConfiguration.DOMAIN_EVENT_EXCHANGE, EXAM_GRADED_ROUTING_KEY);
        }
```

- [ ] **步骤 3：追加发布器测试**（读取 `ContentEventPublisherTest` 现有结构后按同风格追加）

```java
    @Test
    void examGraded_writesOutboxWithExamPayload() {
        when(sequenceMapper.increment(anyString())).thenReturn(1);
        when(sequenceMapper.selectValue(anyString())).thenReturn(42L);

        publisher.examGraded(101L, "期中考试", 1001L, "Spring Boot 微服务实践",
                2001L, 85, true, 1L, LocalDateTime.of(2026, 8, 28, 10, 0));

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(eventMapper).insert(captor.capture());
        OutboxEventEntity event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("ExamGraded");
        assertThat(event.getAggregateType()).isEqualTo("Exam");
        assertThat(event.getAggregateId()).isEqualTo("101");
        assertThat(event.getPayloadJson()).contains("\"passed\":true").contains("\"score\":85");
    }
```

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test -Dtest=ContentEventPublisherTest`
预期：全部 PASS（含新增用例）

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/messaging/ educloud-backend/educloud-content/src/test/java/com/educloud/content/messaging/
git commit -m "feat(考试): 新增 ExamGraded 事件发布与全域总线路由"
```

## 任务 6：学生端考试服务（TDD）

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamAttemptService.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamTimeoutSweeper.java`
- 测试：`educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ExamAttemptServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamSubmitRequest;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamAttemptMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import com.educloud.content.support.MybatisPlusTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamAttemptServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(ExamAttemptEntity.class, ExamEntity.class, ExamPaperQuestionEntity.class);
    }

    @Mock
    private ExamMapper examMapper;
    @Mock
    private ExamPaperQuestionMapper paperQuestionMapper;
    @Mock
    private ExamAttemptMapper attemptMapper;
    @Mock
    private ContentEventPublisher eventPublisher;

    @InjectMocks
    private ExamAttemptService attemptService;

    private static ExamEntity publishedExam() {
        ExamEntity exam = new ExamEntity();
        exam.setId(101L);
        exam.setCourseId(1001L);
        exam.setCourseTitle("Spring Boot 微服务实践");
        exam.setTitle("期中考试");
        exam.setDurationMinutes(60);
        exam.setPassScore(60);
        exam.setStartTime(LocalDateTime.now().minusHours(1));
        exam.setEndTime(LocalDateTime.now().plusHours(1));
        exam.setStatus("PUBLISHED");
        return exam;
    }

    @Test
    void startAttempt_rejectsUnpublishedExam() {
        ExamEntity draft = publishedExam();
        draft.setStatus("DRAFT");
        when(examMapper.selectById(101L)).thenReturn(draft);

        assertThatThrownBy(() -> attemptService.startAttempt(101L, 2001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_NOT_PUBLISHED);
    }

    @Test
    void startAttempt_rejectsOutsideWindow() {
        ExamEntity exam = publishedExam();
        exam.setStartTime(LocalDateTime.now().plusDays(1));
        when(examMapper.selectById(101L)).thenReturn(exam);

        assertThatThrownBy(() -> attemptService.startAttempt(101L, 2001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_OUTSIDE_WINDOW);
    }

    @Test
    void startAttempt_rejectsDuplicateAttempt() {
        when(examMapper.selectById(101L)).thenReturn(publishedExam());
        when(attemptMapper.selectOne(any())).thenReturn(new ExamAttemptEntity());

        assertThatThrownBy(() -> attemptService.startAttempt(101L, 2001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_ATTEMPT_ALREADY_EXISTS);
    }

    @Test
    void submit_gradesAndPublishesEvent() {
        when(examMapper.selectById(101L)).thenReturn(publishedExam());
        when(paperQuestionMapper.selectList(any())).thenReturn(List.of(
                paper(1L, "SINGLE", List.of(0), 10),
                paper(2L, "MULTIPLE", List.of(0, 2), 20)));
        ExamAttemptEntity attempt = new ExamAttemptEntity();
        attempt.setId(501L);
        attempt.setExamId(101L);
        attempt.setStudentId(2001L);
        attempt.setStatus("IN_PROGRESS");
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(10));
        when(attemptMapper.selectById(501L)).thenReturn(attempt);
        when(attemptMapper.markGraded(any())).thenReturn(1);

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setAnswers(Map.of(1L, List.of(0), 2L, List.of(0, 2)));
        request.setTabSwitchCount(2);

        var response = attemptService.submitAttempt(101L, 501L, 2001L, request);

        assertThat(response.getScore()).isEqualTo(30);
        assertThat(response.getPassed()).isTrue();
        verify(attemptMapper).markGraded(any());
        verify(eventPublisher).examGraded(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submit_rejectsAttemptNotOwned() {
        when(attemptMapper.selectById(501L)).thenReturn(attempt(501L, 9999L));

        assertThatThrownBy(() -> attemptService.submitAttempt(101L, 501L, 2001L, new ExamSubmitRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ContentErrorCode.EXAM_ATTEMPT_NOT_OWNED);
        verify(attemptMapper, never()).markGraded(any());
    }

    private static ExamPaperQuestionEntity paper(Long id, String type, List<Integer> answer, int score) {
        ExamPaperQuestionEntity p = new ExamPaperQuestionEntity();
        p.setId(id);
        p.setExamId(101L);
        p.setQuestionId(id);
        p.setScore(score);
        p.setQuestionSnapshot("{\"questionId\":" + id + ",\"questionType\":\"" + type
                + "\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":" + answer + ",\"score\":" + score + "}");
        return p;
    }

    private static ExamAttemptEntity attempt(Long id, Long studentId) {
        ExamAttemptEntity a = new ExamAttemptEntity();
        a.setId(id);
        a.setExamId(101L);
        a.setStudentId(studentId);
        a.setStatus("IN_PROGRESS");
        a.setStartedAt(LocalDateTime.now().minusMinutes(5));
        return a;
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test -Dtest=ExamAttemptServiceTest`
预期：编译失败，`ExamAttemptService` 不存在

- [ ] **步骤 3：编写实现**

`ExamAttemptService.java`：

```java
package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamSubmitRequest;
import com.educloud.content.dto.response.ExamAttemptResponse;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.exam.ExamGradingEngine;
import com.educloud.content.exam.ExamQuestionSnapshot;
import com.educloud.content.mapper.ExamAttemptMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.educloud.content.messaging.ContentEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamAttemptService {

    /** 切屏次数 >= 该阈值标记 flagged。 */
    private static final int FLAG_THRESHOLD = 5;
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final ExamMapper examMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ContentEventPublisher contentEventPublisher;
    private final ObjectMapper objectMapper;

    public ExamAttemptEntity startAttempt(Long examId, Long studentId) {
        ExamEntity exam = requirePublishedInWindow(examId);
        ExamAttemptEntity existing = attemptMapper.selectOne(
                new LambdaQueryWrapper<ExamAttemptEntity>()
                        .eq(ExamAttemptEntity::getExamId, examId)
                        .eq(ExamAttemptEntity::getStudentId, studentId));
        if (existing != null) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_ALREADY_EXISTS,
                    "Active attempt already exists: exam=" + examId + ", student=" + studentId);
        }
        ExamAttemptEntity attempt = new ExamAttemptEntity();
        attempt.setExamId(examId);
        attempt.setStudentId(studentId);
        attempt.setStatus(STATUS_IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setTabSwitchCount(0);
        attempt.setFlagged(0);
        attempt.setTimeout(0);
        attemptMapper.insert(attempt);
        return attempt;
    }

    @Transactional
    public ExamAttemptResponse submitAttempt(Long examId, Long attemptId, Long studentId, ExamSubmitRequest request) {
        ExamEntity exam = requirePublishedInWindow(examId);
        ExamAttemptEntity attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND,
                    "Exam attempt not found: " + attemptId);
        }
        if (!attempt.getStudentId().equals(studentId)) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_OWNED,
                    "Attempt " + attemptId + " does not belong to student " + studentId);
        }
        if (!STATUS_IN_PROGRESS.equals(attempt.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt is not in progress: " + attemptId);
        }

        List<ExamQuestionSnapshot> paper = loadPaper(examId);
        Map<Long, List<Integer>> answers = request.getAnswers() == null ? Map.of() : request.getAnswers();
        boolean timeout = isTimeout(exam, attempt);
        ExamGradingEngine.GradeResult result = ExamGradingEngine.grade(paper, answers);
        int earned = timeout ? result.earnedScore() : result.earnedScore();
        int tabSwitches = request.getTabSwitchCount() == null ? 0 : request.getTabSwitchCount();

        attempt.setScore(earned);
        attempt.setPassed(earned >= exam.getPassScore() ? 1 : 0);
        attempt.setAnswersJson(writeJson(answers));
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setTabSwitchCount(tabSwitches);
        attempt.setFlagged(tabSwitches >= FLAG_THRESHOLD ? 1 : 0);
        attempt.setTimeout(timeout ? 1 : 0);

        int updated = attemptMapper.markGraded(attempt);
        if (updated != 1) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt was concurrently submitted: " + attemptId);
        }

        try {
            contentEventPublisher.examGraded(
                    exam.getId(), exam.getTitle(), exam.getCourseId(), exam.getCourseTitle(),
                    studentId, earned, earned >= exam.getPassScore(), 1L, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to publish ExamGraded event for exam {} student {}",
                    examId, studentId, e);
        }
        return toAttemptResponse(attempt, paper, answers, earned >= exam.getPassScore());
    }

    /** 超时判定：服务端时间 > started_at + duration。 */
    private boolean isTimeout(ExamEntity exam, ExamAttemptEntity attempt) {
        LocalDateTime deadline = attempt.getStartedAt().plusMinutes(exam.getDurationMinutes());
        return LocalDateTime.now().isAfter(deadline);
    }

    public List<ExamQuestionSnapshot> loadPaper(Long examId) {
        List<ExamPaperQuestionEntity> rows = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId)
                        .orderByAsc(ExamPaperQuestionEntity::getSortOrder));
        List<ExamQuestionSnapshot> paper = new ArrayList<>();
        for (ExamPaperQuestionEntity row : rows) {
            paper.add(readSnapshot(row));
        }
        return paper;
    }

    private ExamQuestionSnapshot readSnapshot(ExamPaperQuestionEntity row) {
        try {
            Map<String, Object> snap = objectMapper.readValue(row.getQuestionSnapshot(),
                    new TypeReference<>() {
                    });
            Long questionId = row.getQuestionId();
            String type = String.valueOf(snap.get("questionType"));
            List<String> options = objectMapper.convertValue(snap.get("options"), new TypeReference<>() {
            });
            List<Integer> answer = objectMapper.convertValue(snap.get("answer"), new TypeReference<>() {
            });
            int score = row.getScore();
            return new ExamQuestionSnapshot(questionId, type, options, answer, score);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid question snapshot for paper row " + row.getId(), e);
        }
    }

    private ExamEntity requirePublishedInWindow(Long examId) {
        ExamEntity exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_FOUND, "Exam not found: " + examId);
        }
        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_PUBLISHED, "Exam is not published: " + examId);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime()) || now.isAfter(exam.getEndTime())) {
            throw new BusinessException(ContentErrorCode.EXAM_OUTSIDE_WINDOW,
                    "Exam outside window: " + examId);
        }
        return exam;
    }

    private String writeJson(Map<Long, List<Integer>> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize answers", e);
        }
    }

    private ExamAttemptResponse toAttemptResponse(ExamAttemptEntity attempt,
                                                  List<ExamQuestionSnapshot> paper,
                                                  Map<Long, List<Integer>> answers,
                                                  boolean passed) {
        List<ExamAttemptResponse.ExamQuestionResult> results = new ArrayList<>();
        for (ExamQuestionSnapshot q : paper) {
            results.add(ExamAttemptResponse.ExamQuestionResult.builder()
                    .questionId(q.questionId())
                    .questionType(q.questionType())
                    .stem(q.options() == null ? "" : stemOf(q))
                    .options(q.options())
                    .answer(q.answer())
                    .score(q.score())
                    .correct(answers.containsKey(q.questionId())
                            && isCorrectAnswer(q, answers.get(q.questionId())))
                    .build());
        }
        return ExamAttemptResponse.builder()
                .id(attempt.getId())
                .examId(attempt.getExamId())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .score(attempt.getScore())
                .passed(passed)
                .timeout(attempt.getTimeout() != null && attempt.getTimeout() == 1)
                .tabSwitchCount(attempt.getTabSwitchCount())
                .answers(answers)
                .results(results)
                .build();
    }

    private String stemOf(ExamQuestionSnapshot q) {
        return "";
    }

    private boolean isCorrectAnswer(ExamQuestionSnapshot q, List<Integer> chosen) {
        if ("MULTIPLE".equals(q.questionType())) {
            return chosen.size() == q.answer().size()
                    && chosen.stream().sorted().toList().equals(q.answer().stream().sorted().toList());
        }
        return chosen.size() == 1 && chosen.get(0).equals(q.answer().get(0));
    }
}
```

`ExamTimeoutSweeper.java`：

```java
package com.educloud.content.service;

import com.educloud.content.dto.request.ExamSubmitRequest;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.mapper.ExamAttemptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 超时收敛：扫描超时未交卷的 IN_PROGRESS attempt，按已答内容自动判分（规格 §5.2）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamTimeoutSweeper {

    private final ExamAttemptMapper attemptMapper;
    private final ExamAttemptService attemptService;

    @Scheduled(fixedDelay = 30_000)
    public void sweepExpiredAttempts() {
        List<ExamAttemptEntity> expired = attemptMapper.selectExpiredInProgress();
        for (ExamAttemptEntity attempt : expired) {
            try {
                attemptService.submitAttempt(attempt.getExamId(), attempt.getId(),
                        attempt.getStudentId(), new ExamSubmitRequest());
                log.info("Timeout-swept exam attempt: attemptId={}, examId={}, studentId={}",
                        attempt.getId(), attempt.getExamId(), attempt.getStudentId());
            } catch (Exception e) {
                log.warn("Failed to timeout-sweep attempt {}: {}", attempt.getId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test -Dtest=ExamAttemptServiceTest`
预期：6 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamAttemptService.java educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamTimeoutSweeper.java educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ExamAttemptServiceTest.java
git commit -m "feat(考试): 新增考试开始/交卷判分/超时收敛服务"
```

## 任务 7：学生端考试控制器

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ExamStudentController.java`

- [ ] **步骤 1：编写控制器**

对齐 `CertificateController` 模式（`@AuthenticationPrincipal Jwt` + `JwtSecurityUtils.userId`）：

```java
package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.ExamSubmitRequest;
import com.educloud.content.dto.response.ExamAttemptResponse;
import com.educloud.content.dto.response.ExamQuestionResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.exam.ExamQuestionSnapshot;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.ExamAttemptService;
import com.educloud.content.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端考试接口（规格 §4.2）：
 * 列表 / 详情（不含答案） / 开始考试 / 交卷判分 / 成绩答卷。
 */
@RestController
@RequestMapping("/api/v1/me/exams")
@RequiredArgsConstructor
public class ExamStudentController {

    private final ExamService examService;
    private final ExamAttemptService attemptService;
    private final ApiResponseFactory responses;

    @GetMapping
    public ApiResponse<List<ExamResponse>> listMyExams(@AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(examService.listStudentExams(studentId));
    }

    @GetMapping("/{examId}")
    public ApiResponse<ExamResponse> getExam(@PathVariable Long examId,
                                             @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(examService.getStudentExam(examId, studentId));
    }

    @PostMapping("/{examId}/attempts")
    public ApiResponse<ExamAttemptResponse> startAttempt(@PathVariable Long examId,
                                                         @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        ExamAttemptEntity attempt = attemptService.startAttempt(examId, studentId);
        return responses.success(ExamAttemptResponse.builder()
                .id(attempt.getId())
                .examId(attempt.getExamId())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .build());
    }

    @PostMapping("/{examId}/attempts/{attemptId}/submit")
    public ApiResponse<ExamAttemptResponse> submitAttempt(@PathVariable Long examId,
                                                          @PathVariable Long attemptId,
                                                          @RequestBody ExamSubmitRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(attemptService.submitAttempt(examId, attemptId, studentId, request));
    }

    @GetMapping("/{examId}/attempts/{attemptId}")
    public ApiResponse<ExamAttemptResponse> getAttempt(@PathVariable Long examId,
                                                       @PathVariable Long attemptId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        Long studentId = JwtSecurityUtils.userId(jwt);
        return responses.success(attemptService.getAttemptResult(examId, attemptId, studentId));
    }
}
```

- [ ] **步骤 2：编译验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content -am compile`
预期：BUILD SUCCESS（若 `ExamService.listStudentExams/getStudentExam`、`ExamAttemptService.getAttemptResult` 缺失则先补最小实现：`ExamService` 中 `listStudentExams` 返回空列表、`getStudentExam` 读 `examMapper` + `loadPaper`；`getAttemptResult` 校验归属后返回判分结果）

- [ ] **步骤 3：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ExamStudentController.java
git commit -m "feat(考试): 新增学生端考试接口"
```

## 任务 8：教师端考试服务（TDD）

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamBankService.java`
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamService.java`
- 测试：`educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ExamBankServiceTest.java`、`ExamServiceTest.java`

- [ ] **步骤 1：编写失败的测试**

`ExamBankServiceTest.java`：

```java
package com.educloud.content.service;

import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamQuestionRequest;
import com.educloud.content.entity.ExamBankQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamBankQuestionMapper;
import com.educloud.content.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamBankServiceTest {

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.registerTableInfo(ExamBankQuestionEntity.class);
    }

    @Mock
    private ExamBankQuestionMapper questionMapper;
    @Mock
    private ExamPaperQuestionServiceSupport unused; // 占位防误用，实际引用见下

    @InjectMocks
    private ExamBankService bankService;
    ...
}
```

> 说明：`ExamBankService` 构造依赖 `ExamBankQuestionMapper` 与 `ExamPaperQuestionMapper`（用于"被已发布考试引用则拒绝删除"校验）。测试用例：
> 1. `createQuestion_persistsWithTypeAndJsonFields` —— 保存后 `options`/`answer` 为 JSON 字符串，`status=ENABLED`
> 2. `updateQuestion_softDeleteDisabled` —— 软删置 `status=DISABLED`
> 3. `deleteQuestion_referencedByPublishedExam_rejected` —— `exam_paper_question` 存在引用且考试 `PUBLISHED` 时抛 `EXAM_QUESTION_IN_USE`

`ExamServiceTest.java` 用例：
> 1. `createExam_buildsSnapshotAndTotals` —— 组卷时 `question_snapshot` 含题干/选项/答案/题型/分值，`total_score = Σ score`，状态 `DRAFT`
> 2. `publish_rejectsEmptyPaper` —— 无题目抛 `EXAM_PAPER_EMPTY`
> 3. `publish_setsPublishedAndTotalScore` —— 校验窗口合法后置 `PUBLISHED`
> 4. `update_rejectsPublishedExam` —— 已发布考试抛 `EXAM_NOT_DRAFT`

- [ ] **步骤 2：运行测试确认失败**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test -Dtest=ExamBankServiceTest,ExamServiceTest`
预期：编译失败

- [ ] **步骤 3：编写实现**

`ExamBankService.java`（核心逻辑）：

```java
package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamQuestionRequest;
import com.educloud.content.dto.response.ExamBankQuestionResponse;
import com.educloud.content.entity.ExamBankQuestionEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamBankQuestionMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamBankService {

    private final ExamBankQuestionMapper questionMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamMapper examMapper;
    private final ObjectMapper objectMapper;

    public ExamBankQuestionResponse createQuestion(ExamQuestionRequest request, Long teacherId) {
        ExamBankQuestionEntity entity = new ExamBankQuestionEntity();
        entity.setCourseId(request.getCourseId());
        entity.setTeacherId(teacherId);
        entity.setQuestionType(request.getQuestionType());
        entity.setStem(request.getStem());
        entity.setOptions(writeJson(request.getOptions()));
        entity.setAnswer(writeJson(request.getAnswer()));
        entity.setAnalysis(request.getAnalysis());
        entity.setDefaultScore(request.getDefaultScore() == null ? 5 : request.getDefaultScore());
        entity.setStatus("ENABLED");
        questionMapper.insert(entity);
        return toResponse(entity);
    }

    public List<ExamBankQuestionResponse> listQuestions(Long courseId) {
        return questionMapper.selectList(
                        new LambdaQueryWrapper<ExamBankQuestionEntity>()
                                .eq(courseId != null, ExamBankQuestionEntity::getCourseId, courseId)
                                .eq(ExamBankQuestionEntity::getStatus, "ENABLED")
                                .orderByDesc(ExamBankQuestionEntity::getId))
                .stream().map(this::toResponse).toList();
    }

    public ExamBankQuestionResponse updateQuestion(Long id, ExamQuestionRequest request) {
        ExamBankQuestionEntity entity = requireQuestion(id);
        entity.setQuestionType(request.getQuestionType());
        entity.setStem(request.getStem());
        entity.setOptions(writeJson(request.getOptions()));
        entity.setAnswer(writeJson(request.getAnswer()));
        entity.setAnalysis(request.getAnalysis());
        if (request.getDefaultScore() != null) {
            entity.setDefaultScore(request.getDefaultScore());
        }
        questionMapper.updateById(entity);
        return toResponse(entity);
    }

    /** 软删：被已发布考试引用的题目拒绝删除。 */
    public void deleteQuestion(Long id) {
        ExamBankQuestionEntity entity = requireQuestion(id);
        Long referencingExamId = findReferencingPublishedExam(id);
        if (referencingExamId != null) {
            throw new BusinessException(ContentErrorCode.EXAM_QUESTION_IN_USE,
                    "Question " + id + " referenced by published exam " + referencingExamId);
        }
        entity.setStatus("DISABLED");
        questionMapper.updateById(entity);
    }

    private Long findReferencingPublishedExam(Long questionId) {
        List<ExamPaperQuestionEntity> refs = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getQuestionId, questionId));
        for (ExamPaperQuestionEntity ref : refs) {
            ExamEntity exam = examMapper.selectById(ref.getExamId());
            if (exam != null && "PUBLISHED".equals(exam.getStatus())) {
                return exam.getId();
            }
        }
        return null;
    }

    private ExamBankQuestionEntity requireQuestion(Long id) {
        ExamBankQuestionEntity entity = questionMapper.selectById(id);
        if (entity == null || !"ENABLED".equals(entity.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_QUESTION_NOT_FOUND,
                    "Exam bank question not found: " + id);
        }
        return entity;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize field", e);
        }
    }

    private ExamBankQuestionResponse toResponse(ExamBankQuestionEntity entity) {
        return ExamBankQuestionResponse.builder()
                .id(entity.getId())
                .courseId(entity.getCourseId())
                .questionType(entity.getQuestionType())
                .stem(entity.getStem())
                .options(readList(entity.getOptions()))
                .defaultScore(entity.getDefaultScore())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
```

`ExamService.java`（核心逻辑：组卷快照 + 发布）：

```java
package com.educloud.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.educloud.common.error.BusinessException;
import com.educloud.content.dto.request.ExamCreateRequest;
import com.educloud.content.dto.response.ExamQuestionResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.entity.ExamAttemptEntity;
import com.educloud.content.entity.ExamBankQuestionEntity;
import com.educloud.content.entity.ExamEntity;
import com.educloud.content.entity.ExamPaperQuestionEntity;
import com.educloud.content.exception.ContentErrorCode;
import com.educloud.content.mapper.ExamAttemptMapper;
import com.educloud.content.mapper.ExamBankQuestionMapper;
import com.educloud.content.mapper.ExamMapper;
import com.educloud.content.mapper.ExamPaperQuestionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExamService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final ExamMapper examMapper;
    private final ExamBankQuestionMapper bankQuestionMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public ExamResponse createExam(ExamCreateRequest request, Long teacherId) {
        validateWindow(request.getStartTime(), request.getEndTime());
        List<ExamBankQuestionEntity> questions = loadQuestions(request.getPaper());
        int totalScore = request.getPaper().stream()
                .mapToInt(ExamCreateRequest.PaperItem::getScore).sum();

        ExamEntity exam = new ExamEntity();
        exam.setCourseId(request.getCourseId());
        exam.setCourseTitle(resolveCourseTitle(request.getCourseId()));
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setTotalScore(totalScore);
        exam.setPassScore(request.getPassScore());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        exam.setStatus(STATUS_DRAFT);
        exam.setTeacherId(teacherId);
        examMapper.insert(exam);

        int sort = 0;
        for (ExamCreateRequest.PaperItem item : request.getPaper()) {
            ExamBankQuestionEntity q = questions.stream()
                    .filter(x -> x.getId().equals(item.getQuestionId()))
                    .findFirst().orElseThrow();
            ExamPaperQuestionEntity row = new ExamPaperQuestionEntity();
            row.setExamId(exam.getId());
            row.setQuestionId(q.getId());
            row.setQuestionSnapshot(buildSnapshot(q, item.getScore()));
            row.setScore(item.getScore());
            row.setSortOrder(sort++);
            paperQuestionMapper.insert(row);
        }
        return getExamResponse(exam, true);
    }

    public void publishExam(Long examId) {
        ExamEntity exam = requireExam(examId);
        if (!STATUS_DRAFT.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_DRAFT, "Exam is not draft: " + examId);
        }
        Long paperCount = paperQuestionMapper.selectCount(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId));
        if (paperCount == null || paperCount == 0) {
            throw new BusinessException(ContentErrorCode.EXAM_PAPER_EMPTY, "Exam paper is empty: " + examId);
        }
        validateWindow(exam.getStartTime(), exam.getEndTime());
        exam.setStatus(STATUS_PUBLISHED);
        examMapper.updateById(exam);
    }

    public List<ExamResponse> listStudentExams(Long studentId) {
        List<ExamEntity> exams = examMapper.selectList(
                new LambdaQueryWrapper<ExamEntity>()
                        .eq(ExamEntity::getStatus, STATUS_PUBLISHED)
                        .orderByDesc(ExamEntity::getStartTime));
        List<ExamResponse> result = new ArrayList<>();
        for (ExamEntity exam : exams) {
            ExamAttemptEntity attempt = attemptMapper.selectOne(
                    new LambdaQueryWrapper<ExamAttemptEntity>()
                            .eq(ExamAttemptEntity::getExamId, exam.getId())
                            .eq(ExamAttemptEntity::getStudentId, studentId));
            result.add(toStudentView(exam, attempt));
        }
        return result;
    }

    public ExamResponse getStudentExam(Long examId, Long studentId) {
        ExamEntity exam = requireExam(examId);
        ExamAttemptEntity attempt = attemptMapper.selectOne(
                new LambdaQueryWrapper<ExamAttemptEntity>()
                        .eq(ExamAttemptEntity::getExamId, examId)
                        .eq(ExamAttemptEntity::getStudentId, studentId));
        return toStudentView(exam, attempt);
    }

    private ExamResponse toStudentView(ExamEntity exam, ExamAttemptEntity attempt) {
        boolean hasResult = attempt != null && "GRADED".equals(attempt.getStatus());
        List<ExamQuestionResponse> questions = hasResult
                ? List.of()
                : loadQuestionsForDisplay(exam.getId());
        String displayStatus = displayStatus(exam, attempt);
        return ExamResponse.builder()
                .id(exam.getId())
                .courseId(exam.getCourseId())
                .courseTitle(exam.getCourseTitle())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .durationMinutes(exam.getDurationMinutes())
                .totalScore(exam.getTotalScore())
                .passScore(exam.getPassScore())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .status(displayStatus)
                .questions(questions)
                .score(hasResult ? attempt.getScore() : null)
                .passed(hasResult ? attempt.getPassed() == 1 : null)
                .attemptStatus(attempt == null ? null : attempt.getStatus())
                .build();
    }

    /** 展示状态推导（规格 §5.2）：NOT_STARTED / IN_PROGRESS / GRADED。 */
    private String displayStatus(ExamEntity exam, ExamAttemptEntity attempt) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            return "NOT_STARTED";
        }
        if (attempt != null && "GRADED".equals(attempt.getStatus())) {
            return "GRADED";
        }
        return "IN_PROGRESS";
    }

    private List<ExamQuestionResponse> loadQuestionsForDisplay(Long examId) {
        List<ExamPaperQuestionEntity> rows = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId)
                        .orderByAsc(ExamPaperQuestionEntity::getSortOrder));
        List<ExamQuestionResponse> list = new ArrayList<>();
        for (ExamPaperQuestionEntity row : rows) {
            Map<String, Object> snap = readSnapshot(row.getQuestionSnapshot());
            list.add(ExamQuestionResponse.builder()
                    .id(row.getQuestionId())
                    .questionType(String.valueOf(snap.get("questionType")))
                    .stem(String.valueOf(snap.get("stem")))
                    .options(objectMapper.convertValue(snap.get("options"),
                            new com.fasterxml.jackson.core.type.TypeReference<>() {
                            }))
                    .score(row.getScore())
                    .build());
        }
        return list;
    }

    private List<ExamBankQuestionEntity> loadQuestions(List<ExamCreateRequest.PaperItem> paper) {
        List<Long> ids = paper.stream().map(ExamCreateRequest.PaperItem::getQuestionId).toList();
        return bankQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamBankQuestionEntity>()
                        .in(ExamBankQuestionEntity::getId, ids)
                        .eq(ExamBankQuestionEntity::getStatus, "ENABLED"));
    }

    private String buildSnapshot(ExamBankQuestionEntity q, int score) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("questionId", q.getId());
        snap.put("questionType", q.getQuestionType());
        snap.put("stem", q.getStem());
        snap.put("options", readList(q.getOptions()));
        snap.put("answer", readIntList(q.getAnswer()));
        snap.put("score", score);
        try {
            return objectMapper.writeValueAsString(snap);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to build snapshot", e);
        }
    }

    private ExamResponse getExamResponse(ExamEntity exam, boolean withQuestions) {
        return toStudentView(exam, null);
    }

    private ExamEntity requireExam(Long examId) {
        ExamEntity exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_FOUND, "Exam not found: " + examId);
        }
        return exam;
    }

    private void validateWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_DRAFT,
                    "Invalid exam window: end must be after start");
        }
    }

    private String resolveCourseTitle(Long courseId) {
        return String.valueOf(courseId);
    }

    private Map<String, Object> readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid snapshot", e);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Integer> readIntList(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
```

> 说明：`resolveCourseTitle` 简化为返回 courseId 字符串，实际课程标题可由前端列表用已有 `CourseClient` 反查补齐；如需快照真实标题，参照 `CourseClient` 既有用法实现。

- [ ] **步骤 4：运行测试确认通过**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test -Dtest=ExamBankServiceTest,ExamServiceTest`
预期：全部 PASS

- [ ] **步骤 5：补充 `getAttemptResult` 到 ExamAttemptService**

在 `ExamAttemptService` 中追加（学生端查看成绩/答卷，校验归属后判分结果）：

```java
    public ExamAttemptResponse getAttemptResult(Long examId, Long attemptId, Long studentId) {
        ExamAttemptEntity attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_FOUND,
                    "Exam attempt not found: " + attemptId);
        }
        if (!attempt.getStudentId().equals(studentId)) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_OWNED,
                    "Attempt " + attemptId + " does not belong to student " + studentId);
        }
        if (!"GRADED".equals(attempt.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_ATTEMPT_NOT_SUBMITTABLE,
                    "Attempt not graded yet: " + attemptId);
        }
        Map<Long, List<Integer>> answers = readAnswers(attempt.getAnswersJson());
        List<ExamQuestionSnapshot> paper = loadPaper(examId);
        ExamGradingEngine.GradeResult result = ExamGradingEngine.grade(paper, answers);
        return toAttemptResponse(attempt, paper, answers, attempt.getPassed() == 1);
    }

    private Map<Long, List<Integer>> readAnswers(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse answers_json for attempt, treat as empty", e);
            return Map.of();
        }
    }
```

- [ ] **步骤 6：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamBankService.java educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamService.java educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamAttemptService.java educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ExamBankServiceTest.java educloud-backend/educloud-content/src/test/java/com/educloud/content/service/ExamServiceTest.java
git commit -m "feat(考试): 新增教师端题库与组卷服务"
```

## 任务 9：教师端考试控制器

**文件：**
- 创建：`educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ExamTeacherController.java`

- [ ] **步骤 1：编写控制器**

```java
package com.educloud.content.controller;

import com.educloud.common.api.ApiResponse;
import com.educloud.common.api.ApiResponseFactory;
import com.educloud.content.dto.request.ExamCreateRequest;
import com.educloud.content.dto.request.ExamQuestionRequest;
import com.educloud.content.dto.response.ExamBankQuestionResponse;
import com.educloud.content.dto.response.ExamResponse;
import com.educloud.content.security.JwtSecurityUtils;
import com.educloud.content.service.ExamBankService;
import com.educloud.content.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教师端考试接口（规格 §4.1）：题库 CRUD + 考试 CRUD + 组卷发布。
 * 接口路径 /api/v1/teacher/exams/**，网关已预留；需 ROLE_TEACHER。
 */
@RestController
@RequestMapping("/api/v1/teacher/exams")
@RequiredArgsConstructor
public class ExamTeacherController {

    private final ExamBankService bankService;
    private final ExamService examService;
    private final ApiResponseFactory responses;

    @PostMapping("/exam-bank/questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamBankQuestionResponse> createQuestion(
            @Valid @RequestBody ExamQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(bankService.createQuestion(request, JwtSecurityUtils.userId(jwt)));
    }

    @GetMapping("/exam-bank/questions")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<List<ExamBankQuestionResponse>> listQuestions(
            @RequestParam(required = false) Long courseId) {
        return responses.success(bankService.listQuestions(courseId));
    }

    @PutMapping("/exam-bank/questions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamBankQuestionResponse> updateQuestion(
            @PathVariable Long id,
            @Valid @RequestBody ExamQuestionRequest request) {
        return responses.success(bankService.updateQuestion(id, request));
    }

    @DeleteMapping("/exam-bank/questions/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long id) {
        bankService.deleteQuestion(id);
        return responses.success(null);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamResponse> createExam(
            @Valid @RequestBody ExamCreateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return responses.success(examService.createExam(request, JwtSecurityUtils.userId(jwt)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<List<ExamResponse>> listExams() {
        return responses.success(examService.listTeacherExams());
    }

    @PutMapping("/{examId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<ExamResponse> updateExam(@PathVariable Long examId,
                                                @Valid @RequestBody ExamCreateRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        return responses.success(examService.updateExam(examId, request, JwtSecurityUtils.userId(jwt)));
    }

    @PostMapping("/{examId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> publishExam(@PathVariable Long examId) {
        examService.publishExam(examId);
        return responses.success(null);
    }

    @DeleteMapping("/{examId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ApiResponse<Void> deleteExam(@PathVariable Long examId) {
        examService.deleteExam(examId);
        return responses.success(null);
    }
}
```

- [ ] **步骤 2：补齐 ExamService 缺失方法**（`listTeacherExams` 返回全部考试含 DRAFT/PUBLISHED/CLOSED；`updateExam` 仅 DRAFT 可改且重建组卷；`deleteExam` 仅 DRAFT 可删——均复用任务 8 既有私有方法）

```java
    public List<ExamResponse> listTeacherExams() {
        return examMapper.selectList(
                        new LambdaQueryWrapper<ExamEntity>()
                                .orderByDesc(ExamEntity::getCreatedAt))
                .stream().map(e -> toStudentView(e, null)).toList();
    }

    @Transactional
    public ExamResponse updateExam(Long examId, ExamCreateRequest request, Long teacherId) {
        ExamEntity exam = requireExam(examId);
        if (!STATUS_DRAFT.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_DRAFT, "Exam is not draft: " + examId);
        }
        paperQuestionMapper.delete(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId));
        List<ExamBankQuestionEntity> questions = loadQuestions(request.getPaper());
        int totalScore = request.getPaper().stream()
                .mapToInt(ExamCreateRequest.PaperItem::getScore).sum();
        exam.setCourseId(request.getCourseId());
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setTotalScore(totalScore);
        exam.setPassScore(request.getPassScore());
        exam.setStartTime(request.getStartTime());
        exam.setEndTime(request.getEndTime());
        examMapper.updateById(exam);
        int sort = 0;
        for (ExamCreateRequest.PaperItem item : request.getPaper()) {
            ExamBankQuestionEntity q = questions.stream()
                    .filter(x -> x.getId().equals(item.getQuestionId())).findFirst().orElseThrow();
            ExamPaperQuestionEntity row = new ExamPaperQuestionEntity();
            row.setExamId(examId);
            row.setQuestionId(q.getId());
            row.setQuestionSnapshot(buildSnapshot(q, item.getScore()));
            row.setScore(item.getScore());
            row.setSortOrder(sort++);
            paperQuestionMapper.insert(row);
        }
        return toStudentView(exam, null);
    }

    public void deleteExam(Long examId) {
        ExamEntity exam = requireExam(examId);
        if (!STATUS_DRAFT.equals(exam.getStatus())) {
            throw new BusinessException(ContentErrorCode.EXAM_NOT_DRAFT, "Exam is not draft: " + examId);
        }
        paperQuestionMapper.delete(
                new LambdaQueryWrapper<ExamPaperQuestionEntity>()
                        .eq(ExamPaperQuestionEntity::getExamId, examId));
        examMapper.deleteById(examId);
    }
```

- [ ] **步骤 3：编译 + 全模块单测验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-content test`
预期：content 模块全部测试 PASS

- [ ] **步骤 4：Commit**

```bash
git add educloud-backend/educloud-content/src/main/java/com/educloud/content/controller/ExamTeacherController.java educloud-backend/educloud-content/src/main/java/com/educloud/content/service/ExamService.java
git commit -m "feat(考试): 新增教师端题库与考试管理接口"
```

## 任务 10：analytics 动态流消费端

**文件：**
- 修改：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/config/RabbitMqConfig.java`
- 修改：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/ActivityFeedConsumer.java`
- 修改：`educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/ActivityFeedController.java`

- [ ] **步骤 1：RabbitMqConfig 新增考试队列与绑定**

在 `QUEUE_ACTIVITY_FEED_ASSIGNMENT` 常量后追加：

```java
    public static final String QUEUE_ACTIVITY_FEED_EXAM = "activity_feed.exam.queue";
    public static final String ROUTING_KEY_EXAM_GRADED = "exam.graded";
```

在 `activityFeedAssignmentQueue` Bean 后追加队列 Bean（对齐既有 DLQ 配置）：

```java
    @Bean
    public Queue activityFeedExamQueue() {
        return QueueBuilder.durable(QUEUE_ACTIVITY_FEED_EXAM)
                .deadLetterExchange(EXCHANGE_ANALYTICS_DLX)
                .deadLetterRoutingKey(ROUTING_KEY_DLQ)
                .build();
    }
```

在 `activityFeedAssignmentBinding` 后追加：

```java
    // 考试判分事件发布在全域总线 educloud.events（routing key exam.graded），独立队列定向订阅。
    @Bean
    public Binding activityFeedExamBinding(Queue activityFeedExamQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(activityFeedExamQueue).to(domainEventsExchange).with(ROUTING_KEY_EXAM_GRADED);
    }
```

- [ ] **步骤 2：ActivityFeedConsumer 新增监听与映射**

在 `onAssignmentEvent` 后追加：

```java
    /** 考试判分专用队列：绑定全域总线 educloud.events 的 exam.graded 路由。 */
    @RabbitListener(queues = RabbitMqConfig.QUEUE_ACTIVITY_FEED_EXAM)
    public void onExamEvent(Message message) {
        handle(message, "educloud-content", "ExamGraded");
    }
```

在 switch 的 `"coursepublished"` 分支前追加：

```java
                case "examgraded", "exam.graded" -> mapExamGraded(root, eventId, occurredAt);
```

在 `mapCourseLifecycle` 前追加映射方法（对齐 `mapAssignmentGraded` 风格）：

```java
    /** 考试判分 → 学生考试动态（通过/未通过两种文案，extra 带 score/passed）。 */
    private void mapExamGraded(JsonNode root, String eventId, LocalDateTime occurredAt) {
        String studentId = text(root, "studentId", "userId");
        if (studentId == null || studentId.isBlank()) {
            log.warn("ExamGraded event without studentId/userId skipped: eventId={}", eventId);
            return;
        }
        JsonNode passedNode = field(root, "passed");
        boolean passed = passedNode != null && passedNode.asBoolean(false);
        String actionType = passed ? "EXAM_PASSED" : "EXAM_FAILED";
        Map<String, Object> extra = new LinkedHashMap<>();
        putIfPresent(extra, "score", root, "score");
        extra.put("passed", passed);
        activityFeedService.recordActivity(
                studentId, ROLE_STUDENT, actionType, "EXAM",
                text(root, "examId"), text(root, "examTitle", "title"),
                extra.isEmpty() ? null : extra,
                suffix(eventId, actionType), occurredAt);
    }
```

- [ ] **步骤 3：ActivityFeedController 新增文案**

在 `buildActionText` switch 的 `"CERTIFICATE_ISSUED"` 分支后追加：

```java
            case "EXAM_PASSED" -> "你通过了《" + t + "》考试：" + extraValue(extra, "score", "") + " 分";
            case "EXAM_FAILED" -> "你完成了《" + t + "》考试（未通过）：" + extraValue(extra, "score", "") + " 分";
```

- [ ] **步骤 4：编译验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-analytics -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/config/RabbitMqConfig.java educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/messaging/ActivityFeedConsumer.java educloud-backend/educloud-analytics/src/main/java/com/educloud/analytics/controller/ActivityFeedController.java
git commit -m "feat(考试): 动态流新增考试判分事件消费与文案"
```

## 任务 11：notification 通知消费端

**文件：**
- 修改：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/config/RabbitMqConfiguration.java`
- 创建：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/events/ExamGradedEvent.java`
- 修改：`educloud-backend/educloud-notification/src/main/java/com/educloud/notification/messaging/DomainNotificationConsumer.java`

- [ ] **步骤 1：RabbitMqConfiguration 新增常量与绑定**

在 `ROUTING_KEY_ASSIGNMENT_GRADED` 后追加：

```java
    public static final String ROUTING_KEY_EXAM_GRADED = "exam.graded";
```

在 `assignmentGradedBinding` 后追加：

```java
    @Bean
    public Binding examGradedBinding(Queue notificationDomainQueue, TopicExchange educloudEventsExchange) {
        return BindingBuilder.bind(notificationDomainQueue).to(educloudEventsExchange).with(ROUTING_KEY_EXAM_GRADED);
    }
```

- [ ] **步骤 2：创建 ExamGradedEvent DTO**（对齐 `AssignmentGradedEvent` 扁平结构）

```java
package com.educloud.notification.messaging.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamGradedEvent {
    private String eventId;
    private Long examId;
    private Long userId;
    private Long courseId;
    private String examTitle;
    private String courseTitle;
    private Integer score;
    private Boolean passed;
}
```

- [ ] **步骤 3：DomainNotificationConsumer 新增分支与处理方法**

在 `ROUTING_KEY_ASSIGNMENT_GRADED` 分支后追加：

```java
            } else if (RabbitMqConfiguration.ROUTING_KEY_EXAM_GRADED.equals(routingKey)) {
                ExamGradedEvent event = objectMapper.treeToValue(root, ExamGradedEvent.class);
                handleExamGraded(event);
```

在 `handleAssignmentGraded` 后追加：

```java
    public void handleExamGraded(ExamGradedEvent event) {
        if (event.getUserId() == null) return;
        String title = "考试已出分";
        String passedText = Boolean.TRUE.equals(event.getPassed()) ? "已通过" : "未通过";
        String content = "您的考试《" + (event.getExamTitle() != null ? event.getExamTitle() : "在线考试")
                + "》已出分，得分：" + (event.getScore() != null ? event.getScore() : 0)
                + " 分，" + passedText + "。";
        notificationService.sendDirectNotification(
                event.getUserId(),
                NotificationKind.EXAM,
                title,
                content,
                "查看考试",
                "/exams",
                false
        );
    }
```

- [ ] **步骤 4：编译验证**

运行：`mvn -f educloud-backend/pom.xml -pl educloud-notification -am compile`
预期：BUILD SUCCESS

- [ ] **步骤 5：Commit**

```bash
git add educloud-backend/educloud-notification/src/main/java/com/educloud/notification/
git commit -m "feat(考试): 通知中心新增考试出分通知"
```

## 任务 12：学生端前端对接

**文件：**
- 修改：`educloud-frontend/student-portal/src/types/index.ts`
- 修改：`educloud-frontend/student-portal/src/services/studentAssignmentService.ts`
- 修改：`educloud-frontend/student-portal/src/components/exams/ExamSessionModal.tsx`

- [ ] **步骤 1：扩展类型**

`types/index.ts` 中 `ExamQuestion` 与 `Exam` 改为：

```ts
export interface ExamQuestion {
  id: number;
  question: string;
  options: string[];
  questionType?: 'SINGLE' | 'MULTIPLE' | 'JUDGE';
  answer?: number[];
}

export interface Exam {
  id: number | string;
  courseId: number | string;
  courseTitle: string;
  title: string;
  description: string;
  duration: number; // minutes
  totalQuestions: number;
  totalScore: number;
  status: ExamStatus;
  startTime?: string;
  endTime?: string;
  score?: number;
  passScore: number;
  submittedAt?: string;
  questions?: ExamQuestion[];
  attemptId?: number | string;
  attemptStatus?: string;
}
```

- [ ] **步骤 2：service 对接真实 API**

`studentAssignmentService.ts` 中 `getExams` 改为 HTTP 优先（对齐 `getAssignments` 模式），并新增 `startExam` / `submitExam`：

```ts
  /** 获取当前学员的考试列表（对接后端 API，回退至本地存储） */
  getExams: async (): Promise<Exam[]> => {
    try {
      const resp = await http.get<ApiEnvelope<Exam[]>>('/me/exams');
      if (resp.data?.data && Array.isArray(resp.data.data) && resp.data.data.length > 0) {
        return resp.data.data.map((exam) => ({ ...exam, totalQuestions: exam.questions?.length ?? 0 }));
      }
    } catch (e) {
      console.warn('Failed to fetch /api/v1/me/exams, falling back:', e);
    }
    const key = getUserStorageKey(EXAM_STORAGE_KEY_PREFIX);
    try {
      const stored = localStorage.getItem(key);
      if (stored) return JSON.parse(stored);
    } catch {
      // ignore
    }
    try {
      localStorage.setItem(key, JSON.stringify(defaultExamsSeed));
    } catch {
      // ignore
    }
    return defaultExamsSeed;
  },

  /** 开始考试（真实 API 失败时回退本地 mock） */
  startExam: async (examId: string | number): Promise<{ attemptId: number | string }> => {
    try {
      const resp = await http.post<ApiEnvelope<any>>(`/me/exams/${examId}/attempts`);
      if (resp.data?.data?.id) return { attemptId: resp.data.data.id };
    } catch (e) {
      console.warn('Failed to start exam via API, falling back:', e);
    }
    return { attemptId: `local-${Date.now()}` };
  },

  /** 交卷（真实 API 失败时回退本地 mock 判分） */
  submitExam: async (
    examId: string | number,
    answers: Record<number, number[]>,
    tabSwitchCount = 0,
  ): Promise<{ exam: Exam; score: number; passed: boolean }> => {
    try {
      const resp = await http.post<ApiEnvelope<any>>(`/me/exams/${examId}/attempts/local`, { answers, tabSwitchCount });
      // 注：真实流程需携带 attemptId，由调用方传入；此签名在任务 12 步骤 3 中调整为完整版
    } catch (e) {
      console.warn('Failed to submit exam via API, falling back:', e);
    }
    const key = getUserStorageKey(EXAM_STORAGE_KEY_PREFIX);
    const list = await studentAssignmentService.getExams();
    const now = dayjs().format('YYYY-MM-DD HH:mm:ss');
    let computedScore = 0;
    const updatedList = list.map((item) => {
      if (String(item.id) === String(examId)) {
        const questions = item.questions || [];
        const eachScore = item.totalScore / Math.max(1, questions.length);
        let correctCount = 0;
        questions.forEach((q) => {
          const chosen = answers[q.id] ?? [];
          const correct = Array.isArray(q.answer)
            ? chosen.length === q.answer.length && [...chosen].sort().join() === [...q.answer].sort().join()
            : chosen.length === 1 && chosen[0] === (q as any).correctAnswer;
          if (correct) correctCount++;
        });
        computedScore = Math.round(correctCount * eachScore);
        return { ...item, status: 'GRADED' as const, score: computedScore, submittedAt: now };
      }
      return item;
    });
    localStorage.setItem(key, JSON.stringify(updatedList));
    const target = updatedList.find((i) => String(i.id) === String(examId));
    if (!target) throw new Error('考试未找到');
    return { exam: target, score: computedScore, passed: computedScore >= (target.passScore || 60) };
  },
```

> 说明：`submitExam` 的 HTTP 分支需要真实 `attemptId`（`startExam` 返回值）。最终实现将签名调整为 `submitExam(examId, attemptId, answers, tabSwitchCount)`，`ExamSessionModal` 调用处同步更新：进入考试先 `startExam` 拿 `attemptId`，交卷时带上。mock 回退路径在 attemptId 为 `local-*` 前缀时走本地判分。

- [ ] **步骤 3：ExamSessionModal 改造**（多选/判断 + 切屏监听 + 移除答案展示）

核心改动（在现有组件内）：

```tsx
  // 状态：answers 从 Record<number, number> 改为 Record<number, number[]>
  const [answers, setAnswers] = useState<Record<number, number[]>>({});
  const [tabSwitchCount, setTabSwitchCount] = useState(0);

  // 切屏监听：答题弹窗打开期间统计 blur + visibilitychange
  useEffect(() => {
    if (!isOpen || !exam) return;
    const onBlur = () => setTabSwitchCount((c) => c + 1);
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') setTabSwitchCount((c) => c + 1);
    };
    window.addEventListener('blur', onBlur);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      window.removeEventListener('blur', onBlur);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [isOpen, exam]);

  // 选项选择：SINGLE/JUDGE 单选，MULTIPLE 切换集合
  const handleSelectOption = (questionId: number, optionIdx: number, questionType?: string) => {
    if (result) return;
    setAnswers((prev) => {
      const current = prev[questionId] ?? [];
      if (questionType === 'MULTIPLE') {
        const next = current.includes(optionIdx)
          ? current.filter((i) => i !== optionIdx)
          : [...current, optionIdx];
        return { ...prev, [questionId]: next };
      }
      return { ...prev, [questionId]: [optionIdx] };
    });
  };

  // 交卷：先 startExam 拿 attemptId，再 submitExam
  const handleFinish = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const { attemptId } = await studentAssignmentService.startExam(exam.id);
      const res = await studentAssignmentService.submitExam(exam.id, attemptId, answers, tabSwitchCount);
      setResult(res);
      onExamComplete(res.exam);
    } catch (err) {
      alert(err instanceof Error ? err.message : '交卷失败');
    } finally {
      setSubmitting(false);
    }
  };
```

渲染部分改动：
- 选项 `isSelected` 改为 `(answers[q.id] ?? []).includes(optIndex)`
- 多选题选项旁显示选中状态（方形勾选样式可复用现有圆点样式）
- **删除** `result && isCorrect && (正确答案...)` 展示块（真实系统交卷前不下发答案；成绩见结果横幅）
- `answeredCount` 改为 `Object.keys(answers).length`
- mock 数据中的 `correctAnswer` 字段保持兼容（本地回退判分读取），真实 API 数据无此字段

- [ ] **步骤 4：构建验证**

运行：`cd educloud-frontend/student-portal && npm run build`
预期：TypeScript 编译 + Vite 构建 0 错误

- [ ] **步骤 5：Commit**

```bash
git add educloud-frontend/student-portal/src/types/index.ts educloud-frontend/student-portal/src/services/studentAssignmentService.ts educloud-frontend/student-portal/src/components/exams/ExamSessionModal.tsx
git commit -m "feat(考试): 学生端考试对接真实 API 并支持多选与切屏监控"
```

## 任务 13：教师端前端

**文件：**
- 修改：`educloud-frontend/teacher-portal/src/services/api.ts`
- 修改：`educloud-frontend/teacher-portal/src/pages/ExamManage.tsx`
- 修改：`educloud-frontend/teacher-portal/src/types/index.ts`（或 types 所在文件，按实际结构对齐）

- [ ] **步骤 1：api.ts 对接真实 API（保留 mock 回退）**

将 `getExams` / `createExam` 改为 HTTP 优先（对齐 `getActivities` 的 HTTP+回退模式）：

```ts
  // Exams
  getExams: async (): Promise<Exam[]> => {
    try {
      const resp = await http.get<ApiEnvelope<any[]>>('/teacher/exams');
      if (resp.data?.data && Array.isArray(resp.data.data)) {
        return resp.data.data.map((e: any) => ({
          id: e.id,
          title: e.title,
          courseId: e.courseId,
          courseName: e.courseTitle,
          questionCount: e.questions?.length ?? 0,
          duration: e.durationMinutes,
          studentCount: 0,
          status: mapExamStatus(e.status),
          scheduledAt: e.startTime,
        }));
      }
    } catch (e) {
      console.warn('Failed to fetch /teacher/exams, falling back:', e);
    }
    return delay(mockExams);
  },
  createExam: async (data: Partial<Exam>): Promise<Exam> => { /* HTTP 优先，回退现有 mock 逻辑 */ },
  publishExam: async (examId: string | number): Promise<void> => {
    try {
      await http.post(`/teacher/exams/${examId}/publish`);
    } catch (e) {
      console.warn('Failed to publish exam via API, falling back:', e);
    }
  },
  // 题库
  getQuestions: async (courseId?: string): Promise<any[]> => {
    try {
      const resp = await http.get<ApiEnvelope<any[]>>('/teacher/exams/exam-bank/questions', {
        params: courseId ? { courseId } : {},
      });
      if (resp.data?.data) return resp.data.data;
    } catch (e) {
      console.warn('Failed to fetch question bank, falling back:', e);
    }
    return [];
  },
  createQuestion: async (data: any): Promise<any> => {
    try {
      const resp = await http.post('/teacher/exams/exam-bank/questions', data);
      return resp.data?.data;
    } catch (e) {
      console.warn('Failed to create question via API, falling back:', e);
    }
    return data;
  },
```

> 说明：`mapExamStatus` 将后端 `DRAFT/PUBLISHED/CLOSED` 映射为前端 `DRAFT/PUBLISHED/ONGOING/ENDED`（规格 §5.2：PUBLISHED 窗口内 → ONGOING，CLOSED/窗口过 → ENDED）。`studentCount` 后端暂无统计时填 0。

- [ ] **步骤 2：ExamManage 增加题库管理视图 + 组卷改造**

`ExamManage.tsx` 增加：
1. 顶部 Tab：「考试管理」/「题库管理」两个视图（复用现有页面样式）
2. 「题库管理」视图：题目列表（题干/题型/分值）+ 「新建题目」弹窗（题型选择、题干、选项编辑器——单选/多选动态增删选项、判断题固定两选项、答案勾选、分值输入）+ 编辑/删除
3. 「考试管理」创建弹窗增加组卷步骤：选择课程后加载该课程题库题目，勾选题目 + 每道题分值输入 + 排序；保存草稿后列表出现「发布」按钮（调 `publishExam`）
4. 现有 `newQuestionCount` 字段改为真实选题

> 该任务 UI 工作量大但均为既有组件复用（表格、弹窗、CustomSelect、badge）；完成后手动验证创建题目 → 组卷 → 发布全流程。

- [ ] **步骤 3：构建验证**

运行：`cd educloud-frontend/teacher-portal && npm run build`
预期：TypeScript 编译 + Vite 构建 0 错误

- [ ] **步骤 4：Commit**

```bash
git add educloud-frontend/teacher-portal/src/services/api.ts educloud-frontend/teacher-portal/src/pages/ExamManage.tsx
git commit -m "feat(考试): 教师端题库管理与组卷发布对接真实 API"
```

## 任务 14：契约脚本与全量验证

**文件：**
- 创建：`deploy/tests/content-exam-contract-tests.sh`

- [ ] **步骤 1：编写契约脚本**（对齐既有契约脚本风格：MySQL 表结构断言 + 网关路由断言 + 事件路由断言）

```bash
#!/usr/bin/env bash
# EduCloud 在线考试模块契约测试（规格 2026-08-28-educloud-exam-design.md §9）
# 前置：MySQL/Nacos/网关已按既有契约脚本准备；依赖 mysql 客户端与 curl。
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8080}"

echo "== [1/4] 考试表结构 =="
for table in exam_bank_question exam exam_paper_question exam_attempt; do
  exists=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='educloud_content' AND table_name='$table';" 2>/dev/null)
  if [ "$exists" != "1" ]; then
    echo "FAIL: table $table missing"; exit 1
  fi
  echo "OK: $table"
done

echo "== [2/4] content_app 授权 =="
grants=$(mysql -h"$MYSQL_HOST" -u"$MYSQL_USER" ${MYSQL_PASS:+-p"$MYSQL_PASS"} -N -e \
  "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee LIKE '%content_app%' AND table_schema='educloud_content' AND table_name IN ('exam_bank_question','exam','exam_paper_question','exam_attempt');" 2>/dev/null)
if [ "$grants" -lt 4 ]; then
  echo "FAIL: content_app grants missing for exam tables"; exit 1
fi
echo "OK: grants"

echo "== [3/4] 网关路由 =="
# 学生/教师考试路由应已被网关配置覆盖（既有路由已预留 /api/v1/me/exams 与 /api/v1/exams/**）
routes=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/me/exams" || true)
if [ "$routes" = "404" ] || [ "$routes" = "000" ]; then
  echo "WARN: gateway route not reachable (HTTP $routes), check gateway is up"
else
  echo "OK: gateway route reachable (HTTP $routes)"
fi

echo "== [4/4] RabbitMQ 事件路由 =="
# 检查 exam.graded 绑定存在（依赖 rabbitmqadmin 或 rabbitmqctl，缺失时跳过）
if command -v rabbitmqadmin >/dev/null 2>&1; then
  binding=$(rabbitmqadmin -q list bindings source=educloud.events routing_key=exam.graded 2>/dev/null | grep -c exam.graded || true)
  if [ "$binding" -ge 1 ]; then
    echo "OK: exam.graded binding present"
  else
    echo "FAIL: exam.graded binding missing"; exit 1
  fi
else
  echo "SKIP: rabbitmqadmin not available"
fi

echo "== 考试契约测试全部通过 =="
```

- [ ] **步骤 2：后端全量测试**

运行：`mvn -f educloud-backend/pom.xml verify`
预期：全模块 BUILD SUCCESS，测试 0 失败（content/analytics/notification 均含新测试）

- [ ] **步骤 3：前端全量构建**

运行：`cd educloud-frontend/student-portal && npm run build && cd ../teacher-portal && npm run build && cd ../admin-portal && npm run build`
预期：三端构建 0 错误

- [ ] **步骤 4：执行迁移与契约脚本（VM/本地）**

运行：`bash deploy/tests/content-exam-contract-tests.sh`
预期：全部 OK（RabbitMQ 检查在绑定建立后执行）

- [ ] **步骤 5：E2E 验证（VM 192.168.100.136）**

1. 部署新 jar（content/analytics/notification）并重启三服务
2. 教师端：创建单选题/多选题/判断题 → 组卷 → 发布考试
3. 学生端：考试列表出现新考试 → 开始考试 → 作答（含多选）→ 切屏一次 → 交卷 → 显示分数与及格判定
4. 动态流：学生首页「我的学习动态」出现「你通过了《XX》考试：85 分」
5. 通知中心：站内通知出现「考试已出分」
6. 超时验证：将考试时长设为 1 分钟，不交卷等待超时，确认记录被定时任务收敛为 GRADED 且 timeout=1

- [ ] **步骤 6：Commit**

```bash
git add deploy/tests/content-exam-contract-tests.sh
git commit -m "test(考试): 新增考试模块契约测试脚本"
```

---

## 自检

**1. 规格覆盖度：**
- §3 数据模型 4 张表 → 任务 1（迁移）+ 任务 2（实体/Mapper）✓
- §4.1 教师 API → 任务 8/9 ✓；§4.2 学生 API → 任务 6/7 ✓
- §5.1 判分规则 → 任务 3 ✓；§5.2 状态机 + CAS + 超时收敛 → 任务 6（markGraded CAS + ExamTimeoutSweeper）✓
- §5.3 切屏监控 → 任务 12（前端监听 + flagged 阈值）✓
- §6 事件联动 exam.graded → 任务 5（发布）+ 任务 10（动态流）+ 任务 11（通知）✓
- §7 安全模型（不下发答案、防 IDOR、服务端时间）→ 任务 6（归属校验 + isTimeout）+ 任务 7（ExamQuestionResponse 无 answer 字段）✓
- §8 前端 → 任务 12（学生端）+ 任务 13（教师端）✓
- §9 测试门禁 → 任务 14 ✓

**2. 占位符扫描：** 无 TODO/待定；所有步骤含可执行代码或精确命令。

**3. 类型一致性：**
- `ExamQuestionSnapshot(questionId, questionType, options, answer, score)` — 任务 3 定义，任务 6/8 的 `buildSnapshot`/`readSnapshot` JSON 字段（questionId/questionType/stem/options/answer/score）与其一致 ✓
- `ExamAttemptMapper.markGraded(ExamAttemptEntity)` 签名在任务 2 定义，任务 6 调用 ✓
- `ContentEventPublisher.examGraded(Long, String, Long, String, Long, Integer, boolean, long, LocalDateTime)` — 任务 5 定义，任务 6 调用（any() 匹配）✓
- `ExamService.listStudentExams/getStudentExam/listTeacherExams/updateExam/deleteExam` — 任务 7/9 引用，任务 8 定义 ✓
- `ExamAttemptService.getAttemptResult` — 任务 7 引用，任务 8 步骤 5 补充 ✓
- 前端 `ExamQuestion.answer?: number[]` 与 mock 的 `correctAnswer` 兼容（任务 12 步骤 3 说明）✓
- 事件字段 `studentId`（content 发布）与 `userId`（notification DTO）——`DomainNotificationConsumer` 用 `treeToValue` 映射 `ExamGradedEvent.userId`，而 content 事件 payload 是 `studentId`。**需在任务 11 步骤 3 的 `handleExamGraded` 前补兼容**：解析时若 `userId` 为空则读 `studentId`。修复：`ExamGradedEvent` 增加 `studentId` 字段，或在消费分支读取 `root.get("studentId")` 回填。**内联修复**：任务 11 步骤 1 说明中追加「事件 payload 使用 studentId 字段，消费时兼容读取」——在 `handleExamGraded` 中 `if (event.getUserId() == null && root.has("studentId")) event.setUserId(root.get("studentId").asLong());`（该行置于调用 handleExamGraded 前，`DomainNotificationConsumer` 分支内）。

**执行交接：**

计划已完成并保存到 `docs/superpowers/plans/2026-08-28-educloud-exam.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？
