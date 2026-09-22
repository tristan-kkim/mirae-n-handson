<?php
/**
 * DB 연결 (mysqli)
 * 접속 정보는 환경 변수로 받고, 없으면 compose 기본값을 쓴다.
 */

function db_connect()
{
    static $conn = null;
    if ($conn !== null) {
        return $conn;
    }

    $host = getenv('DB_HOST') ? getenv('DB_HOST') : 'mariadb';
    $port = getenv('DB_PORT') ? (int)getenv('DB_PORT') : 3306;
    $name = getenv('DB_NAME') ? getenv('DB_NAME') : 'itembank';
    $user = getenv('DB_USER') ? getenv('DB_USER') : 'app';
    $pass = getenv('DB_PASS') ? getenv('DB_PASS') : 'app-pass';

    mysqli_report(MYSQLI_REPORT_OFF);
    $conn = @new mysqli($host, $user, $pass, $name, $port);
    if ($conn->connect_errno) {
        $err = $conn->connect_error;
        $conn = null;
        throw new RuntimeException('DB 연결 실패: ' . $err);
    }
    $conn->set_charset('utf8mb4');
    return $conn;
}

/**
 * HTML 출력용 이스케이프
 */
function h($s)
{
    return htmlspecialchars((string)$s, ENT_QUOTES | ENT_HTML5, 'UTF-8');
}
