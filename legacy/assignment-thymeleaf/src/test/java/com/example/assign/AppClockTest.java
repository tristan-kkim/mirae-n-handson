package com.example.assign;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AppClockTest {

    @Test
    void fixedClock() {
        assertEquals(LocalDateTime.of(2026, 9, 15, 0, 0, 0), AppClock.now());
    }

    @Test
    void format() {
        assertEquals("2026-09-01 09:00", AppClock.fmt(LocalDateTime.of(2026, 9, 1, 9, 0)));
        assertEquals("", AppClock.fmt(null));
    }
}
