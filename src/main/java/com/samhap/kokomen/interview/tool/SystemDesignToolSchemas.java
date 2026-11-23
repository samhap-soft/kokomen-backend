package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 시스템 설계 면접 LLM 도구 스키마와 판정 필드 이름의 한곳. system 프롬프트의 목록 줄, 도구 스키마, 응답 파서가 모두 이 이름을 쓴다.
 * 판정 필드는 문제의 행마다 하나씩이라 호출마다 만든다. 기존 렌더러가 평평한 string·string enum 필드만 그리기 때문이다.
 */
public final class SystemDesignToolSchemas {

    public static final String REQUIREMENT_ANSWER_TOOL_NAME = "submit_requirement_answer";
    public static final String REASONING_FIELD = "reasoning";
    public static final String ANSWER_FIELD = "answer";
    public static final String REQUIREMENT_ASKED = "YES";
    public static final String REQUIREMENT_NOT_ASKED = "NO";

    private static final String REQUIREMENT_FIELD_PREFIX = "requirement_";
    private static final List<String> REQUIREMENT_VERDICTS = List.of(REQUIREMENT_ASKED, REQUIREMENT_NOT_ASKED);

    private SystemDesignToolSchemas() {
    }

    /**
     * code는 영문 대문자·숫자·밑줄이다. 기본 로캘과 상관없이 같은 이름이 나오도록 Locale.ROOT로 바꾼다.
     */
    public static String requirementFieldName(String code) {
        return REQUIREMENT_FIELD_PREFIX + code.toLowerCase(Locale.ROOT);
    }

    /**
     * 판정을 먼저 내고 그 판정에 맞춰 답을 쓰게 하려고 reasoning → 요구사항마다 판정(문제 순서) → answer 순서로 둔다. 모두 required다.
     */
    public static ToolSchema requirementAnswer(List<Requirement> requirements) {
        List<ToolField> fields = new ArrayList<>();
        fields.add(ToolField.required(REASONING_FIELD,
                "역질문이 묻는 내용과 항목별 판정 근거. 200자 이내. 사용자에게 노출되지 않는다."));
        for (Requirement requirement : requirements) {
            fields.add(ToolField.requiredEnum(requirementFieldName(requirement.code()),
                    "역질문이 '" + requirement.name() + "' 항목을 묻는지. 물으면 YES, 아니면 NO.", REQUIREMENT_VERDICTS));
        }
        fields.add(ToolField.required(ANSWER_FIELD,
                "면접관의 답. 해요체 1~3문장, 300자 이내. YES인 항목이 없으면 정해진 문구."));
        return new ToolSchema(REQUIREMENT_ANSWER_TOOL_NAME,
                "면접자의 요구사항 역질문이 숨은 요구사항의 각 항목을 묻는지 판정하고 면접관의 답을 제출한다.", fields);
    }
}
