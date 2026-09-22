package com.example.assignment;

import com.example.common.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학급 리포트 생성. */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 외부 집계 시스템이 요구하는 서명 반복 횟수(스트레칭). */
    static final int SIGNATURE_ROUNDS = 200_000;

    private final ClassRoomRepository classRoomRepository;
    private final DistributionRepository distributionRepository;
    private final SubmissionRepository submissionRepository;

    public ReportService(
            ClassRoomRepository classRoomRepository,
            DistributionRepository distributionRepository,
            SubmissionRepository submissionRepository) {
        this.classRoomRepository = classRoomRepository;
        this.distributionRepository = distributionRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * 학급 리포트를 만든다: 배포 · 제출을 읽어 집계한 뒤 외부 집계 시스템 형식의 서명을 붙인다.
     */
    @Transactional
    public ClassReport buildClassReport(Integer classId) {
        ClassRoom classRoom = classRoomRepository.findById(classId)
            .orElseThrow(() -> new NotFoundException("학급이 없습니다: id=" + classId));

        List<Distribution> distributions = distributionRepository.findByClassRoomIdOrderByDistributedAtAsc(classId);

        int submissionCount = 0;
        BigDecimal scoreSum = BigDecimal.ZERO;
        int scoredCount = 0;
        StringBuilder payload = new StringBuilder(classRoom.getId().toString());
        for (Distribution distribution : distributions) {
            List<Submission> submissions =
                submissionRepository.findByDistributionIdOrderByStudentIdAsc(distribution.getId());
            submissionCount += submissions.size();
            for (Submission submission : submissions) {
                payload.append('|').append(submission.getStudentId());
                if (submission.getScore() != null) {
                    scoreSum = scoreSum.add(submission.getScore());
                    scoredCount++;
                }
            }
        }

        BigDecimal averageScore = scoredCount == 0
            ? null
            : scoreSum.divide(BigDecimal.valueOf(scoredCount), 1, RoundingMode.HALF_UP);

        // TODO: 트랜잭션 밖으로 — 외부 집계 시스템 호출을 흉내내는 긴 루프.
        //       DB 작업은 위에서 끝났는데 커넥션(트랜잭션)을 쥔 채로 돈다.
        String signature = sign(payload.toString());

        log.info("built class report for class {}: {} distributions, {} submissions",
            classId, distributions.size(), submissionCount);
        return new ClassReport(
            classRoom.getId(),
            classRoom.getName(),
            distributions.size(),
            submissionCount,
            averageScore,
            signature);
    }

    /** 외부 집계 시스템 서명 형식: SHA-256 을 {@link #SIGNATURE_ROUNDS} 회 반복. */
    static String sign(String payload) {
        MessageDigest digest = sha256();
        byte[] current = payload.getBytes(StandardCharsets.UTF_8);
        for (int round = 0; round < SIGNATURE_ROUNDS; round++) {
            digest.reset();
            current = digest.digest(current);
        }
        return HexFormat.of().formatHex(current);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 지원하지 않는 JVM 입니다", e);
        }
    }
}
