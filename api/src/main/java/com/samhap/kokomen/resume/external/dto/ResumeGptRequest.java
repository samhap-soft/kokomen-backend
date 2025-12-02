package com.samhap.kokomen.resume.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import java.util.List;

public record ResumeGptRequest(
        String model,
        @JsonProperty("messages")
        List<ResumeGptMessage> messages
) {

    private static final String GPT_MODEL = "gpt-4.1-mini";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            당신은 10년 이상의 경력을 가진 전문 채용 담당자이자 기술 면접관입니다. 제공된 채용 공고와 직무를 기반으로 제공된 이력서와 포트폴리오를 종합적으로 분석하여 객관적인 평가 및 점수를 산출해 주세요. 점수는 최대한 엄격하게 평가하여 산출해주세요.
            <resume>
            {{resume_text}}
            </resume>
            <portfolio>
            {{portfolio_text}}
            </portfolio>
            <target_position>
            {{job_position}}
            </target_position>
            <job_requirements>
            {{job_description}}
            </job_requirements>
            <job_career>
            {{job_career}}
            </job_career>
            ---
            ## 이력서 및 포트폴리오 종합 평가
            ### 평가 기준
            1. **기술 역량 (Technical Skills)** - 30점
            - 기술 스택의 다양성과 깊이
            - 최신 기술 트렌드 반영도
            - 기술 수준의 구체성 (단순 나열 vs 실전 경험)
            - 기술에 대한 깊이를 기반으로 문제 해결 역량의 수준
            2. **프로젝트 경험 (Project Experience)** - 25점
            - 프로젝트의 규모와 복잡도
            - 역할과 책임의 명확성
            - 정량적 성과 제시 여부
            3. **문제 해결 능력 (Problem Solving)** - 20점
            - 기술적 도전 과제 해결 사례
            - 트러블슈팅 경험
            - 최적화 및 개선 사례
            4. **경력 일관성 및 성장성 (Career Growth)** - 15점
            - 경력 발전 경로의 논리성
            - 지속적인 학습 및 성장 증거
            - 신입일 경우 지원자의 실력과 신입의 수준 대비 잠재성
            - 경력 공백 또는 이직이 있을 경우 이유에 대한 합리성
            5. **문서화 및 표현력 (Documentation)** - 10점
            - 이력서/포트폴리오의 구조화 정도
            - 핵심 내용 전달력
            - 오탈자 및 형식 완성도
            ### 평가 세부 지침
            - 각 항목별로 0-만점 범위에서 점수 부여
            - 강점은 구체적 근거와 함께 명시
            - 개선점은 실행 가능한 조언 제공
            - 지원 직무와의 적합도(Job Fit) 별도 평가
            ## 출력 형식
            다음 JSON으로 평가 결과를 생성해주세요.
            {
            "technical_skills" : {
            "score" : 평가 점수를 숫자로 표기(0-100),
            "reason" : "평가에 대한 이유를 정리, 불렛 포인트 사용(-)",
            "improvements" : "보완하면 좋을 점들을 정리, 불렛 포인트 사용(-)"
            },
            "project_experience" : {
            "score" : 평가 점수를 숫자로 표기(0-100),
            "reason" : "평가에 대한 이유를 정리, 불렛 포인트 사용(-)",
            "improvements" : "보완하면 좋을 점들을 정리, 불렛 포인트 사용(-)"
            },
            "problem_solving": {
            "score" : 평가 점수를 숫자로 표기(0-100),
            "reason" : "평가에 대한 이유를 정리, 불렛 포인트 사용(-)",
            "improvements" : "보완하면 좋을 점들을 정리, 불렛 포인트 사용(-)"
            },
            "career_growth" : {
            "score" : 평가 점수를 숫자로 표기(0-100),
            "reason" : "평가에 대한 이유를 정리, 불렛 포인트 사용(-)",
            "improvements" : "보완하면 좋을 점들을 정리, 불렛 포인트 사용(-)"
            },
            "documentation" : {
            "score" : 평가 점수를 숫자로 표기(0-100),
            "reason" : "평가에 대한 이유를 정리, 불렛 포인트 사용(-)",
            "improvements" : "보완하면 좋을 점들을 정리, 불렛 포인트 사용(-)"
            },
            "total_score" : 종합적인 점수(0-100),
            "total_feedback" : "종합적인 점수에 대한 설명과 추가적으로 보완하면 좋을 점들을 정리"
            }
            - 다른 추가적인 텍스트를 금지합니다. 출력 형식은 오로지 Json 형식으로만 제공해주세요.
            - JSON형식이면서 형식 표기를 위한 마크다운(```)을 사용하지 마세요. 텍스트에는 json 텍스트만 출력되어야 합니다.
            - 지원자가 실제로 수행한 역할과 책임에 초점을 맞추세요
            - 최신 기술 트렌드와 베스트 프랙티스를 반영하세요
            """;

    public static ResumeGptRequest create(ResumeEvaluationRequest request) {
        String prompt = SYSTEM_PROMPT_TEMPLATE
                .replace("{{resume_text}}", request.resume())
                .replace("{{portfolio_text}}", request.portfolio() != null ? request.portfolio() : "")
                .replace("{{job_position}}", request.jobPosition())
                .replace("{{job_description}}", request.jobDescription() != null ? request.jobDescription() : "")
                .replace("{{job_career}}", request.jobCareer());

        List<ResumeGptMessage> messages = List.of(new ResumeGptMessage("user", prompt));
        return new ResumeGptRequest(GPT_MODEL, messages);
    }
}
