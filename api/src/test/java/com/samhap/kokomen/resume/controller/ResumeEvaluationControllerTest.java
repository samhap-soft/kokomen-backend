package com.samhap.kokomen.resume.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;

class ResumeEvaluationControllerTest extends BaseControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String MOCK_GPT_RESPONSE = """
            {
                "technical_skills": {
                    "score": 75,
                    "reason": "- Java, Spring Boot 경험 보유\\n- REST API 설계 능력 확인",
                    "improvements": "- 클라우드 기술 학습 권장\\n- 테스트 코드 작성 경험 필요"
                },
                "project_experience": {
                    "score": 70,
                    "reason": "- 2개의 실무 프로젝트 경험\\n- 팀 프로젝트에서 백엔드 담당",
                    "improvements": "- 프로젝트 성과를 정량적으로 기술\\n- 기술적 도전 과제 추가"
                },
                "problem_solving": {
                    "score": 65,
                    "reason": "- 기본적인 문제 해결 능력 보유\\n- 디버깅 경험 확인",
                    "improvements": "- 트러블슈팅 사례 추가\\n- 성능 최적화 경험 필요"
                },
                "career_growth": {
                    "score": 80,
                    "reason": "- 꾸준한 학습 의지 확인\\n- 관련 분야 경력 일관성",
                    "improvements": "- 장기적인 커리어 목표 명시\\n- 기술 블로그 운영 권장"
                },
                "documentation": {
                    "score": 70,
                    "reason": "- 이력서 구조화 양호\\n- 핵심 내용 전달력 적절",
                    "improvements": "- 오탈자 검토 필요\\n- 포트폴리오 보완 권장"
                },
                "total_score": 72,
                "total_feedback": "전반적으로 백엔드 개발자로서 기본기를 갖추고 있습니다. 클라우드 기술과 테스트 코드 작성 경험을 보완하면 더욱 경쟁력 있는 지원자가 될 수 있습니다."
            }
            """;

    @Test
    void 이력서_평가_성공() throws Exception {
        given(resumeGptClient.requestResumeEvaluation(any())).willReturn(MOCK_GPT_RESPONSE);

        ResumeEvaluationRequest request = new ResumeEvaluationRequest(
                "3년차 백엔드 개발자입니다. Java, Spring Boot를 주로 사용합니다.",
                "GitHub 포트폴리오: https://github.com/example",
                "백엔드 개발자",
                "Spring Boot 기반 REST API 개발 경험자 우대",
                "1-3년"
        );

        mockMvc.perform(post("/api/v3/resume/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technical_skills.score").value(75))
                .andExpect(jsonPath("$.project_experience.score").value(70))
                .andExpect(jsonPath("$.problem_solving.score").value(65))
                .andExpect(jsonPath("$.career_growth.score").value(80))
                .andExpect(jsonPath("$.documentation.score").value(70))
                .andExpect(jsonPath("$.total_score").value(72))
                .andDo(document("resume-evaluation",
                        requestFields(
                                fieldWithPath("resume").type(JsonFieldType.STRING).description("이력서 텍스트"),
                                fieldWithPath("portfolio").type(JsonFieldType.STRING).description("포트폴리오 텍스트").optional(),
                                fieldWithPath("job_position").type(JsonFieldType.STRING).description("지원 직무"),
                                fieldWithPath("job_description").type(JsonFieldType.STRING).description("채용 공고 내용").optional(),
                                fieldWithPath("job_career").type(JsonFieldType.STRING).description("경력 요건")
                        ),
                        responseFields(
                                fieldWithPath("technical_skills").type(JsonFieldType.OBJECT).description("기술 역량 평가"),
                                fieldWithPath("technical_skills.score").type(JsonFieldType.NUMBER).description("기술 역량 점수 (0-100)"),
                                fieldWithPath("technical_skills.reason").type(JsonFieldType.STRING).description("평가 이유"),
                                fieldWithPath("technical_skills.improvements").type(JsonFieldType.STRING).description("개선점"),
                                fieldWithPath("project_experience").type(JsonFieldType.OBJECT).description("프로젝트 경험 평가"),
                                fieldWithPath("project_experience.score").type(JsonFieldType.NUMBER).description("프로젝트 경험 점수 (0-100)"),
                                fieldWithPath("project_experience.reason").type(JsonFieldType.STRING).description("평가 이유"),
                                fieldWithPath("project_experience.improvements").type(JsonFieldType.STRING).description("개선점"),
                                fieldWithPath("problem_solving").type(JsonFieldType.OBJECT).description("문제 해결 능력 평가"),
                                fieldWithPath("problem_solving.score").type(JsonFieldType.NUMBER).description("문제 해결 능력 점수 (0-100)"),
                                fieldWithPath("problem_solving.reason").type(JsonFieldType.STRING).description("평가 이유"),
                                fieldWithPath("problem_solving.improvements").type(JsonFieldType.STRING).description("개선점"),
                                fieldWithPath("career_growth").type(JsonFieldType.OBJECT).description("경력 성장성 평가"),
                                fieldWithPath("career_growth.score").type(JsonFieldType.NUMBER).description("경력 성장성 점수 (0-100)"),
                                fieldWithPath("career_growth.reason").type(JsonFieldType.STRING).description("평가 이유"),
                                fieldWithPath("career_growth.improvements").type(JsonFieldType.STRING).description("개선점"),
                                fieldWithPath("documentation").type(JsonFieldType.OBJECT).description("문서화 평가"),
                                fieldWithPath("documentation.score").type(JsonFieldType.NUMBER).description("문서화 점수 (0-100)"),
                                fieldWithPath("documentation.reason").type(JsonFieldType.STRING).description("평가 이유"),
                                fieldWithPath("documentation.improvements").type(JsonFieldType.STRING).description("개선점"),
                                fieldWithPath("total_score").type(JsonFieldType.NUMBER).description("종합 점수 (0-100)"),
                                fieldWithPath("total_feedback").type(JsonFieldType.STRING).description("종합 피드백")
                        )
                ));
    }
}
