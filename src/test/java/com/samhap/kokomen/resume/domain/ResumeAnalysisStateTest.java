package com.samhap.kokomen.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ResumeAnalysisStateTest {

    private static final int STATE_COLUMN_LENGTH = 30;

    @Test
    void 평가가_공개되는_상태는_평가완료와_완료와_질문실패다() {
        assertThat(Arrays.stream(ResumeAnalysisState.values())
                .filter(ResumeAnalysisState::isEvaluationRevealed)
                .toList())
                .containsExactly(ResumeAnalysisState.EVALUATION_COMPLETED, ResumeAnalysisState.COMPLETED,
                        ResumeAnalysisState.QUESTION_FAILED);
    }

    @Test
    void 질문이_준비된_상태는_COMPLETED뿐이다() {
        assertThat(Arrays.stream(ResumeAnalysisState.values())
                .filter(ResumeAnalysisState::isQuestionReady)
                .toList())
                .containsExactly(ResumeAnalysisState.COMPLETED);
    }

    @Test
    void 종단_상태는_완료와_평가실패와_질문실패다() {
        assertThat(Arrays.stream(ResumeAnalysisState.values())
                .filter(ResumeAnalysisState::isTerminal)
                .toList())
                .containsExactly(ResumeAnalysisState.COMPLETED, ResumeAnalysisState.EVALUATION_FAILED,
                        ResumeAnalysisState.QUESTION_FAILED);
    }

    @Test
    void PENDING은_공개도_준비도_종단도_아니다() {
        assertThat(ResumeAnalysisState.PENDING.isEvaluationRevealed()).isFalse();
        assertThat(ResumeAnalysisState.PENDING.isQuestionReady()).isFalse();
        assertThat(ResumeAnalysisState.PENDING.isTerminal()).isFalse();
    }

    @Test
    void EVALUATION_COMPLETED는_공개되지만_종단은_아니다() {
        assertThat(ResumeAnalysisState.EVALUATION_COMPLETED.isEvaluationRevealed()).isTrue();
        assertThat(ResumeAnalysisState.EVALUATION_COMPLETED.isQuestionReady()).isFalse();
        assertThat(ResumeAnalysisState.EVALUATION_COMPLETED.isTerminal()).isFalse();
    }

    @Test
    void EVALUATION_FAILED는_종단이지만_평가가_공개되지_않는다() {
        assertThat(ResumeAnalysisState.EVALUATION_FAILED.isTerminal()).isTrue();
        assertThat(ResumeAnalysisState.EVALUATION_FAILED.isEvaluationRevealed()).isFalse();
    }

    @Test
    void 실패_원인은_설계에_확정된_7개다() {
        assertThat(ResumeAnalysisFailureReason.values()).containsExactly(
                ResumeAnalysisFailureReason.EVALUATION_LLM, ResumeAnalysisFailureReason.OUTPUT_TRUNCATED,
                ResumeAnalysisFailureReason.QUESTION_LLM, ResumeAnalysisFailureReason.PERSISTENCE,
                ResumeAnalysisFailureReason.CAPACITY, ResumeAnalysisFailureReason.STALE_SWEEP,
                ResumeAnalysisFailureReason.GUEST_LIMIT);
    }

    @Test
    void 상태와_실패_원인_이름은_모두_VARCHAR_30_안에_들어간다() {
        // failure_reason에 30자를 넘는 값이 들어가면 Data too long으로 실패 기록 트랜잭션 자체가 롤백되어
        // 행이 PENDING에 남는다. state는 EVALUATION_COMPLETED가 정확히 20자라 여유가 10자뿐이다.
        assertThat(Arrays.stream(ResumeAnalysisState.values()).map(Enum::name).toList())
                .allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(STATE_COLUMN_LENGTH));
        assertThat(Arrays.stream(ResumeAnalysisFailureReason.values()).map(Enum::name).toList())
                .allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(STATE_COLUMN_LENGTH));
    }
}
