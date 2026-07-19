package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.interview.domain.InterviewType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * H4 드리프트 가드: 답변 피드백 단계의 참고용 평가 기준 요약이 EVALUATION_CRITERIA 목록에서 파생되므로,
 * 그 목록의 각 카테고리명이 실제 RUBRIC 문구와 답변 피드백 프롬프트에 모두 존재하는지 검증한다.
 * RUBRIC 카테고리명을 바꾸면서 EVALUATION_CRITERIA를 갱신하지 않으면(혹은 그 반대) 이 테스트가 깨진다.
 */
class InterviewEvaluationCriteriaTest {

    @Test
    void 일반_평가_기준은_RUBRIC과_답변피드백_프롬프트_모두에_나타난다() {
        assertCriteriaConsistent(InterviewPromptFragments.EVALUATION_CRITERIA,
                InterviewPromptFragments.RUBRIC, InterviewType.CATEGORY_BASED);
    }

    @Test
    void 코딩_평가_기준은_RUBRIC과_답변피드백_프롬프트_모두에_나타난다() {
        assertCriteriaConsistent(CodingInterviewPromptFragments.EVALUATION_CRITERIA,
                CodingInterviewPromptFragments.RUBRIC, InterviewType.LIVE_CODING);
    }

    @Test
    void 인성_평가_기준은_RUBRIC과_답변피드백_프롬프트_모두에_나타난다() {
        assertCriteriaConsistent(PersonalityInterviewPromptFragments.EVALUATION_CRITERIA,
                PersonalityInterviewPromptFragments.RUBRIC, InterviewType.PERSONALITY);
    }

    private void assertCriteriaConsistent(List<String> criteria, String rubric, InterviewType interviewType) {
        String answerFeedbackPrompt = InterviewSystemMessageBuilder.answerFeedback(interviewType);
        for (String criterion : criteria) {
            assertThat(rubric)
                    .as("RUBRIC은 평가 기준 '%s'을 포함해야 한다", criterion)
                    .contains(criterion);
            assertThat(answerFeedbackPrompt)
                    .as("답변 피드백 프롬프트는 평가 기준 '%s'을 포함해야 한다", criterion)
                    .contains(criterion);
        }
    }
}
