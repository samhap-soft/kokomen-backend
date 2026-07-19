package com.samhap.kokomen.global.external.llm;

import java.util.List;

/**
 * provider 중립적인 tool/function 스키마의 필드 서술자. 현재 면접·이력서 도구의 모든 필드는 string 타입이므로
 * 타입은 렌더러가 string으로 고정하고, 여기서는 이름/설명/enum/required 여부만 표현한다.
 */
public record ToolField(String name, String description, List<String> enumValues, boolean required) {

    public static ToolField required(String name, String description) {
        return new ToolField(name, description, null, true);
    }

    public static ToolField requiredEnum(String name, String description, List<String> enumValues) {
        return new ToolField(name, description, enumValues, true);
    }
}
