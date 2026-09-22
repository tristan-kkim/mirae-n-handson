<?php
/**
 * SimpleLog — 외부 라이브러리(더미). 수정하지 말 것.
 * error_log 로만 기록한다. 화면 출력 없음.
 */

namespace SimpleLog;

class Log
{
    const LEVEL_DEBUG = 10;
    const LEVEL_INFO  = 20;
    const LEVEL_WARN  = 30;
    const LEVEL_ERROR = 40;

    private static $threshold = self::LEVEL_INFO;

    public static function setThreshold($level)
    {
        self::$threshold = (int)$level;
    }

    public static function debug($msg, array $ctx = array())
    {
        self::write(self::LEVEL_DEBUG, 'DEBUG', $msg, $ctx);
    }

    public static function info($msg, array $ctx = array())
    {
        self::write(self::LEVEL_INFO, 'INFO', $msg, $ctx);
    }

    public static function warn($msg, array $ctx = array())
    {
        self::write(self::LEVEL_WARN, 'WARN', $msg, $ctx);
    }

    public static function error($msg, array $ctx = array())
    {
        self::write(self::LEVEL_ERROR, 'ERROR', $msg, $ctx);
    }

    private static function write($level, $label, $msg, array $ctx)
    {
        if ($level < self::$threshold) {
            return;
        }
        $line = '[simplelog][' . $label . '] ' . $msg;
        if (!empty($ctx)) {
            $line .= ' ' . json_encode($ctx, JSON_UNESCAPED_UNICODE);
        }
        error_log($line);
    }
}
