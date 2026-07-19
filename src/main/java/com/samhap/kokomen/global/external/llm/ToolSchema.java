package com.samhap.kokomen.global.external.llm;

import java.util.List;

/**
 * provider 중립적인 tool/function 스키마의 단일 서술자.
 * 이 하나의 서술자를 GPT(function-calling)와 Bedrock(tool-use) 각 provider 렌더러가 자신의 형식으로 변환한다.
 * 덕분에 도구 필드 집합·설명·enum·required 를 한 곳에서만 정의한다.
 */
public record ToolSchema(String name, String description, List<ToolField> fields) {

    public List<ToolField> requiredFields() {
        return fields.stream()
                .filter(ToolField::required)
                .toList();
    }
}
