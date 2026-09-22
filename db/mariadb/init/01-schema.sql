-- 문항 은행(itembank) 스키마
-- compose 가 /docker-entrypoint-initdb.d 로 마운트한다. 01 → 02 → 03 순서로 실행된다.
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS itembank CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE itembank;

-- ---------------------------------------------------------------
-- 문항 은행
-- ---------------------------------------------------------------
CREATE TABLE unit (
  id    INT          NOT NULL,
  code  VARCHAR(16)  NOT NULL,
  name  VARCHAR(100) NOT NULL,
  grade TINYINT      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_unit_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE item (
  id         INT          NOT NULL,
  unit_id    INT          NOT NULL,
  title      VARCHAR(200) NOT NULL,
  stem       TEXT         NOT NULL,
  level      TINYINT      NOT NULL,                 -- 1~5
  status     CHAR(1)      NOT NULL DEFAULT 'A',     -- A=공개 D=삭제 R=검수중
  created_at DATETIME     NOT NULL,
  updated_at DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_item_unit (unit_id),
  KEY idx_item_level (level),
  CONSTRAINT fk_item_unit FOREIGN KEY (unit_id) REFERENCES unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tag (
  id   INT         NOT NULL,
  name VARCHAR(50) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE item_tag (
  item_id INT NOT NULL,
  tag_id  INT NOT NULL,
  PRIMARY KEY (item_id, tag_id),
  CONSTRAINT fk_item_tag_item FOREIGN KEY (item_id) REFERENCES item (id),
  CONSTRAINT fk_item_tag_tag  FOREIGN KEY (tag_id)  REFERENCES tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 화면 노출 대상 문항. 노출 여부는 삭제 플래그가 아니라 status 코드로 판정한다.
-- (검색 화면 search.php 와 등록 화면이 모두 이 뷰를 기준으로 삼는다)
CREATE OR REPLACE VIEW v_item_public AS
SELECT
  i.id,
  i.unit_id,
  u.code  AS unit_code,
  u.name  AS unit_name,
  u.grade AS unit_grade,
  i.title,
  i.stem,
  i.level,
  i.created_at,
  i.updated_at,
  (SELECT GROUP_CONCAT(t.name ORDER BY t.id SEPARATOR ',')
     FROM item_tag it
     JOIN tag t ON t.id = it.tag_id
    WHERE it.item_id = i.id) AS tag_names
FROM item i
JOIN unit u ON u.id = i.unit_id
WHERE i.status = 'A';

-- ---------------------------------------------------------------
-- 과제 배포 (assignment-thymeleaf 모듈이 같은 DB를 쓴다)
-- ---------------------------------------------------------------
CREATE TABLE class (
  id         INT         NOT NULL,
  name       VARCHAR(50) NOT NULL,
  teacher_id VARCHAR(20) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment (
  id      INT          NOT NULL,
  title   VARCHAR(200) NOT NULL,
  unit_id INT          NOT NULL,
  due_at  DATETIME     NOT NULL,
  status  CHAR(1)      NOT NULL DEFAULT 'O',        -- O=진행 C=마감
  PRIMARY KEY (id),
  CONSTRAINT fk_assignment_unit FOREIGN KEY (unit_id) REFERENCES unit (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE distribution (
  id             INT      NOT NULL,
  assignment_id  INT      NOT NULL,
  class_id       INT      NOT NULL,
  distributed_at DATETIME NOT NULL,
  redistributed  TINYINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_distribution_assignment (assignment_id),
  KEY idx_distribution_class (class_id),
  CONSTRAINT fk_distribution_assignment FOREIGN KEY (assignment_id) REFERENCES assignment (id),
  CONSTRAINT fk_distribution_class      FOREIGN KEY (class_id)      REFERENCES class (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE submission (
  id              INT          NOT NULL,
  distribution_id INT          NOT NULL,
  student_id      VARCHAR(20)  NOT NULL,            -- STU-1001 형식
  submitted_at    DATETIME     NOT NULL,
  score           DECIMAL(5,1) NULL,
  PRIMARY KEY (id),
  KEY idx_submission_distribution (distribution_id),
  KEY idx_submission_student (student_id),
  CONSTRAINT fk_submission_distribution FOREIGN KEY (distribution_id) REFERENCES distribution (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
