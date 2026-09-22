package com.example.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.common.NotFoundException;
import com.example.item.Unit;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class DistributionServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 0, 0, 0);
    private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 9, 1, 9, 0, 0);

    @Mock
    private DistributionRepository distributionRepository;

    private DistributionService service() {
        Clock fixed = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        return new DistributionService(distributionRepository, fixed);
    }

    private static Distribution distribution(int id, String assignmentStatus) {
        Unit unit = new Unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);
        Assignment assignment = new Assignment(10, "분수 덧셈 연습", unit, SEED_TIME.plusDays(14), assignmentStatus);
        ClassRoom classRoom = new ClassRoom(1, "5학년 1반", "TCH-01");
        Distribution distribution = new Distribution(assignment, classRoom, SEED_TIME);
        ReflectionTestUtils.setField(distribution, "id", id);
        return distribution;
    }

    @Test
    @DisplayName("redistribute: 진행 중 과제는 배포 시각을 현재로 갱신하고 재배포 표시를 남긴다")
    void redistributeMarksAndUpdatesTime() {
        Distribution distribution = distribution(5, "O");
        when(distributionRepository.findWithDetailsById(5)).thenReturn(Optional.of(distribution));

        DistributionResponse response = service().redistribute(5, "출제 오류 수정");

        assertThat(response.redistributed()).isTrue();
        assertThat(response.distributedAt()).isEqualTo(NOW);
        assertThat(response.assignmentTitle()).isEqualTo("분수 덧셈 연습");
        assertThat(distribution.isRedistributed()).isTrue();
    }

    @Test
    @DisplayName("redistribute: 마감된 과제는 IllegalStateException(409)")
    void redistributeRejectsClosedAssignment() {
        Distribution distribution = distribution(6, DistributionService.ASSIGNMENT_CLOSED);
        when(distributionRepository.findWithDetailsById(6)).thenReturn(Optional.of(distribution));

        assertThatThrownBy(() -> service().redistribute(6, null))
            .isInstanceOf(IllegalStateException.class);
        assertThat(distribution.isRedistributed()).isFalse();
        assertThat(distribution.getDistributedAt()).isEqualTo(SEED_TIME);
    }

    @Test
    @DisplayName("redistribute: 없는 배포는 NotFoundException")
    void redistributeThrowsWhenMissing() {
        when(distributionRepository.findWithDetailsById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().redistribute(99, null))
            .isInstanceOf(NotFoundException.class);
    }
}
