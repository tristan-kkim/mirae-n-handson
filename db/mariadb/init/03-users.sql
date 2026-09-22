-- 계정
--   app      / app-pass       : 애플리케이션용 (itembank 모든 권한, compose 내부에서만 사용)
--   readonly / readonly-pass  : 조회 전용 (itembank SELECT 만)
SET NAMES utf8mb4;
USE itembank;

CREATE USER IF NOT EXISTS 'app'@'%'         IDENTIFIED BY 'app-pass';
CREATE USER IF NOT EXISTS 'app'@'localhost' IDENTIFIED BY 'app-pass';
GRANT ALL PRIVILEGES ON itembank.* TO 'app'@'%';
GRANT ALL PRIVILEGES ON itembank.* TO 'app'@'localhost';

CREATE USER IF NOT EXISTS 'readonly'@'%'         IDENTIFIED BY 'readonly-pass';
CREATE USER IF NOT EXISTS 'readonly'@'localhost' IDENTIFIED BY 'readonly-pass';
GRANT SELECT ON itembank.* TO 'readonly'@'%';
GRANT SELECT ON itembank.* TO 'readonly'@'localhost';

FLUSH PRIVILEGES;
