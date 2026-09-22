package com.example.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.common.NotFoundException;
import com.example.item.Unit;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ReportServiceTest {

    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Mock
    private ClassRoomRepository classRoomRepository;

    @Mock
    private DistributionRepository distributionRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("buildClassReport: 제출 수 · 평균(소수 1자리, 미채점 제외) · 서명을 계산한다")
    void buildsAggregatedReport() {
        ClassRoom classRoom = new ClassRoom(1, "5학년 1반", "TCH-01");
        Unit unit = new Unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);
        Assignment assignment = new Assignment(10, "분수 덧셈 연습", unit, SEED_TIME.plusDays(14), "O");
        Distribution d1 = distribution(1, assignment, classRoom);
        Distribution d2 = distribution(2, assignment, classRoom);

        when(classRoomRepository.findById(1)).thenReturn(Optional.of(classRoom));
        when(distributionRepository.findByClassRoomIdOrderByDistributedAtAsc(1)).thenReturn(List.of(d1, d2));
        when(submissionRepository.findByDistributionIdOrderByStudentIdAsc(1)).thenReturn(List.of(
            new Submission(d1, "STU-1001", SEED_TIME, new BigDecimal("80.0")),
            new Submission(d1, "STU-1002", SEED_TIME, new BigDecimal("95.5"))));
        when(submissionRepository.findByDistributionIdOrderByStudentIdAsc(2)).thenReturn(List.of(
            new Submission(d2, "STU-1003", null, null)));

        ClassReport report = reportService.buildClassReport(1);

        assertThat(report.className()).isEqualTo("5학년 1반");
        assertThat(report.assignmentCount()).isEqualTo(2);
        assertThat(report.submissionCount()).isEqualTo(3);
        assertThat(report.averageScore()).isEqualByComparingTo("87.8");
        assertThat(report.signature()).hasSize(64).isEqualTo(ReportService.sign("1|STU-1001|STU-1002|STU-1003"));
    }

    @Test
    @DisplayName("buildClassReport: 없는 학급은 NotFoundException")
    void throwsWhenClassMissing() {
        when(classRoomRepository.findById(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.buildClassReport(9))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("sign: 같은 입력이면 같은 서명(결정적)")
    void signIsDeterministic() {
        assertThat(ReportService.sign("1|STU-1001")).isEqualTo(ReportService.sign("1|STU-1001"));
        assertThat(ReportService.sign("1|STU-1001")).isNotEqualTo(ReportService.sign("1|STU-1002"));
    }

    private static Distribution distribution(int id, Assignment assignment, ClassRoom classRoom) {
        Distribution distribution = new Distribution(assignment, classRoom, SEED_TIME);
        ReflectionTestUtils.setField(distribution, "id", id);
        return distribution;
    }
}
