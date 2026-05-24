package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.resume.tool.ResumePromptFragments;

public final class ResumeBedrockSystemMessageConstant {

    public static final String QUESTION_GENERATION_PROMPT = """
            <role>
            %s
            </role>

            <task>
            사용자가 제공한 이력서, 포트폴리오, 직무 경력 정보를 분석하여 기술 면접에서 물어볼 핵심 질문들을 생성하라.
            </task>

            %s

            <output>
            제공된 도구를 호출하여 questions 배열을 제출하라. 각 항목은 question(질문 내용)과 reason(질문 선정 이유)을 포함해야 한다.
            </output>
            """.formatted(
            ResumePromptFragments.PERSONA_INTERVIEWER,
            ResumePromptFragments.QUESTION_GENERATION_GUIDE
    );

    public static final String EVALUATION_PROMPT = """
            <role>
            %s
            </role>

            <task>
            사용자가 제공한 채용 공고와 직무를 기반으로 이력서와 포트폴리오를 종합적으로 분석하여 객관적인 평가 및 점수를 산출하라.
            </task>

            %s

            %s

            %s

            %s

            <output>
            제공된 도구를 호출하여 다음 필드를 모두 제출하라.
            - technical_skills, project_experience, problem_solving, career_growth, documentation : 각 카테고리는 reasoning(점수 산정 전 사고 과정), score(0-100, score_anchors 기준), reason(평가 이유 항목 배열, 2-6개), improvements(보완 사항 항목 배열, 2-6개)
            - total_feedback : 강점·개선·학습 방향을 포함한 종합 총평 (한 단락)
            (종합 점수는 서버에서 가중평균으로 재계산하므로 별도 출력하지 않는다.)
            </output>
            """.formatted(
            ResumePromptFragments.PERSONA_RECRUITER,
            ResumePromptFragments.SECURITY_RULES,
            ResumePromptFragments.EVALUATION_CRITERIA,
            ResumePromptFragments.INDEPENDENCE_PRINCIPLE,
            ResumePromptFragments.SCORE_ANCHORS
    );

    private ResumeBedrockSystemMessageConstant() {
    }
}
