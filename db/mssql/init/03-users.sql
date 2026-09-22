-- =============================================================================
-- 03-users.sql : 로그인 · DB 사용자
--   app      : 얇은 Java 호출부(legacy/grade-mssql)가 쓰는 계정. compose 내부용.
--   readonly : 교안에 노출되는 읽기 전용 계정. db_datareader +
--              두 프로시저 EXECUTE 만(EXECUTE GRANT 는 프로시저 생성 뒤 04-procs.sql 끝에서).
-- =============================================================================
USE master;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'app')
    CREATE LOGIN app WITH PASSWORD = N'App-pass1234!', CHECK_POLICY = OFF, DEFAULT_DATABASE = grades;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'readonly')
    CREATE LOGIN readonly WITH PASSWORD = N'Readonly-pass1', CHECK_POLICY = OFF, DEFAULT_DATABASE = grades;
GO

USE grades;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'app')
    CREATE USER app FOR LOGIN app WITH DEFAULT_SCHEMA = dbo;
GO
ALTER ROLE db_owner ADD MEMBER app;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'readonly')
    CREATE USER readonly FOR LOGIN readonly WITH DEFAULT_SCHEMA = dbo;
GO
ALTER ROLE db_datareader ADD MEMBER readonly;
GO
