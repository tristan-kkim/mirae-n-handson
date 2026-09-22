package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 문항 은행 API 진입점.
 *
 * <p>패키지 구성:
 * <ul>
 *   <li>{@code com.example.item} — 단원 · 문항 · 태그 (문항 은행 도메인)</li>
 *   <li>{@code com.example.assignment} — 학급 · 과제 · 배포 · 제출 (과제 배포 도메인, 최소 골격)</li>
 *   <li>{@code com.example.common} — 공통 예외 처리 · 루트 엔드포인트 · 시각 주입</li>
 *   <li>{@code com.example.config} — 웹 MVC 설정(CORS)</li>
 * </ul>
 */
@SpringBootApplication
public class ItemBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItemBankApplication.class, args);
    }
}
