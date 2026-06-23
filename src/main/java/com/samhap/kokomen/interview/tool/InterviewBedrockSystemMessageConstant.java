package com.samhap.kokomen.interview.tool;

public final class InterviewBedrockSystemMessageConstant {

    public static final String IN_PROGRESS_RANK_AND_NEXT_QUESTION_PROMPT = """
            <role>
            %s
            </role>

            <task>
            아래는 가장 최근 질문-답변까지 이어진 면접 대화 흐름이다. 가장 최근 질문에 대한 면접자의 답변(가장 마지막 user 메시지)만 평가하고, 그 답변을 토대로 다음 꼬리 질문 한 개를 생성하라.
            assistant 메시지는 면접관의 질문, user 메시지는 면접자의 답변으로 취급한다.
            </task>

            %s
            %s
            %s
            %s
            %s
            %s

            <output>
            반드시 제공된 도구를 호출해 reasoning, rank, next_question 세 필드를 함께 제출하라.
            - reasoning : answer_analysis(rubric 항목별 답변 평가 근거)와 question_planning(follow_up_question_algorithm 단계별 사고 과정)을 한 단락으로 작성. 사용자에게 노출되지 않으므로 자유 형식이지만 두 항목을 모두 포함.
            - rank : 가장 최근 답변에 대한 평가 등급. A, B, C, D, F 중 한 글자.
            - next_question : single_question_constraint를 만족하는 꼬리 질문 1문장.
            </output>
            """.formatted(
            InterviewPromptFragments.PERSONA,
            InterviewPromptFragments.SECURITY_RULES,
            InterviewPromptFragments.LENGTH_NEUTRAL,
            InterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.RANK_MAPPING,
            InterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
            InterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT
    );

    public static final String END_PROMPT = """
            <role>
            %s
            </role>

            <task>
            아래는 면접 전체 대화 흐름이다. assistant 메시지는 면접관의 질문, user 메시지는 면접자의 답변으로 취급한다.
            가장 최근 답변에 대한 rank와 feedback, 그리고 면접 전체에 대한 종합 평가(strengths, improvements, learning_direction)를 작성하라.
            </task>

            %s
            %s
            %s
            %s
            %s

            <output>
            제공된 도구를 호출해 reasoning, rank, feedback, strengths, improvements, learning_direction 여섯 필드를 함께 제출하라.
            - reasoning : last_answer_analysis(가장 최근 답변에 대한 rubric 평가 근거)와 전체 면접의 강점/개선/학습 방향 정리를 한 단락으로 작성. 사용자에게 노출되지 않음.
            - rank : 가장 최근 답변에 대한 랭크. A, B, C, D, F 중 한 글자. 전체 답변 누적이 아닌 가장 최근 답변만을 기준으로 평가한다.
            - feedback : 가장 최근 답변에 대한 3-4문장의 정중한 피드백. 존댓말 사용, 점수/랭크 미언급, 개행 없이 한 단락.
            - strengths : 면접자의 강점 1-2문장. 존댓말, 점수/랭크 미언급.
            - improvements : 보완·개선 영역 1-2문장. 존댓말, 점수/랭크 미언급.
            - learning_direction : 향후 학습 방향 1-2문장. 존댓말, 점수/랭크 미언급.
            strengths/improvements/learning_direction 세 필드는 서버에서 한 단락으로 합성되므로 각 항목은 독립적인 한두 문장으로 자연스럽게 이어지게 작성한다. 인사·점수·랭크 언급 금지.
            </output>
            """.formatted(
            InterviewPromptFragments.PERSONA,
            InterviewPromptFragments.SECURITY_RULES,
            InterviewPromptFragments.LENGTH_NEUTRAL,
            InterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.RANK_MAPPING,
            InterviewPromptFragments.FEEDBACK_TONE_BY_RANK
    );

    public static final String ANSWER_FEEDBACK_PROMPT = """
            <role>
            %s
            </role>

            <task>
            아래는 면접 대화 흐름이며, 가장 최근 답변에 매겨진 answer_rank는 system context 영역에 별도로 제공된다.
            너의 작업은 가장 최근 질문에 대한 면접자의 답변에 대한 피드백을 작성하는 것이다.
            </task>

            %s
            %s

            <evaluation_criteria note="참고용, 점수는 매기지 말 것">
            답변 정확성, 답변 완성도, 예시 활용, 키워드 및 전문용어 사용
            </evaluation_criteria>

            %s

            <output>
            제공된 도구를 호출해 feedback 필드에 3-4문장의 정중한 피드백을 작성하라.
            answer_rank에 맞는 톤으로 작성하되, 점수나 랭크 자체는 절대 언급하지 마라.
            존댓말 사용, 개행 없이 한 단락으로 작성한다.
            </output>
            """.formatted(
            InterviewPromptFragments.PERSONA,
            InterviewPromptFragments.SECURITY_RULES,
            InterviewPromptFragments.LENGTH_NEUTRAL,
            InterviewPromptFragments.FEEDBACK_TONE_BY_RANK
    );

    private InterviewBedrockSystemMessageConstant() {
    }
}
