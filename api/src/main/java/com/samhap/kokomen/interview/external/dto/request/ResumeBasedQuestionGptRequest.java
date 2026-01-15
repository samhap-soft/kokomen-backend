package com.samhap.kokomen.interview.external.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ResumeBasedQuestionGptRequest(
        String model,
        @JsonProperty("messages")
        List<ResumeBasedQuestionGptMessage> messages
) {

    private static final String GPT_MODEL = "gpt-4.1-mini";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 10년 이상의 경력을 가진 전문 기술 면접관입니다. 제공된 이력서와 포트폴리오를 분석하여 기술 면접에서 물어볼 핵심 질문들을 생성해주세요.

            <resume>
            {{resume_text}}
            </resume>
            <portfolio>
            {{portfolio_text}}
            </portfolio>
            <job_career>
            {{job_career}}
            </job_career>
            <question_count>
            {{question_count}}
            </question_count>

            ---
            ## 질문 생성 지침
            1. 이력서와 포트폴리오에 기재된 기술 스택과 프로젝트 경험을 기반으로 질문을 생성하세요.
            2. 지원자의 실제 역량을 파악할 수 있는 깊이 있는 기술 질문을 생성하세요.
            3. 단순 암기가 아닌 경험과 이해도를 확인할 수 있는 질문을 생성하세요.
            4. 신입/경력에 따라 질문의 난이도를 조절하세요.
            5. 각 질문에 대해 왜 이 질문을 선택했는지 이유를 함께 제공하세요.

            ## 질문 유형 가이드
            - 프로젝트에서 사용한 특정 기술에 대한 심층 질문
            - 문제 해결 경험에 대한 상황 기반 질문
            - 기술 선택의 이유와 트레이드오프에 대한 질문
            - 협업 및 커뮤니케이션 관련 기술적 질문

            ## 출력 형식
            다음 JSON 형식으로 정확히 {{question_count}}개의 질문을 생성해주세요:
            {
              "questions": [
                {
                  "question": "면접 질문 내용",
                  "reason": "이 질문을 선택한 이유"
                }
              ]
            }

            - 다른 추가적인 텍스트를 금지합니다. 출력 형식은 오로지 JSON 형식으로만 제공해주세요.
            - JSON 형식이면서 형식 표기를 위한 마크다운(```)을 사용하지 마세요. 텍스트에는 JSON 텍스트만 출력되어야 합니다.
            """;

    public static ResumeBasedQuestionGptRequest create(
            String resumeText,
            String portfolioText,
            String jobCareer,
            int questionCount
    ) {
        String prompt = SYSTEM_PROMPT_TEMPLATE
                .replace("{{resume_text}}", resumeText)
                .replace("{{portfolio_text}}", portfolioText != null ? portfolioText : "포트폴리오가 제공되지 않았습니다.")
                .replace("{{job_career}}", jobCareer)
                .replace("{{question_count}}", String.valueOf(questionCount));

        List<ResumeBasedQuestionGptMessage> messages = List.of(
                new ResumeBasedQuestionGptMessage("user", prompt)
        );
        return new ResumeBasedQuestionGptRequest(GPT_MODEL, messages);
    }
}
