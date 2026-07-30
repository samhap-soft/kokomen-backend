package com.samhap.kokomen.resume.external.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.interview.external.dto.request.ResumeBasedQuestionGptRequest;
import com.samhap.kokomen.resume.tool.ResumePromptFragments;
import com.samhap.kokomen.resume.tool.ResumeSystemMessages;
import com.samhap.kokomen.resume.tool.ResumeToolNames;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * Stage 6 정합성 테스트: 이력서 질문생성(구 플로우, Task 9에서 삭제 예정) 시스템 프롬프트가 GPT/Bedrock
 * 모두 단일 소스({@link ResumeSystemMessages})에서 나오는지, 도구 이름이 단일화됐는지, 페르소나 인칭이
 * 통일됐는지 검증한다(H2/H5).
 * 평가 쪽 동등 검증은 이력서 평가 플로우 전삭제(Task 8)로 이 클래스에서 제거됐고
 * {@code ResumeAnalysisSystemMessageConsistencyTest}가 신규 분석 플로우에 대해 담당한다.
 */
class ResumeSystemMessageConsistencyTest {

    @Test
    void 질문생성_시스템_메시지는_면접관_페르소나와_질문생성_가이드와_probe_렌즈를_포함한다() {
        assertThat(ResumeSystemMessages.questionGeneration()).contains(
                ResumePromptFragments.PERSONA_INTERVIEWER,
                ResumePromptFragments.QUESTION_GENERATION_GUIDE,
                ResumePromptFragments.QUESTION_PROBE_LENS);
    }

    @Test
    void 질문생성_시스템_메시지는_GPT와_Bedrock이_단일_소스에서_나온다() {
        String single = ResumeSystemMessages.questionGeneration();

        String bedrock = firstText(ResumeBedrockRequestFactory.createQuestionGenerationSystem());
        String gpt = ResumeBasedQuestionGptRequest.create("이력서", "포트폴리오", "3년차 백엔드", 0.7)
                .messages().get(0).content();

        assertThat(bedrock).isEqualTo(single);
        assertThat(gpt).isEqualTo(single);
    }

    @Test
    void 도구_이름은_GPT와_Bedrock이_동일하게_단일화됐다() {
        assertThat(ResumeBasedQuestionGptRequest.QUESTION_GENERATION_FUNCTION_NAME)
                .isEqualTo(ResumeBedrockRequestFactory.QUESTION_GENERATION_TOOL_NAME)
                .isEqualTo(ResumeToolNames.QUESTION_GENERATION);
    }

    @Test
    void 이력서_페르소나_인칭은_면접_도메인과_동일하게_너로_통일됐다() {
        assertThat(ResumePromptFragments.PERSONA_INTERVIEWER).startsWith("너는");
    }

    private String firstText(List<SystemContentBlock> blocks) {
        return blocks.get(0).text();
    }
}
