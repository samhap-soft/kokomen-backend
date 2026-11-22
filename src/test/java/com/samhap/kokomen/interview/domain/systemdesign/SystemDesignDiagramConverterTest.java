package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class SystemDesignDiagramConverterTest {

    private final SystemDesignDiagramConverter converter = new SystemDesignDiagramConverter();

    @Test
    void null은_null로_바꾼다() {
        assertAll(
                () -> assertThat(converter.convertToDatabaseColumn(null)).isNull(),
                () -> assertThat(converter.convertToEntityAttribute(null)).isNull()
        );
    }

    @Test
    void 다이어그램을_정규화한_JSON으로_쓰고_검사하며_읽는다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.fromJson("""
                {"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[],"position":{"x":0,"y":80}},\
                {"id":"api","kind":"API_SERVER","label":"API Server","props":[],"position":{"x":260,"y":80}}],\
                "connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"}]}""");

        String column = converter.convertToDatabaseColumn(diagram);

        assertAll(
                () -> assertThat(column).isEqualTo(diagram.toJson()),
                () -> assertThat(converter.convertToEntityAttribute(column)).isEqualTo(diagram),
                () -> assertThatThrownBy(() -> converter.convertToEntityAttribute("{\"blocks\":[]}"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("다이어그램 형식이 맞지 않아요.")
        );
    }
}
