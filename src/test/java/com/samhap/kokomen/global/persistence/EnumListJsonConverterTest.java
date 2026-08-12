package com.samhap.kokomen.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.member.domain.survey.PrepStage;
import com.samhap.kokomen.member.domain.survey.PrepStageListJsonConverter;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnumListJsonConverterTest {

    private final PrepStageListJsonConverter converter = new PrepStageListJsonConverter();

    @Test
    void enum_목록을_JSON_배열_문자열로_직렬화한다() {
        assertThat(converter.convertToDatabaseColumn(List.of(PrepStage.JOB_SEEKING, PrepStage.GRADUATING)))
                .isEqualTo("[\"JOB_SEEKING\",\"GRADUATING\"]");
    }

    @Test
    void JSON_배열_문자열을_enum_목록으로_역직렬화한다() {
        assertThat(converter.convertToEntityAttribute("[\"JOB_SEEKING\",\"GRADUATING\"]"))
                .containsExactly(PrepStage.JOB_SEEKING, PrepStage.GRADUATING);
    }

    @Test
    void 직렬화와_역직렬화를_거쳐도_값이_유지된다() {
        List<PrepStage> prepStages = List.of(PrepStage.BEGINNER, PrepStage.SWITCHING);

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(prepStages)))
                .isEqualTo(prepStages);
    }

    @Test
    void null을_직렬화하면_null을_반환한다() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void 빈_목록을_직렬화하면_빈_JSON_배열을_반환한다() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
    }

    @Test
    void null을_역직렬화하면_빈_목록을_반환한다() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void 공백을_역직렬화하면_빈_목록을_반환한다() {
        assertThat(converter.convertToEntityAttribute("   ")).isEmpty();
    }

    @Test
    void 유효하지_않은_JSON을_역직렬화하면_예외가_발생한다() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 정의되지_않은_enum_이름을_역직렬화하면_예외가_발생한다() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("[\"UNKNOWN\"]"))
                .isInstanceOf(IllegalStateException.class);
    }
}
