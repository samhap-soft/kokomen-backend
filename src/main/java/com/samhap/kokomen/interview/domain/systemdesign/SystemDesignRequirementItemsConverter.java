package com.samhap.kokomen.interview.domain.systemdesign;

import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 확정한 요구사항 목록 ↔ MySQL JSON 컬럼(requirement_items). NULL(JSON null 포함)은 이 값을 저장하기 전(V58 전)에 만든 행이라 빈
 * 목록이 아니라 null로 읽는다. 항목은 {@link SystemDesignRequirementItem}의 구성 요소 이름(name, detail, source)으로 쓴다.
 *
 * <p>읽을 때 원소마다 같은 규칙(글자 수, 출처)을 다시 검사하고, 어긴 원소는 WARN을 남기고 건너뛴다. 모르는 속성은 무시한다. 뒤 버전이
 * 출처 값을 더하거나 규칙을 바꾼 행을 앞 버전으로 되돌린 배포가 읽어도, 행 하나 때문에 다음 역질문과 리포트가 실패하지 않게 하려는
 * 것이다. 배열로 읽을 수 없는 행은 IllegalStateException이다.
 */
@Slf4j
@Converter
public class SystemDesignRequirementItemsConverter
        implements AttributeConverter<List<SystemDesignRequirementItem>, String> {

    private static final ObjectMapper MAPPER = Jackson2CompatibleJsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

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
        JsonNode array = readTree(dbData);
        if (array.isNull()) {
            return null;
        }
        if (!array.isArray()) {
            throw new IllegalStateException("확정한 요구사항 목록 역직렬화 실패");
        }
        List<SystemDesignRequirementItem> items = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonNode element = array.get(index);
            try {
                if (!element.isObject()) {
                    throw new IllegalArgumentException("객체가 아닌 원소");
                }
                items.add(MAPPER.treeToValue(element, SystemDesignRequirementItem.class));
            } catch (JacksonException | IllegalArgumentException e) {
                log.warn("저장된 확정 항목 가운데 규칙을 어긴 원소를 건너뜁니다. index: {}, reason: {}", index, e.getMessage());
            }
        }
        return List.copyOf(items);
    }

    private static JsonNode readTree(String dbData) {
        try {
            return MAPPER.readTree(dbData);
        } catch (JacksonException e) {
            throw new IllegalStateException("확정한 요구사항 목록 역직렬화 실패", e);
        }
    }
}
