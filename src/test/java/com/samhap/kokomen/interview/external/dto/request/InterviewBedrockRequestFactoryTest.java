package com.samhap.kokomen.interview.external.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.domain.InterviewType;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

class InterviewBedrockRequestFactoryTest {

    @Test
    void 라이브_코테_진행_시스템_프롬프트는_CS_면접용과_다르고_코드_평가_지침을_포함한다() {
        String csPrompt = firstText(InterviewBedrockRequestFactory.createProceedSystem(InterviewType.CATEGORY_BASED));
        String codingPrompt = firstText(InterviewBedrockRequestFactory.createProceedSystem(InterviewType.LIVE_CODING));

        assertThat(codingPrompt).isNotEqualTo(csPrompt);
        assertThat(codingPrompt).contains("코드");
    }

    @Test
    void 라이브_코테_종료_시스템_프롬프트는_CS_면접용과_다르고_코드_평가_지침을_포함한다() {
        String csPrompt = firstText(InterviewBedrockRequestFactory.createEndSystem(InterviewType.CATEGORY_BASED));
        String codingPrompt = firstText(InterviewBedrockRequestFactory.createEndSystem(InterviewType.LIVE_CODING));

        assertThat(codingPrompt).isNotEqualTo(csPrompt);
        assertThat(codingPrompt).contains("코드");
    }

    @Test
    void 라이브_코테_답변_피드백_시스템_프롬프트는_CS_면접용과_다르고_코드_블록_지침을_포함한다() {
        String csPrompt = firstText(
                InterviewBedrockRequestFactory.createAnswerFeedbackSystem(InterviewType.CATEGORY_BASED, AnswerRank.A));
        String codingPrompt = firstText(
                InterviewBedrockRequestFactory.createAnswerFeedbackSystem(InterviewType.LIVE_CODING, AnswerRank.A));

        assertThat(codingPrompt).isNotEqualTo(csPrompt);
        assertThat(codingPrompt).contains("코드 블록");
    }

    private String firstText(List<SystemContentBlock> blocks) {
        return blocks.get(0).text();
    }
}
