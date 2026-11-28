package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SystemDesignRequirementItemsConverterTest {

    private static final SystemDesignRequirementItem CUSTOM_ALIAS = new SystemDesignRequirementItem("커스텀 별칭",
            "커스텀 별칭은 이번 범위에서 뺀다.", SystemDesignRequirementSource.ASSUMED);
    private static final SystemDesignRequirementItem DAILY_CREATION_VOLUME = new SystemDesignRequirementItem("하루 생성량",
            "하루 평균 10만 개가 \"새로\" 만들어진다.", SystemDesignRequirementSource.SEED);

    private final SystemDesignRequirementItemsConverter converter = new SystemDesignRequirementItemsConverter();

    // 배포 전에 저장한 행은 NULL이다. 빈 목록과 구별해 읽는다.
    @Test
    void null은_null로_바꾼다() {
        assertAll(
                () -> assertThat(converter.convertToDatabaseColumn(null)).isNull(),
                () -> assertThat(converter.convertToEntityAttribute(null)).isNull(),
                () -> assertThat(converter.convertToEntityAttribute("null")).isNull()
        );
    }

    @Test
    void 확정_항목을_이름_내용_출처_순서의_JSON_배열로_쓴다() {
        assertAll(
                () -> assertThat(converter.convertToDatabaseColumn(List.of(CUSTOM_ALIAS, DAILY_CREATION_VOLUME)))
                        .isEqualTo("[{\"name\":\"커스텀 별칭\",\"detail\":\"커스텀 별칭은 이번 범위에서 뺀다.\",\"source\":\"ASSUMED\"},"
                                + "{\"name\":\"하루 생성량\",\"detail\":\"하루 평균 10만 개가 \\\"새로\\\" 만들어진다.\","
                                + "\"source\":\"SEED\"}]"),
                () -> assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]")
        );
    }

    // MySQL JSON 컬럼은 키 순서를 바꾸고 공백을 넣어 돌려준다. 나중에 더한 속성은 모르는 값이어도 읽는다(되돌린 배포에서도 행을 읽게).
    @Test
    void 저장한_JSON을_같은_항목으로_읽는다() {
        assertAll(
                () -> assertThat(converter.convertToEntityAttribute(
                        converter.convertToDatabaseColumn(List.of(CUSTOM_ALIAS, DAILY_CREATION_VOLUME))))
                        .containsExactly(CUSTOM_ALIAS, DAILY_CREATION_VOLUME),
                () -> assertThat(converter.convertToEntityAttribute("""
                        [{"name": "커스텀 별칭", "detail": "커스텀 별칭은 이번 범위에서 뺀다.", "source": "ASSUMED", \
                        "order": 1}]"""))
                        .containsExactly(CUSTOM_ALIAS),
                () -> assertThat(converter.convertToEntityAttribute("[]")).isEmpty()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"name\": \"커스텀 별칭\"",
            "{\"name\": \"커스텀 별칭\", \"detail\": \"뺀다.\", \"source\": \"ASSUMED\"}",
            "\"커스텀 별칭\""
    })
    void 배열로_읽을_수_없는_행은_IllegalStateException으로_감싼다(String dbData) {
        assertThatThrownBy(() -> converter.convertToEntityAttribute(dbData))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("확정한 요구사항 목록 역직렬화 실패");
    }

    // 뒤 버전이 출처 값을 더하거나 규칙을 바꾼 행을 앞 버전으로 되돌린 배포가 읽는 경우다. 행 하나 때문에 다음 역질문과 리포트가
    // 실패하지 않게, 규칙을 어긴 원소만 건너뛰고 나머지를 읽는다. DB의 값은 그대로 남는다.
    @Test
    void 규칙을_어긴_원소는_건너뛰고_나머지를_읽는다() {
        assertThat(converter.convertToEntityAttribute("""
                [{"name": "커스텀 별칭", "detail": "뺀다.", "source": "OTHER"},
                 {"name": "", "detail": "뺀다.", "source": "ASSUMED"},
                 {"name": "커스텀 별칭", "source": "ASSUMED"},
                 null, "커스텀 별칭",
                 {"name": "커스텀 별칭", "detail": "커스텀 별칭은 이번 범위에서 뺀다.", "source": "ASSUMED"}]"""))
                .containsExactly(CUSTOM_ALIAS);
    }
}
