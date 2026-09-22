package com.example.assignment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학급 리포트 API. */
@RestController
@RequestMapping("/api/classes")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** {@code GET /api/classes/{id}/report} — 학급 리포트. */
    @GetMapping("/{id}/report")
    public ClassReport classReport(@PathVariable Integer id) {
        return reportService.buildClassReport(id);
    }
}
