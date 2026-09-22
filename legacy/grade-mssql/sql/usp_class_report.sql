-- =============================================================================
-- usp_class_report : 학급 단원별 현황 보고
--
--   입력 : @class_id  (예: 'C1')
--   출력 : unit, unit_name, enrolled, submitted, missing, late, excluded,
--          avg_score, max_score, min_score
--          - 단원별 1행, unit 순 정렬
--   부수효과 : dbo.submission.reported_at, dbo.grade_summary.last_report_at 갱신
--
--   이 파일은 db/mssql/init/04-procs.sql 과 같은 내용이어야 한다.
--   (컨테이너 없이 소스를 읽는 실습용 사본)
--
--   ※ 집계 규칙은 usp_aggregate_grades 와 같아야 하지만 별도로 구현되어 있다.
--     - 제외: status 'X'
--     - 재제출: 가장 늦은 제출 1건
--     - 지연: 마감 + 2일 초과 → 점수 × 0.9
--     - 미제출: 가중치 있는 단원만 0점으로 평균에 포함
--     - 평균은 소수 둘째 자리 (집계 프로시저는 첫째 자리)
-- =============================================================================
USE grades;
GO

CREATE OR ALTER PROCEDURE dbo.usp_class_report
    @class_id VARCHAR(10)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @now       DATETIME2(0) = SYSDATETIME();
    DECLARE @enrolled  INT;

    SELECT @enrolled = COUNT(*)
      FROM dbo.student AS s
     WHERE s.class_id = @class_id;

    IF @enrolled IS NULL OR @enrolled = 0
    BEGIN
        SELECT CAST(NULL AS VARCHAR(16))   AS unit,
               CAST(NULL AS NVARCHAR(100)) AS unit_name,
               CAST(NULL AS INT)           AS enrolled,
               CAST(NULL AS INT)           AS submitted,
               CAST(NULL AS INT)           AS missing,
               CAST(NULL AS INT)           AS late,
               CAST(NULL AS INT)           AS excluded,
               CAST(NULL AS DECIMAL(5,2))  AS avg_score,
               CAST(NULL AS DECIMAL(5,1))  AS max_score,
               CAST(NULL AS DECIMAL(5,1))  AS min_score
         WHERE 1 = 0;
        RETURN;
    END;

    -- -------------------------------------------------------------------------
    -- 1. 유효 제출 (status 'X' 제외, 학생·과제별 최신 1건) + 지연 감점 적용 점수
    -- -------------------------------------------------------------------------
    CREATE TABLE #eff (
        student_id     VARCHAR(20)  NOT NULL,
        unit_code      VARCHAR(16)  NOT NULL,
        assignment_id  VARCHAR(10)  NOT NULL,
        eff_score      DECIMAL(5,1) NOT NULL,
        is_late        BIT          NOT NULL,
        PRIMARY KEY (student_id, assignment_id)
    );

    INSERT INTO #eff (student_id, unit_code, assignment_id, eff_score, is_late)
    SELECT v.student_id,
           v.unit_code,
           v.assignment_id,
           CASE
               WHEN v.submitted_at > DATEADD(DAY, 2, a.due_at)
                   THEN ROUND(ISNULL(v.score, 0.0) * 0.9, 1)     -- 지연 10% 감점
               ELSE ISNULL(v.score, 0.0)
           END,
           CASE WHEN v.submitted_at > DATEADD(DAY, 2, a.due_at) THEN 1 ELSE 0 END
      FROM (
            SELECT sub.student_id,
                   sub.unit_code,
                   sub.assignment_id,
                   sub.score,
                   sub.submitted_at,
                   ROW_NUMBER() OVER (
                       PARTITION BY sub.student_id, sub.assignment_id
                       ORDER BY sub.submitted_at DESC, sub.id DESC
                   ) AS rn
              FROM dbo.submission AS sub
              JOIN dbo.student AS s
                ON s.id = sub.student_id
             WHERE s.class_id = @class_id
               AND sub.status <> 'X'
           ) AS v
      JOIN dbo.assignment AS a
        ON a.id = v.assignment_id
     WHERE v.rn = 1;

    -- -------------------------------------------------------------------------
    -- 2. 단원별 집계
    --    가중치 0 단원(보너스)은 제출한 학생만 평균에 넣고, 나머지는 미제출 0점을 포함한다.
    -- -------------------------------------------------------------------------
    CREATE TABLE #rep (
        unit_code   VARCHAR(16)   NOT NULL PRIMARY KEY,
        unit_name   NVARCHAR(100) NOT NULL,
        weight      DECIMAL(3,2)  NOT NULL,
        submitted   INT           NOT NULL,
        missing     INT           NOT NULL,
        late        INT           NOT NULL,
        excluded    INT           NOT NULL,
        avg_score   DECIMAL(5,2)  NULL,
        max_score   DECIMAL(5,1)  NULL,
        min_score   DECIMAL(5,1)  NULL
    );

    INSERT INTO #rep (unit_code, unit_name, weight, submitted, missing, late, excluded, avg_score, max_score, min_score)
    SELECT u.code,
           u.name,
           u.weight,
           ISNULL(e.submitted, 0),
           @enrolled - ISNULL(e.submitted, 0),
           ISNULL(e.late, 0),
           ISNULL(x.excluded, 0),
           CASE
               -- 보너스 단원: 제출자 평균. 아무도 안 냈으면 NULL
               WHEN u.weight = 0.00 THEN
                   CASE WHEN ISNULL(e.submitted, 0) = 0 THEN NULL
                        ELSE ROUND(e.sum_score / e.submitted, 2) END
               -- 일반 단원: 미제출 0점 포함, 재적 인원으로 나눈다
               ELSE ROUND(ISNULL(e.sum_score, 0.0) / @enrolled, 2)
           END,
           e.max_score,
           CASE
               WHEN u.weight = 0.00 THEN e.min_score
               WHEN ISNULL(e.submitted, 0) < @enrolled THEN 0.0     -- 미제출이 있으면 최저는 0
               ELSE e.min_score
           END
      FROM dbo.unit AS u
      LEFT JOIN (
            SELECT unit_code,
                   COUNT(*)                 AS submitted,
                   SUM(CAST(is_late AS INT)) AS late,
                   SUM(eff_score)           AS sum_score,
                   MAX(eff_score)           AS max_score,
                   MIN(eff_score)           AS min_score
              FROM #eff
             GROUP BY unit_code
           ) AS e
        ON e.unit_code = u.code
      LEFT JOIN (
            SELECT sub.unit_code, COUNT(*) AS excluded
              FROM dbo.submission AS sub
              JOIN dbo.student AS s
                ON s.id = sub.student_id
             WHERE s.class_id = @class_id
               AND sub.status = 'X'
             GROUP BY sub.unit_code
           ) AS x
        ON x.unit_code = u.code;

    -- -------------------------------------------------------------------------
    -- 3. 보고 시각 기록. 잠금 순서: submission → grade_summary
    --    (usp_aggregate_grades 는 grade_summary → submission 순서로 갱신한다)
    -- -------------------------------------------------------------------------
    BEGIN TRANSACTION;

    -- 3-1. submission : 학급 학생의 모든 제출 행에 보고 시각
    UPDATE sub
       SET sub.reported_at = @now
      FROM dbo.submission AS sub
      JOIN dbo.student AS s
        ON s.id = sub.student_id
     WHERE s.class_id = @class_id;

    -- 3-2. grade_summary : 학급 집계 행에 보고 시각
    UPDATE gs
       SET gs.last_report_at = @now
      FROM dbo.grade_summary AS gs
     WHERE gs.class_id = @class_id;

    COMMIT TRANSACTION;

    -- -------------------------------------------------------------------------
    -- 4. 결과
    -- -------------------------------------------------------------------------
    SELECT r.unit_code  AS unit,
           r.unit_name  AS unit_name,
           @enrolled    AS enrolled,
           r.submitted  AS submitted,
           r.missing    AS missing,
           r.late       AS late,
           r.excluded   AS excluded,
           r.avg_score  AS avg_score,
           r.max_score  AS max_score,
           r.min_score  AS min_score
      FROM #rep AS r
     ORDER BY r.unit_code;

    DROP TABLE #rep;
    DROP TABLE #eff;
END;
GO
