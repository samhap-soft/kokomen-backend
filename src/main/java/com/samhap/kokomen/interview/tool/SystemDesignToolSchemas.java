package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementAnswerType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementSource;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 시스템 설계 면접 LLM 도구 스키마와 필드 이름의 한곳. system 프롬프트의 목록 줄, 도구 스키마, 응답 파서가 모두 이 이름을 쓴다.
 * 판정 필드(역질문 답의 requirement_*, 마지막 답변 평가의 point_*와 그 증거 필드 point_*_evidence)는 문제의 행마다 하나씩이라
 * 호출마다 만든다. 판정 필드는 평평한 string enum 필드로 두고, 역질문 답의 확정 항목(requirement_items)만 객체 배열이다.
 */
public final class SystemDesignToolSchemas {

    public static final String REQUIREMENT_ANSWER_TOOL_NAME = "submit_requirement_answer";
    public static final String ANSWER_TOOL_NAME = "submit_system_design_answer";
    public static final String END_TOOL_NAME = "submit_system_design_end";
    public static final String REASONING_FIELD = "reasoning";
    public static final String ANSWER_FIELD = "answer";
    public static final String RANK_FIELD = "rank";
    public static final String FEEDBACK_FIELD = "feedback";
    public static final String NEXT_QUESTION_FIELD = "next_question";
    public static final String STRENGTHS_FIELD = "strengths";
    public static final String IMPROVEMENTS_FIELD = "improvements";
    public static final String LEARNING_DIRECTION_FIELD = "learning_direction";
    public static final String ANSWER_TYPE_FIELD = "answer_type";
    public static final String REQUIREMENT_ITEMS_FIELD = "requirement_items";
    public static final String ITEM_NAME_FIELD = "name";
    public static final String ITEM_DETAIL_FIELD = "detail";
    public static final String ITEM_SOURCE_FIELD = "source";
    public static final String REQUIREMENT_ASKED = "YES";
    public static final String REQUIREMENT_NOT_ASKED = "NO";
    public static final String POINT_MET = "MET";
    public static final String POINT_NOT_MET = "NOT_MET";

    private static final String REQUIREMENT_FIELD_PREFIX = "requirement_";
    private static final String POINT_FIELD_PREFIX = "point_";
    private static final String POINT_EVIDENCE_FIELD_SUFFIX = "_evidence";
    private static final List<String> REQUIREMENT_VERDICTS = List.of(REQUIREMENT_ASKED, REQUIREMENT_NOT_ASKED);
    private static final List<String> POINT_VERDICTS = List.of(POINT_MET, POINT_NOT_MET);
    private static final List<String> ANSWER_TYPES = Arrays.stream(SystemDesignRequirementAnswerType.values())
            .map(SystemDesignRequirementAnswerType::name)
            .toList();
    private static final List<String> REQUIREMENT_SOURCES = Arrays.stream(SystemDesignRequirementSource.values())
            .map(SystemDesignRequirementSource::name)
            .toList();
    private static final List<String> RANKS = Arrays.stream(AnswerRank.values())
            .map(AnswerRank::name)
            .toList();
    private static final String RANK_DESCRIPTION = "가장 최근 답변에 대한 평가 등급. " + String.join(", ", RANKS) + " 중 한 글자.";
    private static final String FEEDBACK_DESCRIPTION = "가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank 규칙을 따른다.";
    private static final String POINT_EVIDENCE_DESCRIPTION = "이 평가 포인트 설명의 요소마다 한 줄씩: "
            + "`[답변 번호] \"답변이나 다이어그램 줄의 원문 그대로 짧은 인용\"`. 확인되지 않은 요소는 `없음: 요소 이름`. 300자 이내.";

    private SystemDesignToolSchemas() {
    }

    /**
     * code는 영문 대문자·숫자·밑줄이다. 기본 로캘과 상관없이 같은 이름이 나오도록 Locale.ROOT로 바꾼다.
     */
    public static String requirementFieldName(String code) {
        return REQUIREMENT_FIELD_PREFIX + code.toLowerCase(Locale.ROOT);
    }

    /**
     * 평가 포인트 판정 필드 이름. 요구사항 판정 필드와 같은 규칙(Locale.ROOT 소문자)이다.
     */
    public static String pointFieldName(String code) {
        return POINT_FIELD_PREFIX + code.toLowerCase(Locale.ROOT);
    }

    /**
     * 평가 포인트 증거 필드 이름. 판정 필드 이름 뒤에 _evidence를 붙인다. code가 _EVIDENCE로 끝나면 다른 포인트의 판정 필드와 이름이
     * 같아지므로 시드에 그런 code를 두지 않는다. V57 시드는 SystemDesignSeedScriptTest가 확인하고, 새 시드도 같은 규칙을 지킨다.
     */
    public static String pointEvidenceFieldName(String code) {
        return pointFieldName(code) + POINT_EVIDENCE_FIELD_SUFFIX;
    }

    /**
     * 판정을 먼저 내고 그 판정에 맞춰 답의 종류와 확정 항목을 정한 뒤 답을 쓰게 하려고 reasoning → 요구사항마다 판정(문제 순서) →
     * answer_type → requirement_items → answer 순서로 둔다. 모두 required다. 확정 항목의 개수·글자 수 제한은 설명에만 적고 서버가
     * 다시 검사한다(SystemDesignRequirementAnswerComposer).
     */
    public static ToolSchema requirementAnswer(List<Requirement> requirements) {
        List<ToolField> fields = new ArrayList<>();
        fields.add(ToolField.required(REASONING_FIELD, "역질문이 묻는 내용, 항목별 판정 근거, 숨은 요구사항에 없는 내용이면 "
                + "정한 값과 그 근거. 200자 이내. 사용자에게 노출되지 않는다."));
        for (Requirement requirement : requirements) {
            fields.add(ToolField.requiredEnum(requirementFieldName(requirement.code()),
                    "역질문이 '" + requirement.name() + "' 항목을 묻는지. 물으면 YES, 아니면 NO.", REQUIREMENT_VERDICTS));
        }
        fields.add(ToolField.requiredEnum(ANSWER_TYPE_FIELD, "답의 종류. SEED(YES인 항목의 정해 둔 사실만으로 답함), "
                + "ASSUMED(숨은 요구사항에 없는 요구사항을 정해 답함), MIXED(둘 다), DECLINED(요구사항을 묻는 질문이 아님) 중 하나.",
                ANSWER_TYPES));
        fields.add(ToolField.requiredObjectArray(REQUIREMENT_ITEMS_FIELD,
                "이번 답으로 새로 확정한 요구사항. 0~3개. 이미 확정한 내용을 다시 말했거나 DECLINED면 빈 배열.", List.of(
                        ToolField.required(ITEM_NAME_FIELD, "요구사항의 짧은 이름. 30자 이하."),
                        ToolField.required(ITEM_DETAIL_FIELD, "확정한 내용 한 문장. 150자 이하."),
                        ToolField.requiredEnum(ITEM_SOURCE_FIELD, "정해 둔 사실이면 SEED, 면접관이 정했으면 ASSUMED.",
                                REQUIREMENT_SOURCES))));
        fields.add(ToolField.required(ANSWER_FIELD, "면접관의 답. 해요체 1~3문장, 300자 이내. DECLINED면 빈 문자열."));
        return new ToolSchema(REQUIREMENT_ANSWER_TOOL_NAME, "면접자의 요구사항 역질문이 숨은 요구사항의 각 항목을 묻는지 판정하고, "
                + "답의 종류와 이번 답으로 확정한 요구사항, 면접관의 답을 제출한다.", fields);
    }

    /**
     * 답변 평가(L2). rank를 먼저 확정한 뒤 다음 질문을 설계하게 reasoning → rank → feedback → next_question 순서로 둔다. 모두 required다.
     * reasoning 글자 수 한도가 출력 길이를 묶어 30초 폴링 안에 끝나게 한다.
     */
    public static ToolSchema answer() {
        return new ToolSchema(ANSWER_TOOL_NAME, "시스템 설계 면접 답변의 rank와 피드백, 다음 질문을 제출한다.", List.of(
                ToolField.required(REASONING_FIELD,
                        "답변 평가 근거(answer_analysis)와 다음 질문 설계 과정(question_planning). 700자 이내. 사용자에게 노출되지 않는다."),
                ToolField.requiredEnum(RANK_FIELD, RANK_DESCRIPTION, RANKS),
                ToolField.required(FEEDBACK_FIELD, FEEDBACK_DESCRIPTION),
                ToolField.required(NEXT_QUESTION_FIELD,
                        "next_question_rules를 지켜 기준 질문을 바탕으로 만든 다음 질문. 한두 문장, 200자 이내.")));
    }

    /**
     * 마지막 답변 평가와 리포트(L3). rank → feedback 뒤에 평가 포인트마다 증거 필드와 MET/NOT_MET 판정 필드를 문제 순서로 붙이고,
     * 그 뒤에 종합 평가(strengths → improvements → learning_direction)를 둔다. 근거를 먼저 쓰고 판정하게 증거 필드를 판정 필드 바로
     * 앞에 두고, 판정에 맞춰 종합 평가를 쓰게 평가 포인트를 종합 평가 앞에 둔다. 판정 필드의 설명은 평가 포인트 문장이다. 모두
     * required다. 출력을 줄이려고 reasoning은 두지 않는다. 평가 포인트별 근거는 증거 필드에 쓰고, 마지막 답변의 항목 점수는 rank를
     * 정하기 전에 속으로 매겨 근거를 증거 필드와 feedback에 반영하게 한다(system 프롬프트의 채점 근거).
     */
    public static ToolSchema end(List<EvaluationPoint> points) {
        List<ToolField> fields = new ArrayList<>(List.of(
                ToolField.requiredEnum(RANK_FIELD, RANK_DESCRIPTION, RANKS),
                ToolField.required(FEEDBACK_FIELD, FEEDBACK_DESCRIPTION)));
        for (EvaluationPoint point : points) {
            fields.add(ToolField.required(pointEvidenceFieldName(point.code()), POINT_EVIDENCE_DESCRIPTION));
            fields.add(ToolField.requiredEnum(pointFieldName(point.code()), point.description(), POINT_VERDICTS));
        }
        fields.add(ToolField.required(STRENGTHS_FIELD, "이번 면접에서 드러난 설계 강점 1-2문장."));
        fields.add(ToolField.required(IMPROVEMENTS_FIELD, "보완할 설계 영역 1-2문장. 확인하지 않은 요구사항이 설계에 영향을 줬다면 짚는다."));
        fields.add(ToolField.required(LEARNING_DIRECTION_FIELD,
                "이번 면접의 약점에 근거한 구체적인 다음 학습 주제 1-2문장. 일반론은 쓰지 않는다."));
        return new ToolSchema(END_TOOL_NAME,
                "시스템 설계 면접 마지막 답변의 rank와 피드백, 평가 포인트별 충족 여부, 종합 평가를 제출한다.", fields);
    }
}
