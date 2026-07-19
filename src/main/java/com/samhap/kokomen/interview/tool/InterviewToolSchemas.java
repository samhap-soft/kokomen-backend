package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 면접 tool/function 스키마의 단일 소스. GPT/Bedrock 렌더러가 이 서술자를 각 provider 형식으로 변환한다.
 * 진행 단계의 feedback 필드는 {@code feedbackInline} 로 토글한다(GPT=포함, Bedrock=미포함 — 2콜 구조).
 */
public final class InterviewToolSchemas {

    public static final String PROCEED_TOOL_NAME = "submit_interview_proceed";
    public static final String END_TOOL_NAME = "submit_interview_end";
    public static final String ANSWER_FEEDBACK_TOOL_NAME = "submit_answer_feedback";

    private static final List<String> RANK_ENUM = Arrays.stream(AnswerRank.values())
            .map(AnswerRank::name)
            .toList();

    private InterviewToolSchemas() {
    }

    public static ToolSchema proceed(boolean feedbackInline) {
        List<ToolField> fields = new ArrayList<>();
        fields.add(ToolField.required("reasoning",
                "답변 평가 근거(answer_analysis)와 다음 질문 설계 과정(question_planning)을 담은 사고 과정. 사용자에게 노출되지 않는다."));
        fields.add(ToolField.requiredEnum("rank",
                "가장 최근 답변에 대한 평가 등급. A, B, C, D, F 중 한 글자.", RANK_ENUM));
        if (feedbackInline) {
            fields.add(ToolField.required("feedback",
                    "가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank 규칙을 따른다."));
        }
        fields.add(ToolField.required("next_question",
                "single_question_constraint를 충족하는 다음 꼬리 질문 1문장."));
        return new ToolSchema(PROCEED_TOOL_NAME, "면접 답변에 대한 rank와 다음 꼬리 질문을 제출한다.", fields);
    }

    public static ToolSchema end() {
        List<ToolField> fields = List.of(
                ToolField.required("reasoning",
                        "마지막 답변 평가 근거와 전체 종합 평가 정리를 담은 사고 과정. 사용자에게 노출되지 않는다."),
                ToolField.requiredEnum("rank",
                        "가장 최근 답변에 대한 평가 등급. A, B, C, D, F 중 한 글자.", RANK_ENUM),
                ToolField.required("feedback",
                        "가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank 규칙을 따른다."),
                ToolField.required("strengths", "면접자의 강점 1-2문장."),
                ToolField.required("improvements", "보완·개선 영역 1-2문장."),
                ToolField.required("learning_direction",
                        "이번 면접에서 드러난 약점에 근거한 구체적 하위 주제·다음 학습 스텝 1-2문장. 일반론 금지."));
        return new ToolSchema(END_TOOL_NAME, "면접 종료 시점의 rank·마지막 답변 피드백·전체 종합 평가를 제출한다.", fields);
    }

    public static ToolSchema answerFeedback() {
        List<ToolField> fields = List.of(
                ToolField.required("feedback",
                        "가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank 규칙을 따른다."));
        return new ToolSchema(ANSWER_FEEDBACK_TOOL_NAME, "가장 최근 답변에 대한 피드백을 제출한다.", fields);
    }
}
