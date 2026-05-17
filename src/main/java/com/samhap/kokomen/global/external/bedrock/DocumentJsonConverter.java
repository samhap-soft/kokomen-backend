package com.samhap.kokomen.global.external.bedrock;

import com.samhap.kokomen.global.exception.ExternalApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;

public final class DocumentJsonConverter {

    private DocumentJsonConverter() {
    }

    public static Object toJavaObject(Document document) {
        if (document == null || document.isNull()) {
            return null;
        }
        if (document.isBoolean()) {
            return document.asBoolean();
        }
        if (document.isString()) {
            return document.asString();
        }
        if (document.isNumber()) {
            return document.asNumber().bigDecimalValue();
        }
        if (document.isList()) {
            return document.asList().stream()
                    .map(DocumentJsonConverter::toJavaObject)
                    .toList();
        }
        if (document.isMap()) {
            Map<String, Object> map = new LinkedHashMap<>();
            document.asMap().forEach((key, value) -> map.put(key, toJavaObject(value)));
            return map;
        }
        throw new ExternalApiException("알 수 없는 Document 타입입니다.");
    }
}
