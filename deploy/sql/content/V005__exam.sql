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
