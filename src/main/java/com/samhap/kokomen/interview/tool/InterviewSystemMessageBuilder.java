package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.InterviewType;
import java.util.ArrayList;
import java.util.List;

/**
 * GPT/Bedrock 공용 면접 시스템 메시지 조립기.
 * <p>
 * 기존에 {@code GptSystemMessageConstant}(6종)와 3개의 {@code *BedrockSystemMessageConstant}(9종)로
 * 손 복제돼 있던 시스템 메시지를, (interviewType, 단계, feedbackInline) 파라미터로 단일하게 생성한다.
 * <ul>
 *   <li>도메인 차이(persona, rubric, 대화 프레이밍)는 {@link InterviewPromptProfile}이 담당.</li>
 *   <li>provider 차이는 진행(proceed) 단계의 {@code feedbackInline} 하나로 수렴한다.
 *       GPT는 한 번의 호출로 feedback까지 생성하므로 {@code true}, Bedrock은 rank/next_question만 먼저
 *       생성(2콜 구조)하므로 {@code false}. 종료(end)는 두 provider가 동일하다.</li>
 *   <li>말투 규칙은 {@link InterviewPromptFragments#FEEDBACK_TONE_BY_RANK} 한곳만 참조한다(재기입 금지).</li>
 * </ul>
 */
public final class InterviewSystemMessageBuilder {

    private InterviewSystemMessageBuilder() {
    }

    /**
     * 진행(다음 꼬리 질문 생성) 단계 시스템 메시지.
     *
     * @param feedbackInline true면 같은 호출에서 feedback까지 생성(GPT), false면 rank+next_question만 생성(Bedrock)
     */
    public static String proceed(InterviewType interviewType, boolean feedbackInline) {
        InterviewPromptProfile profile = InterviewPromptProfile.from(interviewType);

        String task = """
                아래는 %s이다. %s
                가장 최근 질문에 대한 면접자의 답변(가장 마지막 user 메시지)만 평가하고, %s""".formatted(
                profile.flowNoun(),
                profile.conversationContext(),
                proceedActionClause(profile, feedbackInline));

        List<String> fragments = new ArrayList<>(List.of(
                InterviewPromptFragments.SENIOR_STANDARD,
                profile.securityRules(),
                profile.lengthNeutral(),
                profile.rubric(),
                InterviewPromptFragments.RANK_MAPPING));
        if (feedbackInline) {
            fragments.add(InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
        }
        fragments.add(profile.followUpAlgorithm());
        fragments.add(profile.singleQuestionConstraint());

        return assemble(profile.persona(), task, fragments, proceedOutput(profile, feedbackInline));
    }

    /**
     * 종료(마지막 답변 평가 + 전체 종합 평가) 단계 시스템 메시지. GPT/Bedrock 공용.
     */
    public static String end(InterviewType interviewType) {
        InterviewPromptProfile profile = InterviewPromptProfile.from(interviewType);

        String task = """
                아래는 %s 전체이다. %s
                가장 최근 답변에 대한 rank와 feedback, 그리고 면접 전체에 대한 종합 평가(strengths, improvements, learning_direction)를 작성하라.""".formatted(
                profile.flowNoun(),
                profile.conversationContext());

        List<String> fragments = List.of(
                InterviewPromptFragments.SENIOR_STANDARD,
                profile.securityRules(),
                profile.lengthNeutral(),
                profile.rubric(),
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);

        return assemble(profile.persona(), task, fragments, endOutput(profile));
    }

    /**
     * 답변 피드백(rank는 system context로 별도 주입) 단계 시스템 메시지. Bedrock 전용(2콜 구조의 두 번째 콜).
     */
    public static String answerFeedback(InterviewType interviewType) {
        InterviewPromptProfile profile = InterviewPromptProfile.from(interviewType);

        String task = """
                아래는 %s이며, 가장 최근 답변에 매겨진 answer_rank는 system context 영역에 별도로 제공된다.
                너의 작업은 가장 최근 질문에 대한 면접자의 %s에 대한 피드백을 작성하는 것이다.""".formatted(
                profile.flowNoun(),
                profile.answerNoun());

        List<String> fragments = List.of(
                InterviewPromptFragments.SENIOR_STANDARD,
                profile.securityRules(),
                profile.lengthNeutral(),
                evaluationCriteriaBlock(profile),
                InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);

        return assemble(profile.persona(), task, fragments, answerFeedbackOutput(profile));
    }

    private static String proceedActionClause(InterviewPromptProfile profile, boolean feedbackInline) {
        if (feedbackInline) {
            return "그 %s에 대한 피드백과 다음 꼬리 질문을 생성하라.".formatted(profile.answerNoun());
        }
        return "그 %s에 기반해 다음 꼬리 질문 한 개를 생성하라.".formatted(profile.answerNoun());
    }

    private static String proceedOutput(InterviewPromptProfile profile, boolean feedbackInline) {
        if (feedbackInline) {
            return """
                    제공된 도구를 호출해 reasoning, rank, feedback, next_question 네 필드를 함께 제출하라.
                    - reasoning : answer_analysis(rubric 항목별 평가 근거)와 question_planning(follow_up_question_algorithm 단계별 사고 과정)을 한 단락으로 작성한다. 사용자에게 노출되지 않으므로 자유 형식이되 두 항목을 모두 포함한다.
                    - rank : 위 평가 기준과 rank_mapping에 따라 산출한 A/B/C/D/F 중 한 글자.
                    - feedback : 가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank 규칙을 따른다.%s
                    - next_question : single_question_constraint를 모두 충족하는 꼬리 질문 1문장.""".formatted(
                    profile.feedbackFormatNote());
        }
        return """
                제공된 도구를 호출해 reasoning, rank, next_question 세 필드를 함께 제출하라.
                - reasoning : answer_analysis(rubric 항목별 평가 근거)와 question_planning(follow_up_question_algorithm 단계별 사고 과정)을 한 단락으로 작성한다. 사용자에게 노출되지 않으므로 자유 형식이되 두 항목을 모두 포함한다.
                - rank : 위 평가 기준과 rank_mapping에 따라 산출한 A/B/C/D/F 중 한 글자.
                - next_question : single_question_constraint를 모두 충족하는 꼬리 질문 1문장.""";
    }

    private static String endOutput(InterviewPromptProfile profile) {
        return """
                제공된 도구를 호출해 reasoning, rank, feedback, strengths, improvements, learning_direction 여섯 필드를 함께 제출하라.
                - reasoning : last_answer_analysis(가장 최근 답변에 대한 rubric 평가 근거)와 전체 면접의 강점/개선/학습 방향 정리를 한 단락으로 작성한다. 사용자에게 노출되지 않는다.
                - rank : 가장 최근 답변에 대한 rank. A/B/C/D/F 중 한 글자. 전체 답변 누적이 아닌 가장 최근 답변만을 기준으로 평가한다.
                - feedback : 가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank 규칙을 따른다.%s
                - strengths : 면접자의 강점 1-2문장.
                - improvements : 보완·개선 영역 1-2문장.
                - learning_direction : 향후 학습 방향 1-2문장.
                strengths/improvements/learning_direction 세 필드는 서버에서 한 단락으로 합성되므로 각 항목을 독립적인 한두 문장의 존댓말로 자연스럽게 작성하고, 인사·점수·랭크는 언급하지 않는다.""".formatted(
                profile.feedbackFormatNote());
    }

    private static String answerFeedbackOutput(InterviewPromptProfile profile) {
        return """
                제공된 도구를 호출해 feedback 필드에 3-4문장의 정중한 피드백을 작성하라.
                answer_rank에 맞는 톤으로 작성하되 점수나 랭크 자체는 언급하지 않는다. feedback_tone_by_rank 규칙을 따른다.%s""".formatted(
                profile.feedbackFormatNote());
    }

    private static String evaluationCriteriaBlock(InterviewPromptProfile profile) {
        return """
                <evaluation_criteria note="참고용, 점수는 매기지 말 것">
                %s
                </evaluation_criteria>""".formatted(profile.evaluationCriteriaSummary());
    }

    private static String assemble(String persona, String task, List<String> fragments, String output) {
        return """
                <role>
                %s
                </role>

                <task>
                %s
                </task>

                %s

                <output>
                %s
                </output>
                """.formatted(persona, task, String.join("\n", fragments), output);
    }
}
