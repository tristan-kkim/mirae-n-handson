-- =============================================================================
-- 04-procs.sql : 저장 프로시저 2개 + readonly EXECUTE 권한
--   본문은 legacy/grade-mssql/sql/usp_aggregate_grades.sql, usp_class_report.sql 과 동일하다.
--   (Docker 빌드 컨텍스트가 db/mssql 이므로 사본을 둔다. 한쪽을 고치면 다른 쪽도 같이 고칠 것.)
-- =============================================================================
-- =============================================================================
-- usp_aggregate_grades : 학급 단위 성적 집계
--
--   입력 : @class_id  (예: 'C1')
--   출력 : student_id, name, unit, score, grade
--          - 단원별 1행 + 학생별 종합('TOTAL') 1행
--          - 정렬: student_id, unit (TOTAL 은 학생의 마지막 행)
--   부수효과 : dbo.grade_summary 갱신(UPSERT), dbo.submission.aggregated_at 갱신
--
--   이 파일은 db/mssql/init/04-procs.sql 과 같은 내용이어야 한다.
--   (컨테이너 없이 소스를 읽는 실습용 사본)
--
--   변경 이력
--     2019-03  최초 작성
--     2021-09  지연 제출 감점 추가 (2일 유예 → 10% 감점)
--     2022-02  단원 가중치를 unit 테이블로 이동   ※ 아래 CASE 문은 아직 정리 안 됨
--     2023-11  보너스 단원(가중치 0) 미제출 시 0점 처리 제외
--     2024-06  종합(TOTAL) 행 추가, grade_summary 저장
-- =============================================================================
USE grades;
GO

CREATE OR ALTER PROCEDURE dbo.usp_aggregate_grades
    @class_id VARCHAR(10)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @now            DATETIME2(0) = SYSDATETIME();
    DECLARE @class_name     NVARCHAR(50);
    DECLARE @student_count  INT = 0;
    DECLARE @unit_count     INT = 0;
    DECLARE @row_count      INT = 0;

    -- -------------------------------------------------------------------------
    -- 0. 입력 확인. 없는 학급이면 빈 결과를 돌려준다(오류 아님 — 화면에서 "0건"으로 표시)
    -- -------------------------------------------------------------------------
    SELECT @class_name = c.name
      FROM dbo.class AS c
     WHERE c.id = @class_id;

    IF @class_name IS NULL
    BEGIN
        SELECT CAST(NULL AS VARCHAR(20))  AS student_id,
               CAST(NULL AS NVARCHAR(50)) AS name,
               CAST(NULL AS VARCHAR(16))  AS unit,
               CAST(NULL AS DECIMAL(5,1)) AS score,
               CAST(NULL AS CHAR(1))      AS grade
         WHERE 1 = 0;
        RETURN;
    END;

    -- -------------------------------------------------------------------------
    -- 1. 작업용 임시 테이블
    -- -------------------------------------------------------------------------
    CREATE TABLE #stu (
        seq         INT IDENTITY(1,1) PRIMARY KEY,
        student_id  VARCHAR(20)  NOT NULL,
        name        NVARCHAR(50) NOT NULL
    );

    CREATE TABLE #unit (
        seq         INT IDENTITY(1,1) PRIMARY KEY,
        unit_code   VARCHAR(16)  NOT NULL,
        unit_name   NVARCHAR(100) NOT NULL,
        weight      DECIMAL(3,2) NOT NULL,     -- unit 테이블 값 (미제출 판정에만 사용)
        assignment_id VARCHAR(10) NULL,
        due_at      DATETIME2(0) NULL
    );

    -- 유효 제출: status 'X' 제외 후, 같은 학생·과제는 가장 늦게 제출한 1건만 인정
    CREATE TABLE #valid (
        student_id     VARCHAR(20)  NOT NULL,
        assignment_id  VARCHAR(10)  NOT NULL,
        unit_code      VARCHAR(16)  NOT NULL,
        score          DECIMAL(5,1) NULL,
        submitted_at   DATETIME2(0) NOT NULL,
        PRIMARY KEY (student_id, assignment_id)
    );

    CREATE TABLE #calc (
        student_id   VARCHAR(20)  NOT NULL,
        unit_code    VARCHAR(16)  NOT NULL,
        raw_score    DECIMAL(5,1) NULL,
        adj_score    DECIMAL(5,1) NOT NULL,
        is_late      BIT          NOT NULL,
        is_missing   BIT          NOT NULL,
        grade        CHAR(1)      NULL,
        PRIMARY KEY (student_id, unit_code)
    );

    -- -------------------------------------------------------------------------
    -- 2. 대상 적재
    -- -------------------------------------------------------------------------
    INSERT INTO #stu (student_id, name)
    SELECT s.id, s.name
      FROM dbo.student AS s
     WHERE s.class_id = @class_id
     ORDER BY s.id;

    SET @student_count = @@ROWCOUNT;

    INSERT INTO #unit (unit_code, unit_name, weight, assignment_id, due_at)
    SELECT u.code, u.name, u.weight, a.id, a.due_at
      FROM dbo.unit AS u
      LEFT JOIN dbo.assignment AS a
        ON a.unit_code = u.code
     ORDER BY u.code, a.id;

    SET @unit_count = @@ROWCOUNT;

    -- 유효 제출 선별. 재제출이 있으면 submitted_at 이 늦은 것, 그것도 같으면 id 가 큰 것.
    -- ※ 제외 조건은 status = 'X' 하나뿐이다. 점수가 NULL 인 제출은 제외하지 않는다(0점 처리됨).
    INSERT INTO #valid (student_id, assignment_id, unit_code, score, submitted_at)
    SELECT v.student_id, v.assignment_id, v.unit_code, v.score, v.submitted_at
      FROM (
            SELECT sub.student_id,
                   sub.assignment_id,
                   sub.unit_code,
                   sub.score,
                   sub.submitted_at,
                   ROW_NUMBER() OVER (
                       PARTITION BY sub.student_id, sub.assignment_id
                       ORDER BY sub.submitted_at DESC, sub.id DESC
                   ) AS rn
              FROM dbo.submission AS sub
              JOIN #stu AS st
                ON st.student_id = sub.student_id
             WHERE sub.status <> 'X'
           ) AS v
     WHERE v.rn = 1;

    -- -------------------------------------------------------------------------
    -- 3. 학생 × 단원 계산 (커서)
    -- -------------------------------------------------------------------------
    DECLARE @student_id     VARCHAR(20);
    DECLARE @student_name   NVARCHAR(50);
    DECLARE @unit_code      VARCHAR(16);
    DECLARE @unit_weight    DECIMAL(3,2);
    DECLARE @assignment_id  VARCHAR(10);
    DECLARE @due_at         DATETIME2(0);
    DECLARE @raw            DECIMAL(5,1);
    DECLARE @adj            DECIMAL(5,1);
    DECLARE @submitted_at   DATETIME2(0);
    DECLARE @is_late        BIT;
    DECLARE @is_missing     BIT;
    DECLARE @found          INT;

    DECLARE cur_stu CURSOR LOCAL FAST_FORWARD FOR
        SELECT student_id, name
          FROM #stu
         ORDER BY seq;

    OPEN cur_stu;
    FETCH NEXT FROM cur_stu INTO @student_id, @student_name;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        DECLARE cur_unit CURSOR LOCAL FAST_FORWARD FOR
            SELECT unit_code, weight, assignment_id, due_at
              FROM #unit
             ORDER BY seq;

        OPEN cur_unit;
        FETCH NEXT FROM cur_unit INTO @unit_code, @unit_weight, @assignment_id, @due_at;

        WHILE @@FETCH_STATUS = 0
        BEGIN
            SET @raw          = NULL;
            SET @adj          = 0.0;
            SET @submitted_at = NULL;
            SET @is_late      = 0;
            SET @is_missing   = 0;
            SET @found        = 0;

            SELECT @found        = 1,
                   @raw          = v.score,
                   @submitted_at = v.submitted_at
              FROM #valid AS v
             WHERE v.student_id    = @student_id
               AND v.assignment_id = @assignment_id;

            IF @found = 0
            BEGIN
                -- 미제출
                SET @is_missing = 1;

                IF @unit_weight = 0.00
                BEGIN
                    -- 보너스 단원은 미제출이면 행 자체를 만들지 않는다 (2023-11)
                    FETCH NEXT FROM cur_unit INTO @unit_code, @unit_weight, @assignment_id, @due_at;
                    CONTINUE;
                END;

                -- 가중치가 있는 단원은 0점으로 집계
                SET @adj = 0.0;
            END
            ELSE
            BEGIN
                -- 점수 NULL 은 0점으로 본다
                IF @raw IS NULL
                    SET @raw = 0.0;

                SET @adj = @raw;

                -- 지연 제출: 마감 후 2일까지는 봐주고, 그 뒤는 10% 감점 (2021-09)
                IF @due_at IS NOT NULL
                   AND @submitted_at > DATEADD(DAY, 2, @due_at)
                BEGIN
                    SET @is_late = 1;
                    -- 반올림: 소수 첫째 자리, 5 는 올림 (ROUND 는 0.5 를 올린다)
                    SET @adj = ROUND(@raw * 0.9, 1);
                END;
            END;

            INSERT INTO #calc (student_id, unit_code, raw_score, adj_score, is_late, is_missing)
            VALUES (@student_id, @unit_code, @raw, @adj, @is_late, @is_missing);

            FETCH NEXT FROM cur_unit INTO @unit_code, @unit_weight, @assignment_id, @due_at;
        END;

        CLOSE cur_unit;
        DEALLOCATE cur_unit;

        FETCH NEXT FROM cur_stu INTO @student_id, @student_name;
    END;

    CLOSE cur_stu;
    DEALLOCATE cur_stu;

    -- -------------------------------------------------------------------------
    -- 4. 단원별 등급
    --    90 이상 A / 80 이상 B / 70 이상 C / 60 이상 D / 그 외 F
    -- -------------------------------------------------------------------------
    UPDATE #calc
       SET grade = CASE
                       WHEN adj_score >= 90.0 THEN 'A'
                       WHEN adj_score >= 80.0 THEN 'B'
                       WHEN adj_score >= 70.0 THEN 'C'
                       WHEN adj_score >= 60.0 THEN 'D'
                       ELSE 'F'
                   END;

    -- -------------------------------------------------------------------------
    -- 5. 학생별 종합(TOTAL) = Σ(단원 점수 × 단원 가중치), 소수 첫째 자리 반올림
    --    가중치: M5-1 0.30 / M5-2 0.25 / M5-3 0.25 / M6-1 0.20 / M6-2 0.00 (보너스)
    -- -------------------------------------------------------------------------
    DECLARE @total       DECIMAL(9,4);
    DECLARE @unit_score  DECIMAL(5,1);
    DECLARE @w           DECIMAL(3,2);
    DECLARE @has_late    BIT;
    DECLARE @has_missing BIT;

    DECLARE cur_total CURSOR LOCAL FAST_FORWARD FOR
        SELECT student_id
          FROM #stu
         ORDER BY seq;

    OPEN cur_total;
    FETCH NEXT FROM cur_total INTO @student_id;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @total       = 0.0;
        SET @has_late    = 0;
        SET @has_missing = 0;

        DECLARE cur_calc CURSOR LOCAL FAST_FORWARD FOR
            SELECT unit_code, adj_score, is_late, is_missing
              FROM #calc
             WHERE student_id = @student_id
             ORDER BY unit_code;

        OPEN cur_calc;
        FETCH NEXT FROM cur_calc INTO @unit_code, @unit_score, @is_late, @is_missing;

        WHILE @@FETCH_STATUS = 0
        BEGIN
            -- TODO: unit.weight 를 쓰도록 바꾸기 (2022-02 이후 값이 두 곳에 있음)
            SET @w = CASE @unit_code
                         WHEN 'M5-1' THEN 0.30
                         WHEN 'M5-2' THEN 0.25
                         WHEN 'M5-3' THEN 0.25
                         WHEN 'M6-1' THEN 0.20
                         WHEN 'M6-2' THEN 0.00
                         ELSE 0.00
                     END;

            SET @total = @total + (@unit_score * @w);

            IF @is_late = 1
                SET @has_late = 1;
            IF @is_missing = 1
                SET @has_missing = 1;

            FETCH NEXT FROM cur_calc INTO @unit_code, @unit_score, @is_late, @is_missing;
        END;

        CLOSE cur_calc;
        DEALLOCATE cur_calc;

        INSERT INTO #calc (student_id, unit_code, raw_score, adj_score, is_late, is_missing, grade)
        VALUES (
            @student_id,
            'TOTAL',
            NULL,
            ROUND(@total, 1),
            @has_late,
            @has_missing,
            CASE
                WHEN ROUND(@total, 1) >= 90.0 THEN 'A'
                WHEN ROUND(@total, 1) >= 80.0 THEN 'B'
                WHEN ROUND(@total, 1) >= 70.0 THEN 'C'
                WHEN ROUND(@total, 1) >= 60.0 THEN 'D'
                ELSE 'F'
            END
        );

        FETCH NEXT FROM cur_total INTO @student_id;
    END;

    CLOSE cur_total;
    DEALLOCATE cur_total;

    -- -------------------------------------------------------------------------
    -- 6. 저장. 잠금 순서: grade_summary → submission
    --    (usp_class_report 는 submission → grade_summary 순서로 갱신한다)
    -- -------------------------------------------------------------------------
    BEGIN TRANSACTION;

    -- 6-1. grade_summary : 기존 행 갱신
    UPDATE gs
       SET gs.class_id      = @class_id,
           gs.raw_score     = c.raw_score,
           gs.score         = c.adj_score,
           gs.grade         = c.grade,
           gs.is_late       = c.is_late,
           gs.is_missing    = c.is_missing,
           gs.aggregated_at = @now
      FROM dbo.grade_summary AS gs
      JOIN #calc AS c
        ON c.student_id = gs.student_id
       AND c.unit_code  = gs.unit_code;

    -- 6-2. grade_summary : 새 행 추가
    INSERT INTO dbo.grade_summary
        (class_id, student_id, unit_code, raw_score, score, grade, is_late, is_missing, aggregated_at)
    SELECT @class_id, c.student_id, c.unit_code, c.raw_score, c.adj_score, c.grade, c.is_late, c.is_missing, @now
      FROM #calc AS c
     WHERE NOT EXISTS (
               SELECT 1
                 FROM dbo.grade_summary AS gs
                WHERE gs.student_id = c.student_id
                  AND gs.unit_code  = c.unit_code
           );

    -- 6-3. grade_summary : 이번 집계에 없는 학급 행 제거 (전출 학생, 보너스 단원 미제출 등)
    DELETE gs
      FROM dbo.grade_summary AS gs
     WHERE gs.class_id = @class_id
       AND NOT EXISTS (
               SELECT 1
                 FROM #calc AS c
                WHERE c.student_id = gs.student_id
                  AND c.unit_code  = gs.unit_code
           );

    -- 6-4. 검증: 저장된 행 수와 계산한 행 수가 같은지 (커서로 한 건씩 대조 — 2019년 방식 그대로)
    DECLARE @saved DECIMAL(5,1);
    DECLARE @mismatch INT = 0;

    DECLARE cur_verify CURSOR LOCAL FAST_FORWARD FOR
        SELECT student_id, unit_code, adj_score
          FROM #calc
         ORDER BY student_id, unit_code;

    OPEN cur_verify;
    FETCH NEXT FROM cur_verify INTO @student_id, @unit_code, @adj;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @saved = NULL;

        SELECT @saved = gs.score
          FROM dbo.grade_summary AS gs
         WHERE gs.student_id = @student_id
           AND gs.unit_code  = @unit_code;

        IF @saved IS NULL OR @saved <> @adj
            SET @mismatch = @mismatch + 1;

        SET @row_count = @row_count + 1;

        FETCH NEXT FROM cur_verify INTO @student_id, @unit_code, @adj;
    END;

    CLOSE cur_verify;
    DEALLOCATE cur_verify;

    IF @mismatch > 0
    BEGIN
        ROLLBACK TRANSACTION;
        RAISERROR(N'usp_aggregate_grades: grade_summary 저장 불일치 %d건 (class %s)', 16, 1, @mismatch, @class_id);
        RETURN;
    END;

    -- 6-5. submission : 집계 처리 시각 기록 (학급 학생의 모든 제출 행)
    UPDATE sub
       SET sub.aggregated_at = @now
      FROM dbo.submission AS sub
      JOIN #stu AS st
        ON st.student_id = sub.student_id;

    COMMIT TRANSACTION;

    -- -------------------------------------------------------------------------
    -- 7. 결과
    -- -------------------------------------------------------------------------
    SELECT c.student_id                       AS student_id,
           st.name                            AS name,
           c.unit_code                        AS unit,
           c.adj_score                        AS score,
           c.grade                            AS grade
      FROM #calc AS c
      JOIN #stu  AS st
        ON st.student_id = c.student_id
     ORDER BY c.student_id,
              CASE WHEN c.unit_code = 'TOTAL' THEN 1 ELSE 0 END,
              c.unit_code;

    DROP TABLE #calc;
    DROP TABLE #valid;
    DROP TABLE #unit;
    DROP TABLE #stu;
END;
GO

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

-- =============================================================================
-- readonly 는 두 프로시저의 EXECUTE 만 추가로 가진다. 테이블 쓰기는 소유권 연결(dbo)로만 가능.
-- =============================================================================
USE grades;
GO
GRANT EXECUTE ON OBJECT::dbo.usp_aggregate_grades TO readonly;
GRANT EXECUTE ON OBJECT::dbo.usp_class_report     TO readonly;
GO
