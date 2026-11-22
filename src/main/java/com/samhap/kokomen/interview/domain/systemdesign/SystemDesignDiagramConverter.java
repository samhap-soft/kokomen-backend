package com.samhap.kokomen.interview.domain.systemdesign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link SystemDesignDiagram} ↔ MySQL JSON 컬럼. 읽을 때도 {@link SystemDesignDiagram#fromJson(String)}으로 검사하므로
 * 요청과 시드의 시작 다이어그램이 같은 규칙을 지킨다.
 */
@Converter
public class SystemDesignDiagramConverter implements AttributeConverter<SystemDesignDiagram, String> {

    @Override
    public String convertToDatabaseColumn(SystemDesignDiagram attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toJson();
    }

    @Override
    public SystemDesignDiagram convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return SystemDesignDiagram.fromJson(dbData);
    }
}
