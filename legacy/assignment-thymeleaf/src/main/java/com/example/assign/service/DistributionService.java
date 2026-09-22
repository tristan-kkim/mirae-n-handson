package com.example.assign.service;

import com.example.assign.AppClock;
import com.example.assign.dao.AssignmentDao;
import com.example.assign.dao.ClassDao;
import com.example.assign.dao.DistributionDao;
import com.example.assign.model.AssignmentRow;
import com.example.assign.model.ClassRow;
import com.example.assign.model.DistributionRow;
import com.example.assign.model.RedistributeResult;
import com.example.assign.model.SubmissionRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributionService {

    private final DistributionDao distributionDao;
    private final AssignmentDao assignmentDao;
    private final ClassDao classDao;

    public DistributionService(DistributionDao distributionDao, AssignmentDao assignmentDao, ClassDao classDao) {
        this.distributionDao = distributionDao;
        this.assignmentDao = assignmentDao;
        this.classDao = classDao;
    }

    public List<DistributionRow> listAll() {
        return distributionDao.findAllForList();
    }

    public DistributionRow get(long id) {
        return distributionDao.findById(id);
    }

    @Transactional
    public long distribute(long assignmentId, long classId) {
        AssignmentRow a = assignmentDao.findById(assignmentId);
        if (a == null) {
            throw new IllegalArgumentException("존재하지 않는 과제입니다. (id=" + assignmentId + ")");
        }
        ClassRow c = classDao.findById(classId);
        if (c == null) {
            throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");
        }
        if ("D".equals(a.getStatus())) {
            throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");
        }
        if (distributionDao.countByAssignmentAndClass(assignmentId, classId) > 0) {
            throw new IllegalStateException("이미 이 학급에 배포된 과제입니다. 다시 내려면 배포 목록에서 재배포를 사용하세요.");
        }
        long id = distributionDao.nextId();
        int n = distributionDao.insert(id, assignmentId, classId, AppClock.now());
        if (n != 1) {
            throw new IllegalStateException("배포 저장에 실패했습니다.");
        }
        return id;
    }

    // v1.4.2 : 재배포 조건 정리 + 제출 유지 정책 반영 (기존 v1.3 코드 그대로 두고 아래에 덧붙임)
    @Transactional
    public RedistributeResult redistribute(long distributionId, String reason, String operatorId) {
        RedistributeResult result = new RedistributeResult();
        result.setDistributionId(distributionId);
        List<String> notes = result.getNotes();
        List<String> warnings = result.getWarnings();
        LocalDateTime now = AppClock.now();
        StringBuilder audit = new StringBuilder();
        audit.append("[REDIST] id=").append(distributionId);

        // ---------- 0. 입력값 정리 ----------
        String op = operatorId;
        if (op == null) {
            op = "";
        }
        op = op.trim();
        if (op.length() == 0) {
            op = "SYSTEM";
            notes.add("작업자 미지정 → SYSTEM 으로 기록");
        } else if (op.length() > 20) {
            op = op.substring(0, 20);
            warnings.add("작업자 ID가 20자를 넘어 잘렸습니다.");
        }
        if (!op.equals("SYSTEM") && !op.startsWith("teacher-") && !op.startsWith("admin-")) {
            warnings.add("작업자 ID 형식이 표준(teacher-, admin-)과 다릅니다: " + op);
        }
        audit.append(" op=").append(op);

        String rsn = reason;
        if (rsn == null) {
            rsn = "";
        }
        rsn = rsn.trim();
        rsn = rsn.replace("\r", " ").replace("\n", " ").replace("\t", " ");
        while (rsn.contains("  ")) {
            rsn = rsn.replace("  ", " ");
        }
        if (rsn.length() == 0) {
            rsn = "(사유 없음)";
            notes.add("재배포 사유가 비어 있어 기본 문구로 대체");
        } else if (rsn.length() > 200) {
            rsn = rsn.substring(0, 197) + "...";
            warnings.add("재배포 사유가 200자를 넘어 잘렸습니다.");
        }
        if (rsn.indexOf('<') >= 0 || rsn.indexOf('>') >= 0) {
            rsn = rsn.replace("<", "&lt;").replace(">", "&gt;");
            notes.add("사유에 포함된 꺾쇠 문자를 치환");
        }
        int reasonCategory = 0;
        if (rsn.contains("오류") || rsn.contains("장애")) {
            reasonCategory = 1;
        } else if (rsn.contains("연장") || rsn.contains("추가")) {
            reasonCategory = 2;
        } else if (rsn.contains("정정") || rsn.contains("수정")) {
            reasonCategory = 3;
        } else if (rsn.contains("재시험") || rsn.contains("재평가")) {
            reasonCategory = 4;
        } else if (rsn.equals("(사유 없음)")) {
            reasonCategory = 9;
        }
        switch (reasonCategory) {
            case 1:
                notes.add("사유 분류: 시스템 오류");
                break;
            case 2:
                notes.add("사유 분류: 기간 연장");
                break;
            case 3:
                notes.add("사유 분류: 문항 정정");
                break;
            case 4:
                notes.add("사유 분류: 재평가");
                break;
            case 9:
                notes.add("사유 분류: 없음");
                break;
            default:
                notes.add("사유 분류: 기타");
                break;
        }

        // ---------- 1. 배포 조회 ----------
        DistributionRow dist = distributionDao.findById(distributionId);
        if (dist == null) {
            result.setOk(false);
            result.setCode("E101");
            result.setMessage("존재하지 않는 배포입니다. (id=" + distributionId + ")");
            System.out.println(audit.append(" -> E101"));
            return result;
        }
        audit.append(" asg=").append(dist.getAssignmentId()).append(" cls=").append(dist.getClassId());

        // ---------- 2. 과제 상태 ----------
        AssignmentRow asg = assignmentDao.findById(dist.getAssignmentId());
        if (asg == null) {
            result.setOk(false);
            result.setCode("E102");
            result.setMessage("배포에 연결된 과제를 찾을 수 없습니다. (assignment_id=" + dist.getAssignmentId() + ")");
            System.out.println(audit.append(" -> E102"));
            return result;
        }
        String st = asg.getStatus();
        if (st == null) {
            st = "";
        }
        st = st.trim().toUpperCase();
        boolean extended = false;
        boolean closedByTeacher = false;
        boolean underReview = false;
        String statusLabel;
        switch (st) {
            case "O":
                statusLabel = "진행중";
                break;
            case "X":
                statusLabel = "연장";
                extended = true;
                notes.add("과제 상태 '연장' → 마감 판정을 완화합니다.");
                break;
            case "C":
                statusLabel = "종료";
                closedByTeacher = true;
                break;
            case "R":
                statusLabel = "검수중";
                underReview = true;
                break;
            case "D":
                statusLabel = "삭제";
                result.setOk(false);
                result.setCode("E103");
                result.setMessage("삭제된 과제는 재배포할 수 없습니다. [" + asg.getTitle() + "]");
                System.out.println(audit.append(" -> E103"));
                return result;
            case "":
                statusLabel = "미지정";
                warnings.add("과제 상태 코드가 비어 있습니다. 진행중으로 간주합니다.");
                break;
            default:
                statusLabel = "알수없음(" + st + ")";
                warnings.add("알 수 없는 과제 상태 코드: " + st + " (진행중으로 간주)");
                break;
        }
        audit.append(" st=").append(st.length() == 0 ? "-" : st);
        if (underReview) {
            result.setOk(false);
            result.setCode("E104");
            result.setMessage("검수중인 과제는 재배포할 수 없습니다. 검수 완료 후 다시 시도하세요. [" + asg.getTitle() + "]");
            System.out.println(audit.append(" -> E104"));
            return result;
        }
        if (closedByTeacher && !extended) {
            // 종료 처리된 과제라도 마감 후 3일 안이면 아래 마감 판정에 맡긴다
            notes.add("교사 종료 과제 → 마감 후 유예 기간 판정으로 진행");
        }
        String title = asg.getTitle();
        if (title == null) {
            title = "";
        }
        title = title.trim();
        if (title.length() == 0) {
            title = "(제목 없음)";
            warnings.add("과제 제목이 비어 있습니다.");
        } else if (title.length() > 40) {
            title = title.substring(0, 40) + "…";
        }

        // ---------- 3. 학급 ----------
        ClassRow cls = classDao.findById(dist.getClassId());
        String className;
        String teacherId;
        if (cls == null) {
            className = "(삭제된 학급 #" + dist.getClassId() + ")";
            teacherId = "";
            warnings.add("배포 대상 학급이 존재하지 않습니다. 재배포는 진행하지만 알림은 발송되지 않습니다.");
        } else {
            className = cls.getName() == null ? "" : cls.getName().trim();
            if (className.length() == 0) {
                className = "학급 #" + cls.getId();
            }
            teacherId = cls.getTeacherId() == null ? "" : cls.getTeacherId().trim();
            if (teacherId.length() == 0) {
                warnings.add("학급 담당 교사가 지정되지 않았습니다.");
            } else if (!teacherId.startsWith("teacher-")) {
                warnings.add("담당 교사 ID 형식이 표준(teacher-)과 다릅니다: " + teacherId);
            } else if (teacherId.length() != 10) {
                warnings.add("담당 교사 ID 길이가 표준과 다릅니다: " + teacherId);
            }
            if (!op.equals("SYSTEM") && teacherId.length() > 0 && !op.equals(teacherId) && !op.startsWith("admin-")) {
                notes.add("담당 교사(" + teacherId + ")가 아닌 작업자(" + op + ")의 재배포");
            }
        }

        // ---------- 4. 마감 판정 ----------
        LocalDateTime due = asg.getDueAt();
        long hoursAfterDue;
        int dueBucket;
        String dueText;
        if (due == null) {
            hoursAfterDue = Long.MIN_VALUE;
            dueBucket = -1;
            dueText = "마감 없음";
            warnings.add("과제에 마감 시각이 없습니다. 마감 규칙을 적용하지 않습니다.");
        } else {
            Duration diff = Duration.between(due, now);
            hoursAfterDue = diff.toHours();
            long minutesAfterDue = diff.toMinutes();
            if (minutesAfterDue < -72 * 60) {
                dueBucket = 0;
                dueText = "마감 전 (" + (-hoursAfterDue / 24) + "일 이상 남음)";
            } else if (minutesAfterDue < -24 * 60) {
                dueBucket = 1;
                dueText = "마감 임박 (" + (-hoursAfterDue) + "시간 남음)";
            } else if (minutesAfterDue < 0) {
                dueBucket = 2;
                dueText = "마감 당일 (" + (-minutesAfterDue) + "분 남음)";
            } else if (minutesAfterDue == 0) {
                dueBucket = 2;
                dueText = "마감 시각 정각";
            } else if (hoursAfterDue < 72) {
                dueBucket = 3;
                dueText = "마감 후 " + hoursAfterDue + "시간 경과 (유예 기간)";
            } else if (hoursAfterDue < 24 * 7) {
                dueBucket = 4;
                dueText = "마감 후 " + (hoursAfterDue / 24) + "일 경과 (유예 종료)";
            } else if (hoursAfterDue < 24 * 30) {
                dueBucket = 5;
                dueText = "마감 후 " + (hoursAfterDue / 24) + "일 경과";
            } else {
                dueBucket = 6;
                dueText = "마감 후 " + (hoursAfterDue / 24 / 30) + "개월 이상 경과";
            }
        }
        audit.append(" due=").append(due == null ? "-" : AppClock.fmt(due)).append(" bucket=").append(dueBucket);
        notes.add("마감 판정: " + dueText);

        // ---------- 5. 재배포 허용 판정 ----------
        boolean allowed;
        String denyReason = null;
        if (dueBucket == -1) {
            allowed = true;
        } else if (dueBucket <= 2) {
            allowed = true;
        } else if (extended) {
            allowed = true;
            notes.add("상태 '연장' → 마감 경과에도 재배포 허용");
        } else {
            // 재배포는 마감 후 7일 이내에만 허용한다 (교무 지침 §4.2)
            if (hoursAfterDue >= 72) {
                allowed = false;
                denyReason = "마감 후 유예 기간이 지났습니다. (" + dueText + ") 과제 상태를 '연장'으로 바꾼 뒤 다시 시도하세요.";
            } else {
                allowed = true;
            }
        }
        if (allowed && closedByTeacher && dueBucket >= 3) {
            notes.add("종료 과제이지만 유예 기간 내 → 허용");
        }
        if (allowed && dist.getRedistributed() == 1) {
            if (extended) {
                notes.add("이미 재배포된 건이지만 '연장' 상태 → 재배포 다시 허용");
            } else if (reasonCategory == 1) {
                notes.add("이미 재배포된 건이지만 시스템 오류 사유 → 재배포 다시 허용");
            } else {
                allowed = false;
                denyReason = "이미 재배포된 배포입니다. 두 번째 재배포는 '연장' 상태이거나 사유가 시스템 오류일 때만 가능합니다.";
            }
        }
        if (!allowed) {
            result.setOk(false);
            result.setCode(dist.getRedistributed() == 1 ? "E106" : "E105");
            result.setMessage("[" + title + " → " + className + "] 재배포 불가: " + denyReason);
            System.out.println(audit.append(" -> ").append(result.getCode()));
            return result;
        }

        // ---------- 6. 제출 현황 ----------
        List<SubmissionRow> subs = distributionDao.findSubmissions(distributionId);
        int total = subs.size();
        int notSubmitted = 0;
        int submittedNoScore = 0;
        int graded = 0;
        int late = 0;
        int lateGraded = 0;
        int perfect = 0;
        int failed = 0;
        int bandA = 0;
        int bandB = 0;
        int bandC = 0;
        int bandD = 0;
        int bandF = 0;
        int badStudentId = 0;
        int dupStudent = 0;
        int futureSubmit = 0;
        int scoreOutOfRange = 0;
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = null;
        BigDecimal max = null;
        LocalDateTime firstSubmit = null;
        LocalDateTime lastSubmit = null;
        Set<String> seenStudents = new HashSet<>();
        Map<String, Integer> gradeCount = new HashMap<>();
        List<String> studentLines = new ArrayList<>();
        List<String> reopenList = new ArrayList<>();
        for (int i = 0; i < subs.size(); i++) {
            SubmissionRow s = subs.get(i);
            String sid = s.getStudentId() == null ? "" : s.getStudentId().trim();
            if (sid.length() == 0) {
                badStudentId++;
                sid = "(학생ID 없음 #" + s.getId() + ")";
            } else if (!sid.startsWith("STU-")) {
                badStudentId++;
            } else if (sid.length() != 8) {
                badStudentId++;
            } else {
                String num = sid.substring(4);
                boolean digits = true;
                for (int k = 0; k < num.length(); k++) {
                    char ch = num.charAt(k);
                    if (ch < '0' || ch > '9') {
                        digits = false;
                        break;
                    }
                }
                if (!digits) {
                    badStudentId++;
                }
            }
            if (seenStudents.contains(sid)) {
                dupStudent++;
            } else {
                seenStudents.add(sid);
            }
            LocalDateTime at = s.getSubmittedAt();
            BigDecimal sc = s.getScore();
            String state;
            if (at == null && sc == null) {
                notSubmitted++;
                state = "미제출";
                reopenList.add(sid);
            } else if (at == null && sc != null) {
                // 제출 시각 없이 점수만 있는 이상 데이터 : 채점된 것으로 본다
                graded++;
                state = "채점완료(제출시각없음)";
                warnings.add(sid + " : 제출 시각 없이 점수만 존재");
            } else if (at != null && sc == null) {
                submittedNoScore++;
                state = "채점전";
            } else {
                graded++;
                state = "채점완료";
            }
            if (at != null) {
                if (at.isAfter(now)) {
                    futureSubmit++;
                    warnings.add(sid + " : 제출 시각이 현재보다 미래 (" + AppClock.fmt(at) + ")");
                }
                if (due != null && at.isAfter(due)) {
                    late++;
                    state = state + "/지연";
                    if (sc != null) {
                        lateGraded++;
                    }
                }
                if (firstSubmit == null || at.isBefore(firstSubmit)) {
                    firstSubmit = at;
                }
                if (lastSubmit == null || at.isAfter(lastSubmit)) {
                    lastSubmit = at;
                }
            }
            if (sc != null) {
                if (sc.compareTo(BigDecimal.ZERO) < 0 || sc.compareTo(new BigDecimal("100")) > 0) {
                    scoreOutOfRange++;
                    warnings.add(sid + " : 점수 범위 이탈 (" + sc + ")");
                }
                sum = sum.add(sc);
                if (min == null || sc.compareTo(min) < 0) {
                    min = sc;
                }
                if (max == null || sc.compareTo(max) > 0) {
                    max = sc;
                }
                if (sc.compareTo(new BigDecimal("100")) == 0) {
                    perfect++;
                }
                String grade;
                if (sc.compareTo(new BigDecimal("90")) >= 0) {
                    bandA++;
                    grade = "A";
                } else if (sc.compareTo(new BigDecimal("80")) >= 0) {
                    bandB++;
                    grade = "B";
                } else if (sc.compareTo(new BigDecimal("70")) >= 0) {
                    bandC++;
                    grade = "C";
                } else if (sc.compareTo(new BigDecimal("60")) >= 0) {
                    bandD++;
                    grade = "D";
                } else {
                    bandF++;
                    failed++;
                    grade = "F";
                }
                Integer prev = gradeCount.get(grade);
                gradeCount.put(grade, prev == null ? 1 : prev.intValue() + 1);
                state = state + "/" + grade;
            }
            studentLines.add(sid + ":" + state);
        }
        audit.append(" subs=").append(total).append("/").append(notSubmitted).append("/").append(submittedNoScore).append("/").append(graded);

        // ---------- 7. 제출 유지 정책 ----------
        // 재배포는 기존 제출을 지우지 않는다. 미제출자만 '재제출 대상' 으로 계산해 화면에 알려 준다.
        int kept = total - notSubmitted;
        int reopen = notSubmitted;
        if (extended) {
            // 연장 상태면 채점 전 제출자도 다시 낼 수 있다
            reopen = notSubmitted + submittedNoScore;
            notes.add("연장 상태 → 채점 전 제출자 " + submittedNoScore + "명도 재제출 대상에 포함");
        }
        if (reasonCategory == 4) {
            // 재평가 사유면 채점 완료자까지 재제출 대상 (기존 점수는 그대로 유지)
            reopen = total;
            notes.add("재평가 사유 → 전원 재제출 대상 (기존 점수는 유지)");
        }
        if (graded > 0 && reasonCategory != 4) {
            warnings.add("채점 완료 제출 " + graded + "건은 그대로 유지됩니다. 점수 변경이 필요하면 성적 모듈에서 처리하세요.");
        }
        if (late > 0) {
            notes.add("지연 제출 " + late + "건 (그중 채점 " + lateGraded + "건)");
        }
        if (dupStudent > 0) {
            warnings.add("같은 학생의 제출이 " + dupStudent + "건 중복되어 있습니다.");
        }
        if (badStudentId > 0) {
            warnings.add("학생 ID 형식(STU-1001)에 맞지 않는 제출 " + badStudentId + "건");
        }
        if (total == 0) {
            notes.add("제출 데이터 없음 → 학급 전원이 재제출 대상");
        }
        result.setKeptSubmissions(kept);
        result.setReopenTargets(reopen);

        // ---------- 8. 통계 문구 ----------
        StringBuilder stat = new StringBuilder();
        stat.append("제출 ").append(total).append("건");
        if (total > 0) {
            stat.append(" (미제출 ").append(notSubmitted)
                .append(", 채점전 ").append(submittedNoScore)
                .append(", 채점완료 ").append(graded).append(")");
            int submittedAny = total - notSubmitted;
            int rate = total == 0 ? 0 : (int) Math.round(submittedAny * 100.0 / total);
            stat.append(" 제출률 ").append(rate).append("%");
            if (rate == 100) {
                notes.add("전원 제출 완료 상태에서의 재배포");
            } else if (rate >= 80) {
                notes.add("제출률 80% 이상");
            } else if (rate >= 50) {
                notes.add("제출률 50% 이상");
            } else if (rate > 0) {
                warnings.add("제출률 50% 미만 → 마감 안내가 누락되지 않았는지 확인");
            } else {
                warnings.add("제출률 0% → 배포가 학생에게 노출되지 않았을 가능성");
            }
        }
        if (graded > 0) {
            BigDecimal avg = sum.divide(new BigDecimal(graded), 1, RoundingMode.HALF_UP);
            stat.append(" / 평균 ").append(avg.toPlainString());
            if (min != null && max != null) {
                stat.append(" (최저 ").append(min.stripTrailingZeros().toPlainString())
                    .append(" 최고 ").append(max.stripTrailingZeros().toPlainString()).append(")");
            }
            if (perfect > 0) {
                stat.append(" 만점 ").append(perfect).append("명");
            }
            if (failed > 0) {
                stat.append(" 60점 미만 ").append(failed).append("명");
            }
            StringBuilder dist5 = new StringBuilder();
            dist5.append("A").append(bandA).append(" B").append(bandB).append(" C").append(bandC)
                 .append(" D").append(bandD).append(" F").append(bandF);
            notes.add("등급 분포: " + dist5);
            if (avg.compareTo(new BigDecimal("40")) < 0) {
                warnings.add("평균 40점 미만 → 문항 오류 가능성. 사유를 '문항 정정' 으로 남기는 것을 권장");
            } else if (avg.compareTo(new BigDecimal("95")) > 0 && graded >= 5) {
                notes.add("평균 95점 초과");
            }
            if (bandF > graded / 2) {
                warnings.add("채점자 절반 이상이 F");
            }
        }
        if (firstSubmit != null && lastSubmit != null) {
            long spanHours = Duration.between(firstSubmit, lastSubmit).toHours();
            if (spanHours == 0) {
                notes.add("제출 시각이 모두 같은 시간대");
            } else if (spanHours < 24) {
                notes.add("제출 기간 " + spanHours + "시간");
            } else {
                notes.add("제출 기간 " + (spanHours / 24) + "일");
            }
            if (due != null && firstSubmit.isAfter(due)) {
                warnings.add("모든 제출이 마감 이후에 이뤄졌습니다.");
            }
        }
        if (futureSubmit > 0) {
            warnings.add("미래 시각 제출 " + futureSubmit + "건 (시계 설정 확인)");
        }
        if (scoreOutOfRange > 0) {
            warnings.add("점수 범위(0~100) 이탈 " + scoreOutOfRange + "건");
        }
        for (Map.Entry<String, Integer> e : gradeCount.entrySet()) {
            if (e.getValue() != null && e.getValue().intValue() == graded && graded >= 3) {
                notes.add("채점자 전원이 " + e.getKey() + " 등급");
            }
        }

        // ---------- 9. 상태 전환 요약 ----------
        StringBuilder transition = new StringBuilder();
        if (notSubmitted > 0) {
            transition.append("미제출 ").append(notSubmitted).append("명 → 재제출 대상");
        }
        if (extended && submittedNoScore > 0) {
            if (transition.length() > 0) {
                transition.append(", ");
            }
            transition.append("채점전 ").append(submittedNoScore).append("명 → 재제출 가능");
        } else if (submittedNoScore > 0) {
            if (transition.length() > 0) {
                transition.append(", ");
            }
            transition.append("채점전 ").append(submittedNoScore).append("명 → 유지");
        }
        if (graded > 0) {
            if (transition.length() > 0) {
                transition.append(", ");
            }
            if (reasonCategory == 4) {
                transition.append("채점완료 ").append(graded).append("명 → 재제출 가능(점수 유지)");
            } else {
                transition.append("채점완료 ").append(graded).append("명 → 유지");
            }
        }
        if (transition.length() == 0) {
            transition.append("전환 대상 없음");
        }
        notes.add("상태 전환: " + transition);

        // ---------- 10. 플래그 갱신 ----------
        int updated = distributionDao.markRedistributed(distributionId);
        if (updated == 0) {
            result.setOk(false);
            result.setCode("E107");
            result.setMessage("재배포 플래그 갱신에 실패했습니다. 다시 시도하세요.");
            System.out.println(audit.append(" -> E107"));
            return result;
        } else if (updated > 1) {
            // id 는 PK 이므로 있을 수 없지만 방어
            warnings.add("재배포 플래그 갱신 건수가 " + updated + "건입니다.");
        }
        DistributionRow after = distributionDao.findById(distributionId);
        if (after == null) {
            warnings.add("갱신 후 재조회 실패");
        } else if (after.getRedistributed() != 1) {
            warnings.add("갱신 후에도 재배포 플래그가 0 입니다.");
        } else if (after.getSubmittedCount() != (total - notSubmitted)) {
            // 제출 건수 집계는 submitted_at 기준, 여기 계산은 score 포함 → 다를 수 있음
            notes.add("제출 건수 집계 차이 (화면 " + after.getSubmittedCount() + " / 계산 " + (total - notSubmitted) + ")");
        }

        // ---------- 11. 결과 메시지 ----------
        StringBuilder msg = new StringBuilder();
        msg.append("[").append(title).append(" → ").append(className).append("] 재배포 완료.");
        if (dist.getRedistributed() == 1) {
            msg.append(" (재배포 재실행)");
        }
        msg.append(" ").append(stat);
        msg.append(" 재제출 대상 ").append(reopen).append("명");
        if (kept > 0) {
            msg.append(", 기존 제출 ").append(kept).append("건 유지");
        }
        msg.append(". 사유: ").append(rsn);
        if (warnings.size() > 0) {
            msg.append(" (주의 ").append(warnings.size()).append("건)");
        }
        result.setMessage(msg.toString());

        StringBuilder summary = new StringBuilder();
        summary.append("과제=").append(title)
               .append(" | 상태=").append(statusLabel)
               .append(" | 학급=").append(className);
        if (teacherId.length() > 0) {
            summary.append("(").append(teacherId).append(")");
        }
        summary.append(" | 마감=").append(due == null ? "-" : AppClock.fmt(due))
               .append(" | 판정=").append(dueText)
               .append(" | 작업자=").append(op);
        if (studentLines.size() > 0) {
            summary.append(" | 학생=");
            int shown = 0;
            for (int i = 0; i < studentLines.size(); i++) {
                if (shown >= 30) {
                    summary.append(" 외 ").append(studentLines.size() - shown).append("명");
                    break;
                }
                if (shown > 0) {
                    summary.append(", ");
                }
                summary.append(studentLines.get(i));
                shown++;
            }
        }
        result.setSummary(summary.toString());
        result.setOk(true);
        if (warnings.size() == 0) {
            result.setCode("OK");
        } else if (warnings.size() <= 2) {
            result.setCode("OK-W");
        } else {
            result.setCode("OK-W" + warnings.size());
        }
        audit.append(" -> ").append(result.getCode()).append(" reopen=").append(reopen).append(" kept=").append(kept);
        System.out.println(audit);
        if (warnings.size() > 0) {
            for (int i = 0; i < warnings.size(); i++) {
                System.out.println("[REDIST]   warn: " + warnings.get(i));
            }
        }
        return result;
    }
}
