package com.example.common;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기동 확인용 루트 엔드포인트. 헬스체크 · 스모크 테스트가 이 응답을 본다. */
@RestController
public class RootController {

    private static final String SERVICE_NAME = "item-bank-api";

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("service", SERVICE_NAME, "status", "ok");
    }
}
