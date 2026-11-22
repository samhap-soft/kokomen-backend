package com.samhap.kokomen.interview.domain.systemdesign;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.hibernate.annotations.Immutable;

/**
 * {@link SystemDesignDiagram} ↔ MySQL JSON 컬럼. 읽을 때도 {@link SystemDesignDiagram#fromJson(String)}으로 검사하므로
 * 요청과 시드의 시작 다이어그램이 같은 규칙을 지킨다.
 *
 * <p>{@link Immutable}이 없으면 Hibernate는 변환 타입을 가변으로 보고 더티 체킹 스냅샷을 만들 때마다 toJson·fromJson(검사 포함)으로
 * 깊은 복사를 한다. 다이어그램은 불변 값 객체이고 상태를 바꿀 때는 항상 새 객체로 교체하므로
 * ({@link SystemDesignInterview#recordAnswer}) 스냅샷은 같은 객체를 쓰고, 교체한 값은 equals로 비교해 UPDATE한다.
 */
@Immutable
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
