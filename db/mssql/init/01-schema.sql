-- =============================================================================
-- 01-schema.sql : grades DB 생성 + 테이블
--   성적 집계(레거시) — MS-SQL Server 2022
--   컨테이너 첫 기동 시 entrypoint.sh 가 sqlcmd 로 실행한다.
-- =============================================================================
IF DB_ID(N'grades') IS NULL
BEGIN
    CREATE DATABASE grades;
END;
GO

ALTER DATABASE grades SET READ_COMMITTED_SNAPSHOT OFF;
GO

USE grades;
GO

-- -----------------------------------------------------------------------------
-- 학급
-- -----------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.class', N'U') IS NULL
CREATE TABLE dbo.class (
    id          VARCHAR(10)   NOT NULL CONSTRAINT PK_class PRIMARY KEY,
    name        NVARCHAR(50)  NOT NULL
);
GO

-- -----------------------------------------------------------------------------
-- 학생 (id 형식: STU-1001)
-- -----------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.student', N'U') IS NULL
CREATE TABLE dbo.student (
    id          VARCHAR(20)   NOT NULL CONSTRAINT PK_student PRIMARY KEY,
    name        NVARCHAR(50)  NOT NULL,
    class_id    VARCHAR(10)   NOT NULL
        CONSTRAINT FK_student_class REFERENCES dbo.class(id)
);
GO

-- -----------------------------------------------------------------------------
-- 단원. weight = 학기 성적 가중치(합계 1.00). 0.00 인 단원은 보너스 단원(성적 미반영).
-- -----------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.unit', N'U') IS NULL
CREATE TABLE dbo.unit (
    code        VARCHAR(16)   NOT NULL CONSTRAINT PK_unit PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL,
    weight      DECIMAL(3,2)  NOT NULL CONSTRAINT DF_unit_weight DEFAULT (0.00)
);
GO

-- -----------------------------------------------------------------------------
-- 과제. 단원당 1개. due_at 이 지연 제출 판정의 기준.
-- -----------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.assignment', N'U') IS NULL
CREATE TABLE dbo.assignment (
    id          VARCHAR(10)   NOT NULL CONSTRAINT PK_assignment PRIMARY KEY,
    unit_code   VARCHAR(16)   NOT NULL
        CONSTRAINT FK_assignment_unit REFERENCES dbo.unit(code),
    title       NVARCHAR(100) NOT NULL,
    due_at      DATETIME2(0)  NOT NULL
);
GO

-- -----------------------------------------------------------------------------
-- 제출. status: S=제출, X=무효(집계 제외). 같은 학생·과제에 여러 행이 있을 수 있다(재제출).
-- aggregated_at / reported_at 은 프로시저가 마지막으로 처리한 시각(운영 기록용).
-- -----------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.submission', N'U') IS NULL
CREATE TABLE dbo.submission (
    id              INT           NOT NULL CONSTRAINT PK_submission PRIMARY KEY,
    student_id      VARCHAR(20)   NOT NULL
        CONSTRAINT FK_submission_student REFERENCES dbo.student(id),
    unit_code       VARCHAR(16)   NOT NULL
        CONSTRAINT FK_submission_unit REFERENCES dbo.unit(code),
    assignment_id   VARCHAR(10)   NOT NULL
        CONSTRAINT FK_submission_assignment REFERENCES dbo.assignment(id),
    score           DECIMAL(5,1)  NULL,
    submitted_at    DATETIME2(0)  NOT NULL,
    status          CHAR(1)       NOT NULL CONSTRAINT DF_submission_status DEFAULT ('S'),
    aggregated_at   DATETIME2(0)  NULL,
    reported_at     DATETIME2(0)  NULL
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_submission_student_assignment')
CREATE INDEX IX_submission_student_assignment
    ON dbo.submission (student_id, assignment_id, submitted_at);
GO

-- -----------------------------------------------------------------------------
-- 집계 결과. usp_aggregate_grades 가 채우고, usp_class_report 가 보고 시각을 찍는다.
-- -----------------------------------------------------------------------------
IF OBJECT_ID(N'dbo.grade_summary', N'U') IS NULL
CREATE TABLE dbo.grade_summary (
    class_id        VARCHAR(10)   NOT NULL,
    student_id      VARCHAR(20)   NOT NULL,
    unit_code       VARCHAR(16)   NOT NULL,      -- 'TOTAL' = 학생 종합
    raw_score       DECIMAL(5,1)  NULL,          -- 반영 전 원점수(TOTAL 은 NULL)
    score           DECIMAL(5,1)  NOT NULL,      -- 규칙 적용 후 점수(1자리)
    grade           CHAR(1)       NOT NULL,
    is_late         BIT           NOT NULL CONSTRAINT DF_gs_is_late DEFAULT (0),
    is_missing      BIT           NOT NULL CONSTRAINT DF_gs_is_missing DEFAULT (0),
    aggregated_at   DATETIME2(0)  NOT NULL,
    last_report_at  DATETIME2(0)  NULL,
    CONSTRAINT PK_grade_summary PRIMARY KEY (student_id, unit_code)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_grade_summary_class')
CREATE INDEX IX_grade_summary_class ON dbo.grade_summary (class_id);
GO
