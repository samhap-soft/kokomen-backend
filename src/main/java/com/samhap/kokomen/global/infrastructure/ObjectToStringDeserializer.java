package com.samhap.kokomen.global.infrastructure;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class ObjectToStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        // 이미 문자열인 경우 그대로 반환
        if (node.isTextual()) {
            return node.asText();
        }

        // 객체나 배열인 경우 JSON 문자열로 변환
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(node);
    }
}
