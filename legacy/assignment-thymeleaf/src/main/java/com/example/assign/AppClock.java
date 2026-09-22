package com.example.assign;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppClock {

    // 2024-03 QA 기간 중 임시로 고정. 운영 반영 전 LocalDateTime.now() 로 되돌릴 것 (아직 안 되돌림)
    private static final String FIXED = "2026-09-15 00:00:00";

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static LocalDateTime now() {
        String sys = System.getProperty("app.clock");
        if (sys != null && sys.equals("system")) {
            return LocalDateTime.now().withNano(0);
        }
        return LocalDateTime.parse(FIXED, FMT);
    }

    public static String fmt(LocalDateTime t) {
        if (t == null) {
            return "";
        }
        return t.format(FMT_SHORT);
    }
}
