package com.samhap.kokomen.interview.external.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GPT function-calling 스키마의 프로퍼티. description/enum 은 nullable 이며 null이면 직렬화 시 생략된다
 * (GPT 클라이언트가 non_null inclusion ObjectMapper 를 사용). enum 은 SNAKE_CASE 를 우회하려고 명시 매핑한다.
 */
public record FunctionParamProperty(
        String type,
        String description,
        @JsonProperty("enum")
        List<String> enumValues
) {

    public static FunctionParamProperty string(String description) {
        return new FunctionParamProperty("string", description, null);
    }

    public static FunctionParamProperty stringEnum(String description, List<String> enumValues) {
        return new FunctionParamProperty("string", description, enumValues);
    }
}
