package com.example.grade;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /                     : 안내 화면
 * GET /aggregate?class_id=  : usp_aggregate_grades → <table id="grades">
 * GET /report?class_id=     : usp_class_report     → <table id="report">
 */
@RestController
public class GradeController {

    private static final String HTML = "text/html;charset=UTF-8";
    private static final String DEFAULT_CLASS = "C1";

    private final GradeRepository repository;

    public GradeController(GradeRepository repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/", produces = HTML)
    public String index() {
        StringBuilder sb = new StringBuilder();
        sb.append(head("성적 집계"));
        sb.append("<h1>성적 집계 (레거시)</h1>\n");
        sb.append("<p>MS-SQL 저장 프로시저를 호출해 결과를 표로 보여줍니다.</p>\n");
        sb.append("<ul>\n");
        sb.append("  <li><a href=\"/aggregate?class_id=C1\">/aggregate?class_id=C1</a> — usp_aggregate_grades</li>\n");
        sb.append("  <li><a href=\"/report?class_id=C1\">/report?class_id=C1</a> — usp_class_report</li>\n");
        sb.append("</ul>\n");
        sb.append("<p>학급: C1, C2, C3</p>\n");
        sb.append(foot());
        return sb.toString();
    }

    @GetMapping(value = "/aggregate", produces = HTML)
    public ResponseEntity<String> aggregate(@RequestParam(name = "class_id", required = false) String classId) {
        String cid = normalize(classId);
        try {
            List<Map<String, String>> rows = repository.aggregateGrades(cid);
            String body = head("성적 집계 " + cid)
                    + "<h1>성적 집계 — " + esc(cid) + "</h1>\n"
                    + "<p id=\"count\">집계 결과 " + rows.size() + "건</p>\n"
                    + table("grades", List.of("student_id", "name", "unit", "score", "grade"), rows)
                    + foot();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(HTML)).body(body);
        } catch (DataAccessException e) {
            return error(e);
        }
    }

    @GetMapping(value = "/report", produces = HTML)
    public ResponseEntity<String> report(@RequestParam(name = "class_id", required = false) String classId) {
        String cid = normalize(classId);
        try {
            List<Map<String, String>> rows = repository.classReport(cid);
            String body = head("학급 보고 " + cid)
                    + "<h1>학급 단원별 현황 — " + esc(cid) + "</h1>\n"
                    + "<p id=\"count\">단원 " + rows.size() + "건</p>\n"
                    + table("report",
                            List.of("unit", "unit_name", "enrolled", "submitted", "missing", "late", "excluded",
                                    "avg_score", "max_score", "min_score"),
                            rows)
                    + foot();
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(HTML)).body(body);
        } catch (DataAccessException e) {
            return error(e);
        }
    }

    // ---- helpers ----------------------------------------------------------

    private static String normalize(String classId) {
        if (classId == null || classId.isBlank()) {
            return DEFAULT_CLASS;
        }
        return classId.trim();
    }

    private static String table(String id, List<String> columns, List<Map<String, String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table id=\"").append(id).append("\">\n<thead><tr>");
        for (String c : columns) {
            sb.append("<th>").append(esc(c)).append("</th>");
        }
        sb.append("</tr></thead>\n<tbody>\n");
        for (Map<String, String> row : rows) {
            sb.append("<tr>");
            for (String c : columns) {
                sb.append("<td>").append(esc(row.getOrDefault(c, ""))).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n</table>\n");
        return sb.toString();
    }

    private static ResponseEntity<String> error(DataAccessException e) {
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        String body = head("오류")
                + "<h1>DB 오류</h1>\n<p id=\"message\">" + esc(msg) + "</p>\n"
                + foot();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.parseMediaType(HTML)).body(body);
    }

    private static String head(String title) {
        return "<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n<meta charset=\"UTF-8\">\n<title>" + esc(title)
                + "</title>\n<style>body{font-family:sans-serif;margin:24px}table{border-collapse:collapse}"
                + "th,td{border:1px solid #999;padding:4px 8px;text-align:left}th{background:#eee}</style>\n"
                + "</head>\n<body>\n";
    }

    private static String foot() {
        return "<footer><a href=\"/\">처음으로</a></footer>\n</body>\n</html>\n";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }
}
