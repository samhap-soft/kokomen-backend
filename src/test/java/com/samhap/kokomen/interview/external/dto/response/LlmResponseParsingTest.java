package com.samhap.kokomen.interview.external.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import org.junit.jupiter.api.Test;

/**
 * Stage 0 특성화 테스트: 리팩토링 전 provider별 응답 파싱 동작을 고정한다.
 * 운영 ObjectMapper와 동일하게 SNAKE_CASE + FAIL_ON_UNKNOWN_PROPERTIES=false 로 구성한다
 * (reasoning 등 스키마에만 있고 응답 record에는 없는 필드가 존재하므로 unknown 허용에 의존한다).
 */
class LlmResponseParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void Bedrock_진행_응답에서_랭크만_추출한다() {
        BedrockConverseResponse response = BedrockResponseFixtureBuilder.builder()
                .answerRank(AnswerRank.B)
                .nextQuestion("스레드 안전이란 무엇인가요?")
                .buildProceed();

        AnswerRankResponse rankResponse = response.extractAnswerRankResponse(objectMapper);

        assertThat(rankResponse.rank()).isEqualTo("B");
    }

    @Test
    void Bedrock_진행_응답에서_다음_질문을_추출한다() {
        BedrockConverseResponse response = BedrockResponseFixtureBuilder.builder()
                .nextQuestion("데드락이 발생하는 조건은 무엇인가요?")
                .buildProceed();

        NextQuestionResponse nextQuestionResponse = response.extractNextQuestionResponse(objectMapper);

        assertThat(nextQuestionResponse.nextQuestion()).isEqualTo("데드락이 발생하는 조건은 무엇인가요?");
    }

    @Test
    void Bedrock_종료_응답에서_랭크와_피드백을_추출한다() {
        BedrockConverseResponse response = BedrockResponseFixtureBuilder.builder()
                .answerRank(AnswerRank.A)
                .feedback("핵심을 정확히 짚었습니다.")
                .buildEnd();

        AnswerFeedbackResponse feedbackResponse = response.extractAnswerFeedbackResponse(objectMapper);

        assertThat(feedbackResponse.rank()).isEqualTo("A");
        assertThat(feedbackResponse.feedback()).isEqualTo("핵심을 정확히 짚었습니다.");
    }

    @Test
    void Bedrock_종료_응답에서_종합_피드백을_추출하고_한_단락으로_합성한다() {
        BedrockConverseResponse response = BedrockResponseFixtureBuilder.builder()
                .strengths("개념 이해가 명확합니다.")
                .improvements("실무 사례가 부족합니다.")
                .learningDirection("동시성 기초를 더 학습하세요.")
                .buildEnd();

        TotalFeedbackResponse totalFeedbackResponse = response.extractTotalFeedbackResponse(objectMapper);

        assertThat(totalFeedbackResponse.strengths()).isEqualTo("개념 이해가 명확합니다.");
        assertThat(totalFeedbackResponse.improvements()).isEqualTo("실무 사례가 부족합니다.");
        assertThat(totalFeedbackResponse.learningDirection()).isEqualTo("동시성 기초를 더 학습하세요.");
        assertThat(totalFeedbackResponse.composeTotalFeedback())
                .isEqualTo("개념 이해가 명확합니다. 실무 사례가 부족합니다. 동시성 기초를 더 학습하세요.");
    }
}
