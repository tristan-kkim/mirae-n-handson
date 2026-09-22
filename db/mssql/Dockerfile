# MS-SQL Server 2022 + grades DB 자동 초기화 (compose 프로필 mssql, platform linux/amd64)
FROM mcr.microsoft.com/mssql/server:2022-latest

ENV ACCEPT_EULA=Y \
    MSSQL_PID=Developer \
    MSSQL_MEMORY_LIMIT_MB=2048

USER root
COPY init/ /docker-init/
COPY entrypoint.sh /docker-init/entrypoint.sh
RUN sed -i 's/\r$//' /docker-init/entrypoint.sh \
 && chmod 0755 /docker-init/entrypoint.sh \
 && chown -R mssql:root /docker-init
USER mssql

EXPOSE 1433

# 초기화까지 끝났을 때만 healthy: readonly 로 grades 에 붙어 프로시저가 있는지 본다.
HEALTHCHECK --interval=10s --timeout=5s --start-period=180s --retries=30 \
  CMD /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U readonly -P 'Readonly-pass1' -d grades -b \
      -Q "IF OBJECT_ID('dbo.usp_class_report','P') IS NULL RAISERROR('not ready',16,1)" || exit 1

ENTRYPOINT ["/bin/bash", "/docker-init/entrypoint.sh"]
