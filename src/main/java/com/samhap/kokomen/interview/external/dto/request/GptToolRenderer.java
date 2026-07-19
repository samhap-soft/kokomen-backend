package com.samhap.kokomen.interview.external.dto.request;

import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * provider 중립 {@link ToolSchema}를 GPT function-calling 형식({@link Tool} + {@link ToolChoice})으로 변환한다.
 * enum/description 을 그대로 실어 보내므로 기존 {@code type}만 있던 스키마보다 GPT 응답 제약이 강해진다.
 */
public final class GptToolRenderer {

    private GptToolRenderer() {
    }

    public static List<Tool> renderTools(ToolSchema schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (ToolField field : schema.fields()) {
            if (field.enumValues() != null) {
                properties.put(field.name(), FunctionParamProperty.stringEnum(field.description(), field.enumValues()));
            } else {
                properties.put(field.name(), FunctionParamProperty.string(field.description()));
            }
        }
        GptFunctionParameters parameters = new GptFunctionParameters(
                "object",
                properties,
                schema.requiredFields().stream().map(ToolField::name).toList());
        return List.of(new Tool("function", new GptFunction(schema.name(), parameters)));
    }

    public static ToolChoice renderToolChoice(ToolSchema schema) {
        return new ToolChoice("function", new ToolChoiceFunction(schema.name()));
    }
}
