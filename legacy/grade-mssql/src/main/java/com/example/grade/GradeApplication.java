package com.example.grade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 성적 집계 — 얇은 호출부.
 * 비즈니스 규칙은 전부 MS-SQL 저장 프로시저(usp_aggregate_grades, usp_class_report)에 있다.
 * 이 앱은 프로시저를 호출해 결과 행을 HTML 표로 보여주기만 한다.
 */
@SpringBootApplication
public class GradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GradeApplication.class, args);
    }
}
