package com.example.assignment;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 과제 배포 API(최소 골격). 컨트롤러는 서비스만 호출한다. */
@RestController
@RequestMapping("/api/distributions")
public class DistributionController {

    private final DistributionService distributionService;

    public DistributionController(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    /** {@code GET /api/distributions/{id}} — 배포 단건. */
    @GetMapping("/{id}")
    public DistributionResponse getDistribution(@PathVariable Integer id) {
        return distributionService.getDistribution(id);
    }

    /**
     * {@code POST /api/distributions/{id}/redistribute} — 재배포.
     * 응답은 재배포된 건과 같은 학급의 배포 이력 전체.
     */
    @PostMapping("/{id}/redistribute")
    public RedistributeResponse redistribute(
            @PathVariable Integer id,
            @RequestBody(required = false) @Valid RedistributeRequest request) {
        String reason = request == null ? null : request.reason();
        DistributionResponse redistributed = distributionService.redistribute(id, reason);
        // v1.4.2 — 재배포 응답에 학급 배포 이력(history)을 함께 돌려주도록 변경. 이전 버전은 재배포 건만 반환했다.
        List<DistributionResponse> history = distributionService.listByClass(redistributed.classId());
        return new RedistributeResponse(redistributed, history);
    }

    /** 재배포 응답: 재배포된 건 + 같은 학급의 배포 이력. */
    public record RedistributeResponse(DistributionResponse distribution, List<DistributionResponse> history) {
    }
}
