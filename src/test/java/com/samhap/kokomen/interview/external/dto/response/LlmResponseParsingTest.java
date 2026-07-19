package com.samhap.kokomen.interview.external.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.fixture.interview.BedrockResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.GptResponseFixtureBuilder;
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

    @Test
    void GPT_진행_응답에서_랭크와_피드백을_한_번에_추출한다() {
        GptResponse response = GptResponseFixtureBuilder.builder()
                .answerRank(AnswerRank.C)
                .feedback("부분적으로 이해하고 있습니다.")
                .buildProceed();

        AnswerFeedbackResponse feedbackResponse = response.extractAnswerFeedbackResponse(objectMapper);

        assertThat(feedbackResponse.rank()).isEqualTo("C");
        assertThat(feedbackResponse.feedback()).isEqualTo("부분적으로 이해하고 있습니다.");
    }

    @Test
    void GPT_진행_응답에서_다음_질문을_추출한다() {
        GptResponse response = GptResponseFixtureBuilder.builder()
                .nextQuestion("뮤텍스와 세마포어의 차이는 무엇인가요?")
                .buildProceed();

        NextQuestionResponse nextQuestionResponse = response.extractNextQuestionResponse(objectMapper);

        assertThat(nextQuestionResponse.nextQuestion()).isEqualTo("뮤텍스와 세마포어의 차이는 무엇인가요?");
    }

    @Test
    void GPT_종료_응답에서_종합_피드백을_추출한다() {
        GptResponse response = GptResponseFixtureBuilder.builder()
                .strengths("논리 전개가 좋습니다.")
                .improvements("용어 사용을 다듬으세요.")
                .learningDirection("네트워크 기초를 학습하세요.")
                .buildEnd();

        TotalFeedbackResponse totalFeedbackResponse = response.extractTotalFeedbackResponse(objectMapper);

        assertThat(totalFeedbackResponse.composeTotalFeedback())
                .isEqualTo("논리 전개가 좋습니다. 용어 사용을 다듬으세요. 네트워크 기초를 학습하세요.");
    }

    @Test
    void 랭크만_추출하는_기능은_GPT에서_지원하지_않는다() {
        GptResponse response = GptResponseFixtureBuilder.builder().buildProceed();

        assertThatThrownBy(() -> response.extractAnswerRankResponse(objectMapper))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
