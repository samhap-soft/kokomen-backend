package com.samhap.kokomen.resume.tool;

/**
 * 이력서 기반 질문 생성 / 이력서 평가 시스템 메시지의 GPT·Bedrock 공용 단일 소스.
 * 기존에 {@code ResumeBedrockSystemMessageConstant}, {@code ResumeGptRequest}, {@code ResumeBasedQuestionGptRequest}
 * 에 거의 동일하게 복제돼 있던 시스템 프롬프트를 여기 한곳에서 조립한다(말투는 fragment 정본을 참조).
 */
public final class ResumeSystemMessages {

    private ResumeSystemMessages() {
    }

    public static String questionGeneration() {
        return """
                <role>
                %s
                </role>

                <task>
                제공된 이력서, 포트폴리오, 직무 경력 정보를 분석하여 기술 면접에서 물어볼 핵심 질문들을 생성하라.
                </task>

                %s

                <output>
                제공된 도구를 호출하여 questions 배열을 제출하라. 각 항목은 question(질문 내용)과 reason(질문 선정 이유)을 포함해야 한다.
                </output>
                """.formatted(
                ResumePromptFragments.PERSONA_INTERVIEWER,
                ResumePromptFragments.QUESTION_GENERATION_GUIDE);
    }

    public static String evaluation() {
        return """
                <role>
                %s
                </role>

                <task>
                10년차 시니어 면접관의 시선으로, 지원 직무와 (제공된 경우) 채용 공고를 기준 삼아 이력서와 포트폴리오를 검증하듯 종합 분석하여 카테고리별 객관적 평가와 점수를 산출하고, 지원자가 이력서에서 곧바로 실행할 수 있는 구체적 보완점을 도출하라.
                </task>

                %s

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
                ResumePromptFragments.SENIOR_INTERVIEWER_LENS,
                ResumePromptFragments.EVALUATION_CRITERIA,
                ResumePromptFragments.INDEPENDENCE_PRINCIPLE,
                ResumePromptFragments.SCORE_ANCHORS);
    }
}
