package com.samhap.kokomen.interview.tool;

public final class GptSystemMessageConstant {

    public static final String PROCEED_SYSTEM_MESSAGE = """
            <role>
            %s
            </role>

            <task>
            아래는 가장 최근 질문-답변까지 이어진 면접 대화 흐름이다. 가장 최근 질문에 대한 면접자의 답변(가장 마지막 user 메시지)만 평가하고, 그 답변에 대한 피드백과 다음 꼬리 질문을 생성하라.
            assistant 메시지는 면접관의 질문, user 메시지는 면접자의 답변으로 취급한다.
            </task>

            %s
            %s
            %s
            %s
            %s
            %s
            %s

            <output>
            반드시 제공된 함수(generate_feedback)를 호출하여 다음 네 필드를 함께 제출하라.
            - reasoning : answer_analysis(rubric 항목별 평가 근거)와 question_planning(follow_up_question_algorithm 단계별 사고 과정)을 한 단락으로 작성. 사용자에게 노출되지 않으므로 자유 형식이지만 두 항목을 모두 포함.
            - rank : 위 평가 기준과 랭크 매핑에 따라 산출된 A/B/C/D/F 중 한 글자.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. answer_rank에 맞는 톤. 존댓말, 점수/랭크 미언급, 개행 없이 한 단락.
            - next_question : 위 단일 질문 제약을 모두 충족하는 꼬리 질문 1문장.
            </output>
            """.formatted(
            InterviewPromptFragments.PERSONA,
            InterviewPromptFragments.SECURITY_RULES,
            InterviewPromptFragments.LENGTH_NEUTRAL,
            InterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.RANK_MAPPING,
            InterviewPromptFragments.FEEDBACK_TONE_BY_RANK,
            InterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
            InterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT
    );

    public static final String END_SYSTEM_MESSAGE = """
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
            반드시 제공된 함수(generate_total_feedback)를 호출하여 다음 여섯 필드를 함께 제출하라.
            - reasoning : last_answer_analysis(가장 최근 답변에 대한 rubric 평가 근거)와 전체 면접의 강점/개선/학습 방향 정리를 한 단락으로 작성. 사용자에게 노출되지 않음.
            - rank : 가장 최근 답변에 대한 랭크. A, B, C, D, F 중 한 글자. 전체 답변 누적이 아닌 가장 최근 답변만을 기준으로 평가한다.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. answer_rank에 맞는 톤. 존댓말, 점수/랭크 미언급, 개행 없이 한 단락.
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

    public static final String CODING_PROCEED_SYSTEM_MESSAGE = """
            <role>
            %s
            </role>

            <task>
            아래는 라이브 코딩 테스트의 대화 흐름이다. assistant의 첫 메시지는 코딩 문제, 이후 assistant 메시지는 면접관의 꼬리 질문이며, user 메시지는 면접자가 제출한 코드 또는 그 설명이다.
            가장 최근 답변(가장 마지막 user 메시지)만 평가하고, 그 코드에 대한 피드백과 다음 꼬리 질문을 생성하라.
            </task>

            %s
            %s
            %s
            %s
            %s
            %s
            %s

            <output>
            반드시 제공된 함수(generate_feedback)를 호출하여 다음 네 필드를 함께 제출하라.
            - reasoning : answer_analysis(rubric 항목별 코드 평가 근거)와 question_planning(follow_up_question_algorithm 단계별 사고 과정)을 한 단락으로 작성. 사용자에게 노출되지 않으므로 자유 형식이지만 두 항목을 모두 포함.
            - rank : 위 평가 기준과 랭크 매핑에 따라 산출된 A/B/C/D/F 중 한 글자.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. answer_rank에 맞는 톤. 자연스러운 산문으로 작성하되, 코드를 포함할 경우 반드시 마크다운 코드 블록(```)으로 감싸서 작성한다. 코드 외 설명은 한 단락으로 이어서 작성하고, 존댓말을 사용하며 점수/랭크는 언급하지 않는다.
            - next_question : 위 단일 질문 제약을 모두 충족하는 꼬리 질문 1문장.
            </output>
            """.formatted(
            CodingInterviewPromptFragments.PERSONA,
            CodingInterviewPromptFragments.SECURITY_RULES,
            CodingInterviewPromptFragments.LENGTH_NEUTRAL,
            CodingInterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.RANK_MAPPING,
            InterviewPromptFragments.FEEDBACK_TONE_BY_RANK,
            CodingInterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
            CodingInterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT
    );

    public static final String CODING_END_SYSTEM_MESSAGE = """
            <role>
            %s
            </role>

            <task>
            아래는 라이브 코딩 테스트의 전체 대화 흐름이다. assistant의 첫 메시지는 코딩 문제, 이후 assistant 메시지는 면접관의 꼬리 질문이며, user 메시지는 면접자가 제출한 코드 또는 그 설명이다.
            가장 최근 답변에 대한 rank와 feedback, 그리고 면접 전체에 대한 종합 평가(strengths, improvements, learning_direction)를 작성하라.
            </task>

            %s
            %s
            %s
            %s
            %s

            <output>
            반드시 제공된 함수(generate_total_feedback)를 호출하여 다음 여섯 필드를 함께 제출하라.
            - reasoning : last_answer_analysis(가장 최근 답변에 대한 rubric 평가 근거)와 전체 코딩 테스트의 강점/개선/학습 방향 정리를 한 단락으로 작성. 사용자에게 노출되지 않음.
            - rank : 가장 최근 답변에 대한 랭크. A, B, C, D, F 중 한 글자. 전체 답변 누적이 아닌 가장 최근 답변만을 기준으로 평가한다.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. answer_rank에 맞는 톤. 자연스러운 산문으로 작성하되, 코드를 포함할 경우 반드시 마크다운 코드 블록(```)으로 감싸서 작성한다. 코드 외 설명은 한 단락으로 이어서 작성하고, 존댓말을 사용하며 점수/랭크는 언급하지 않는다.
            - strengths : 면접자의 강점 1-2문장. 존댓말, 점수/랭크 미언급.
            - improvements : 보완·개선 영역 1-2문장. 존댓말, 점수/랭크 미언급.
            - learning_direction : 향후 학습 방향 1-2문장. 존댓말, 점수/랭크 미언급.
            strengths/improvements/learning_direction 세 필드는 서버에서 한 단락으로 합성되므로 각 항목은 독립적인 한두 문장으로 자연스럽게 이어지게 작성한다. 인사·점수·랭크 언급 금지.
            </output>
            """.formatted(
            CodingInterviewPromptFragments.PERSONA,
            CodingInterviewPromptFragments.SECURITY_RULES,
            CodingInterviewPromptFragments.LENGTH_NEUTRAL,
            CodingInterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.RANK_MAPPING,
            InterviewPromptFragments.FEEDBACK_TONE_BY_RANK
    );

    public static final String PERSONALITY_PROCEED_SYSTEM_MESSAGE = """
            <role>
            %s
            </role>

            <task>
            아래는 인성 면접의 대화 흐름이다. assistant 메시지는 면접관의 인성 질문, user 메시지는 면접자가 본인의 경험·태도·생각을 서술한 답변이다.
            가장 최근 질문에 대한 답변(가장 마지막 user 메시지)만 평가하고, 그 답변에 대한 피드백과 다음 꼬리 질문을 생성하라.
            </task>

            %s
            %s
            %s
            %s
            %s
            %s
            %s

            <output>
            반드시 제공된 함수(generate_feedback)를 호출하여 다음 네 필드를 함께 제출하라.
            - reasoning : answer_analysis(rubric 항목별 평가 근거)와 question_planning(follow_up_question_algorithm 단계별 사고 과정)을 한 단락으로 작성. 사용자에게 노출되지 않으므로 자유 형식이지만 두 항목을 모두 포함.
            - rank : 위 평가 기준과 랭크 매핑에 따라 산출된 A/B/C/D/F 중 한 글자.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. answer_rank에 맞는 톤. 존댓말, 점수/랭크 미언급, 개행 없이 한 단락.
            - next_question : 위 단일 질문 제약을 모두 충족하는 꼬리 질문 1문장.
            </output>
            """.formatted(
            PersonalityInterviewPromptFragments.PERSONA,
            PersonalityInterviewPromptFragments.SECURITY_RULES,
            PersonalityInterviewPromptFragments.LENGTH_NEUTRAL,
            PersonalityInterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.RANK_MAPPING,
            InterviewPromptFragments.FEEDBACK_TONE_BY_RANK,
            PersonalityInterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
            PersonalityInterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT
    );

    public static final String PERSONALITY_END_SYSTEM_MESSAGE = """
            <role>
            %s
            </role>

            <task>
            아래는 인성 면접의 전체 대화 흐름이다. assistant 메시지는 면접관의 인성 질문, user 메시지는 면접자가 본인의 경험·태도·생각을 서술한 답변이다.
            가장 최근 답변에 대한 rank와 feedback, 그리고 면접 전체에 대한 종합 평가(strengths, improvements, learning_direction)를 작성하라.
            </task>

            %s
            %s
            %s
            %s
            %s

            <output>
            반드시 제공된 함수(generate_total_feedback)를 호출하여 다음 여섯 필드를 함께 제출하라.
            - reasoning : last_answer_analysis(가장 최근 답변에 대한 rubric 평가 근거)와 전체 인성 면접의 강점/개선/학습 방향 정리를 한 단락으로 작성. 사용자에게 노출되지 않음.
            - rank : 가장 최근 답변에 대한 랭크. A, B, C, D, F 중 한 글자. 전체 답변 누적이 아닌 가장 최근 답변만을 기준으로 평가한다.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. answer_rank에 맞는 톤. 존댓말, 점수/랭크 미언급, 개행 없이 한 단락.
            - strengths : 면접자의 강점 1-2문장. 존댓말, 점수/랭크 미언급.
            - improvements : 보완·개선 영역 1-2문장. 존댓말, 점수/랭크 미언급.
            - learning_direction : 향후 보완 방향 1-2문장. 존댓말, 점수/랭크 미언급.
            strengths/improvements/learning_direction 세 필드는 서버에서 한 단락으로 합성되므로 각 항목은 독립적인 한두 문장으로 자연스럽게 이어지게 작성한다. 인사·점수·랭크 언급 금지.
            </output>
            """.formatted(
            PersonalityInterviewPromptFragments.PERSONA,
            PersonalityInterviewPromptFragments.SECURITY_RULES,
            PersonalityInterviewPromptFragments.LENGTH_NEUTRAL,
            PersonalityInterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.RANK_MAPPING,
            InterviewPromptFragments.FEEDBACK_TONE_BY_RANK
    );

    private GptSystemMessageConstant() {
    }
}
