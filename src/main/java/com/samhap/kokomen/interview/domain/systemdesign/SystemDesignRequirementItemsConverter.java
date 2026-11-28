package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/**
 * 확정한 요구사항 목록 ↔ MySQL JSON 컬럼(requirement_items). NULL은 이 값을 저장하기 전(V58 전)에 만든 행이라 빈 목록이 아니라
 * null로 읽는다. 항목은 {@link SystemDesignRequirementItem}의 구성 요소 이름(name, detail, source)으로 쓰고, 읽을 때 같은 규칙(글자 수,
 * 출처)을 다시 검사한다. 모르는 속성은 무시해, 뒤에 속성을 더한 행도 앞 버전으로 되돌린 배포가 읽을 수 있게 한다.
 */
@Converter
public class SystemDesignRequirementItemsConverter
        implements AttributeConverter<List<SystemDesignRequirementItem>, String> {

    private static final ObjectMapper MAPPER = Jackson2CompatibleJsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final TypeReference<List<SystemDesignRequirementItem>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<SystemDesignRequirementItem> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("확정한 요구사항 목록 직렬화 실패", e);
        }
    }

    @Override
    public List<SystemDesignRequirementItem> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        List<SystemDesignRequirementItem> items;
        try {
            items = MAPPER.readValue(dbData, TYPE);
        } catch (JacksonException e) {
            throw new IllegalStateException("확정한 요구사항 목록 역직렬화 실패", e);
        }
        // JSON null은 SQL NULL처럼 저장 전 행으로 본다. 원소 자리의 null은 항목이 아니다.
        if (items == null) {
            return null;
        }
        if (items.contains(null)) {
            throw new IllegalStateException("확정한 요구사항 목록 역직렬화 실패");
        }
        return List.copyOf(items);
    }
}
