#!/bin/bash
# =============================================================================
# entrypoint.sh — SQL Server 를 띄우고, 준비되면 init/*.sql 을 한 번만 실행한다.
#   - 첫 기동: 01-schema → 02-seed → 03-users → 04-procs
#   - 재기동  : grades DB 에 usp_class_report 가 있으면 건너뜀(마지막 산출물로 판정)
#   - 실패    : 어느 스크립트든 오류면 로그에 남기고 컨테이너를 종료한다(compose 가 재시작)
#   init/*.sql 은 UTF-8 BOM 으로 시작한다(Linux sqlcmd 는 -f 코드페이지 옵션이 없어 BOM 으로 UTF-8 을 판별).
# =============================================================================
set -u

SQLCMD=/opt/mssql-tools18/bin/sqlcmd
INIT_DIR=/docker-init
SA_PW="${MSSQL_SA_PASSWORD:-${SA_PASSWORD:-}}"
WAIT_MAX="${INIT_WAIT_SECONDS:-300}"
# 아래 둘은 테스트용 훅. 평소에는 건드리지 않는다.
SQL_HOST="${INIT_SQL_HOST:-localhost}"
SQLSERVR="${INIT_SQLSERVR:-/opt/mssql/bin/sqlservr}"

log() { printf '%s [init] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }

if [ -z "$SA_PW" ]; then
    log "MSSQL_SA_PASSWORD is not set"
    exit 1
fi

$SQLSERVR &
SQL_PID=$!

trap 'log "stopping sqlservr"; kill -TERM "$SQL_PID" 2>/dev/null; wait "$SQL_PID"' TERM INT

# ---- SQL Server 가 로그인을 받을 때까지 대기 ---------------------------------
waited=0
until "$SQLCMD" -C -S "$SQL_HOST" -U sa -P "$SA_PW" -Q "SELECT 1" -b -o /dev/null >/dev/null 2>&1; do
    if ! kill -0 "$SQL_PID" 2>/dev/null; then
        log "sqlservr exited before accepting connections"
        exit 1
    fi
    if [ "$waited" -ge "$WAIT_MAX" ]; then
        log "sqlservr not ready after ${WAIT_MAX}s"
        kill -TERM "$SQL_PID" 2>/dev/null
        exit 1
    fi
    sleep 5
    waited=$((waited + 5))
done
log "sqlservr ready after ${waited}s"

# ---- 멱등 가드: 마지막 산출물(usp_class_report)이 있으면 초기화 완료로 본다 ----
already=$("$SQLCMD" -C -S "$SQL_HOST" -U sa -P "$SA_PW" -h -1 -W -Q \
    "SET NOCOUNT ON; IF DB_ID('grades') IS NOT NULL AND OBJECT_ID('grades.dbo.usp_class_report','P') IS NOT NULL PRINT 'yes' ELSE PRINT 'no'" 2>/dev/null | tr -d '[:space:]')

if [ "$already" = "yes" ]; then
    log "grades already initialized - skipping init scripts"
else
    for f in "$INIT_DIR"/01-schema.sql "$INIT_DIR"/02-seed.sql "$INIT_DIR"/03-users.sql "$INIT_DIR"/04-procs.sql; do
        log "running $(basename "$f")"
        if ! "$SQLCMD" -C -S "$SQL_HOST" -U sa -P "$SA_PW" -b -i "$f"; then
            log "FAILED: $(basename "$f")"
            kill -TERM "$SQL_PID" 2>/dev/null
            wait "$SQL_PID"
            exit 1
        fi
    done
    log "init complete"
fi

wait "$SQL_PID"
