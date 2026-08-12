package com.samhap.kokomen.global.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import jakarta.persistence.AttributeConverter;
import java.util.List;

/**
 * enum 목록을 JSON 배열 컬럼에 저장하기 위한 공통 컨버터다. JPA가 구체 타입을 요구하므로 enum마다 이 클래스를 상속한 {@code @Converter} 클래스를 하나씩 둔다.
 */
public abstract class EnumListJsonConverter<E extends Enum<E>> implements AttributeConverter<List<E>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CollectionType collectionType;

    protected EnumListJsonConverter(Class<E> enumType) {
        this.collectionType = MAPPER.getTypeFactory().constructCollectionType(List.class, enumType);
    }

    @Override
    public String convertToDatabaseColumn(List<E> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("List<%s> 직렬화 실패".formatted(collectionType.getContentType()), e);
        }
    }

    @Override
    public List<E> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, collectionType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("List<%s> 역직렬화 실패".formatted(collectionType.getContentType()), e);
        }
    }
}
