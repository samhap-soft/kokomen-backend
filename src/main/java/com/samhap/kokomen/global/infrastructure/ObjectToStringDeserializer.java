package com.samhap.kokomen.global.infrastructure;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;

public class ObjectToStringDeserializer extends ValueDeserializer<String> {

    // 트리를 문자열로 쓰기만 하는 매퍼라 하나를 같이 쓴다. Jackson 2 때 호출마다 만들던 new ObjectMapper()와 설정이 같다.
    private static final ObjectMapper MAPPER = Jackson2CompatibleJsonMapper.builder().build();

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);

        // 이미 문자열인 경우 그대로 반환
        if (node.isString()) {
            return node.asString();
        }

        // 객체나 배열인 경우 JSON 문자열로 변환
        return MAPPER.writeValueAsString(node);
    }
}
