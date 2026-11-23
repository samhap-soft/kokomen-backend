package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.answer.domain.AnswerRank;
import java.util.List;

/**
 * 리포트 점수(0~100). LLM이 매기지 않고 서버가 마지막 답변 채점의 평가 포인트 판정과 답변 등급으로 계산한다.
 * 랭킹에 더하는 등급 점수 합(interview.total_score)과는 다른 값이다.
 */
public final class SystemDesignReportScore {

    private SystemDesignReportScore() {
    }

    /**
     * {@code round(100 × (0.5 × 충족률 + 0.5 × 등급 평균))}. 충족률은 충족한 평가 포인트 수 / 평가 포인트 수, 등급 평균은 답변마다
     * A 1.0, B 0.75, C 0.5, D 0.25, F 0으로 바꾼 값의 평균이다. 반올림 경계(x.5)에서 부동소수점 오차로 내려가지 않게 정수로만 계산한다.
     *
     * @param metCount   충족한 평가 포인트 수
     * @param pointCount 문제의 평가 포인트 수(1 이상)
     * @param ranks      질문마다 하나씩 {@value SystemDesignInterview#MAX_QUESTION_COUNT}개의 답변 등급
     */
    public static int calculate(int metCount, int pointCount, List<AnswerRank> ranks) {
        validate(metCount, pointCount, ranks);
        int rankCount = ranks.size();
        int rankQuarterSum = ranks.stream()
                .mapToInt(SystemDesignReportScore::toQuarters)
                .sum();
        // 100 × (0.5 × met / points + 0.5 × (quarters / 4) / ranks) = 100 × (4·ranks·met + points·quarters) / (8·ranks·points)
        long numerator = 100L * (4L * rankCount * metCount + (long) pointCount * rankQuarterSum);
        long denominator = 8L * rankCount * pointCount;
        return Math.toIntExact((2 * numerator + denominator) / (2 * denominator));
    }

    private static void validate(int metCount, int pointCount, List<AnswerRank> ranks) {
        if (pointCount < 1) {
            throw new IllegalArgumentException("평가 포인트는 1개 이상이어야 합니다: %d".formatted(pointCount));
        }
        if (metCount < 0 || metCount > pointCount) {
            throw new IllegalArgumentException(
                    "충족한 평가 포인트 수는 0~%d이어야 합니다: %d".formatted(pointCount, metCount));
        }
        if (ranks.size() != SystemDesignInterview.MAX_QUESTION_COUNT) {
            throw new IllegalArgumentException("답변 등급은 %d개여야 합니다: %d개".formatted(
                    SystemDesignInterview.MAX_QUESTION_COUNT, ranks.size()));
        }
    }

    // 등급 환산값(A 1.0, B 0.75, C 0.5, D 0.25, F 0)을 4배 한 정수
    private static int toQuarters(AnswerRank rank) {
        return switch (rank) {
            case A -> 4;
            case B -> 3;
            case C -> 2;
            case D -> 1;
            case F -> 0;
        };
    }
}
