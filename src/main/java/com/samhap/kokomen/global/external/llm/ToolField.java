package com.samhap.kokomen.global.external.llm;

import java.util.List;

/**
 * provider 중립적인 tool/function 스키마의 필드 서술자. 필드는 string(enumValues가 있으면 그 값 중 하나)이거나, 원소가 객체인
 * 배열이다. 배열이면 itemFields가 원소 객체의 속성이고 원소 안의 required도 itemFields의 required를 따른다. 타입은 렌더러가
 * 이 둘 중에서 정하고, 여기서는 이름/설명/enum/required 여부와 배열 원소의 속성만 표현한다.
 */
public record ToolField(String name, String description, List<String> enumValues, boolean required,
                        List<ToolField> itemFields) {

    public static ToolField required(String name, String description) {
        return new ToolField(name, description, null, true, null);
    }

    public static ToolField requiredEnum(String name, String description, List<String> enumValues) {
        return new ToolField(name, description, enumValues, true, null);
    }

    public static ToolField requiredObjectArray(String name, String description, List<ToolField> itemFields) {
        return new ToolField(name, description, null, true, List.copyOf(itemFields));
    }

    public boolean isObjectArray() {
        return itemFields != null;
    }
}
