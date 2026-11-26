package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.persistence.StringListJsonConverter;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage;
import com.samhap.kokomen.member.domain.survey.PrepStage;
import com.samhap.kokomen.member.domain.survey.PrepStageListJsonConverter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DB JSON 컬럼(문자열 목록, enum 목록, 시스템 설계 다이어그램)에 지금 저장하는 문자열과, 이미 저장된 여러 모양의 행을 읽은 결과를 고정한다.
 * 이미 고정한 것(enum 목록의 기본 직렬화, 다이어그램 toJson 모양·모르는 필드·깨진 JSON)은 각 테스트 클래스에 있고 여기서 다시 보지 않는다.
 */
class DbJsonColumnCharacterizationTest {

    // 원소 타입은 Jackson JavaType의 toString 표기 그대로 문구에 들어간다.
    private static final String PREP_STAGE_LIST_READ_FAILED =
            "List<[simple type, class com.samhap.kokomen.member.domain.survey.PrepStage]> 역직렬화 실패";

    private final StringListJsonConverter stringListConverter = new StringListJsonConverter();
    private final PrepStageListJsonConverter enumListConverter = new PrepStageListJsonConverter();

    @Test
    void 문자열_목록은_이스케이프할_문자만_이스케이프해서_저장한다() {
        List<String> values = Arrays.asList("꼬꼬면", "😀", "\"따옴표\"", "역슬래시 \\", "a/b", "</script>", "줄\n바꿈\t탭",
                "\u0001", "\u2028", "", null);

        assertThat(stringListConverter.convertToDatabaseColumn(values)).isEqualTo("""
                ["꼬꼬면","😀","\\"따옴표\\"","역슬래시 \\\\","a/b","</script>","줄\\n바꿈\\t탭","\\u0001","\u2028","",null]""");
        assertThat(stringListConverter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
        assertThat(stringListConverter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void 문자열_목록은_저장된_여러_모양의_행을_지금처럼_읽는다() {
        assertAll(
                () -> assertThat(stringListConverter.convertToEntityAttribute("[ \"a\" ,\n \"b\" ]"))
                        .containsExactly("a", "b"),
                () -> assertThat(stringListConverter.convertToEntityAttribute("[\"\\uAC00\\uD83D\\uDE00\",\"\\/\"]"))
                        .containsExactly("가😀", "/"),
                () -> assertThat(stringListConverter.convertToEntityAttribute("[\"a\",null]"))
                        .containsExactly("a", null),
                () -> assertThat(stringListConverter.convertToEntityAttribute("[1,true,2.50]"))
                        .containsExactly("1", "true", "2.50"),
                () -> assertThat(stringListConverter.convertToEntityAttribute("[\"a\"] 뒤에 붙은 글자"))
                        .containsExactly("a"),
                () -> assertThat(stringListConverter.convertToEntityAttribute("null")).isNull(),
                () -> assertThat(stringListConverter.convertToEntityAttribute(" ")).isEmpty()
        );
    }

    @Test
    void 문자열_목록을_읽지_못하면_IllegalStateException으로_감싼다() {
        assertAll(
                () -> assertThatThrownBy(() -> stringListConverter.convertToEntityAttribute("[\"a\""))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("List<String> 역직렬화 실패"),
                () -> assertThatThrownBy(() -> stringListConverter.convertToEntityAttribute("{\"a\":1}"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("List<String> 역직렬화 실패"),
                () -> assertThatThrownBy(() -> stringListConverter.convertToEntityAttribute("[[\"a\"]]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("List<String> 역직렬화 실패"),
                () -> assertThatThrownBy(() -> stringListConverter.convertToEntityAttribute("\"a\""))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("List<String> 역직렬화 실패"),
                () -> assertThatThrownBy(() -> stringListConverter.convertToEntityAttribute("[[\"a\",\"b\"]]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("List<String> 역직렬화 실패"),
                () -> assertThatThrownBy(() -> stringListConverter.convertToEntityAttribute("[\"a\",[\"b\"],\"c\"]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("List<String> 역직렬화 실패"),
                () -> assertThatThrownBy(() -> stringListConverter.convertToEntityAttribute("[[]]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("List<String> 역직렬화 실패")
        );
    }

    @Test
    void enum_목록은_저장된_여러_모양의_행을_지금처럼_읽는다() {
        assertAll(
                () -> assertThat(enumListConverter.convertToEntityAttribute("[ \"JOB_SEEKING\" ,\n\"GRADUATING\" ]"))
                        .containsExactly(PrepStage.JOB_SEEKING, PrepStage.GRADUATING),
                () -> assertThat(enumListConverter.convertToEntityAttribute("[\"BEGINNER\",null]"))
                        .containsExactly(PrepStage.BEGINNER, null),
                () -> assertThat(enumListConverter.convertToEntityAttribute("[0,3]"))
                        .containsExactly(PrepStage.BEGINNER, PrepStage.SWITCHING),
                () -> assertThat(enumListConverter.convertToEntityAttribute("[\"BEGINNER\"] 뒤에 붙은 글자"))
                        .containsExactly(PrepStage.BEGINNER)
        );
    }

    @Test
    void enum_목록을_읽지_못하면_원소_타입을_담은_문구로_감싼다() {
        assertAll(
                () -> assertThatThrownBy(() -> enumListConverter.convertToEntityAttribute("[\"beginner\"]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage(PREP_STAGE_LIST_READ_FAILED),
                () -> assertThatThrownBy(() -> enumListConverter.convertToEntityAttribute("[\"\"]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage(PREP_STAGE_LIST_READ_FAILED),
                () -> assertThatThrownBy(() -> enumListConverter.convertToEntityAttribute("[9]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage(PREP_STAGE_LIST_READ_FAILED),
                () -> assertThatThrownBy(() -> enumListConverter.convertToEntityAttribute("\"BEGINNER\""))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage(PREP_STAGE_LIST_READ_FAILED),
                () -> assertThatThrownBy(() -> enumListConverter.convertToEntityAttribute("[[\"BEGINNER\"]]"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage(PREP_STAGE_LIST_READ_FAILED)
        );
    }

    @Test
    void 다이어그램은_공백과_키_순서가_다른_행도_같은_값으로_읽고_정규화해서_다시_쓴다() {
        String stored = """
                {
                  "connections" : [ { "label" : "HTTPS", "target" : "api", "source" : "client", "id" : "c1" } ],
                  "blocks" : [
                    { "position" : { "y" : 80.5, "x" : 0 }, "props" : [ { "value" : "3", "key" : "개수" } ],
                      "label" : "모바일 앱 \\uD83D\\uDCF1", "kind" : "CLIENT", "id" : "client" },
                    { "position" : { "x" : 1E20, "y" : -1.25e-1 }, "props" : [ ], "label" : "API",
                      "kind" : "API_SERVER", "id" : "api" }
                  ]
                }""";

        assertThat(SystemDesignDiagram.fromJson(stored).toJson()).isEqualTo("""
                {"blocks":[{"id":"client","kind":"CLIENT","label":"모바일 앱 📱","props":[{"key":"개수","value":"3"}],\
                "position":{"x":0.0,"y":80.5}},{"id":"api","kind":"API_SERVER","label":"API","props":[],\
                "position":{"x":1.0E20,"y":-0.125}}],\
                "connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"}]}""");
    }

    @Test
    void 다이어그램은_좌표_문자열과_연결_label_null을_지금처럼_읽는다() {
        String stored = """
                {"blocks":[{"id":"a","kind":"DB","label":"A","props":[],"position":{"x":"12.5","y":"-3"}},\
                {"id":"b","kind":"CACHE","label":"B","props":[],"position":{"x":1,"y":2}}],\
                "connections":[{"id":"c1","source":"a","target":"b","label":null}]}""";

        assertThat(SystemDesignDiagram.fromJson(stored).toJson()).isEqualTo("""
                {"blocks":[{"id":"a","kind":"DB","label":"A","props":[],"position":{"x":12.5,"y":-3.0}},\
                {"id":"b","kind":"CACHE","label":"B","props":[],"position":{"x":1.0,"y":2.0}}],\
                "connections":[{"id":"c1","source":"a","target":"b"}]}""");
    }

    @Test
    void 다이어그램은_뒤따르는_내용과_좌표_null을_형식_오류나_좌표_오류로_거절한다() {
        String format = SystemDesignErrorMessage.DIAGRAM_FORMAT.getMessage();
        String position = SystemDesignErrorMessage.DIAGRAM_POSITION.getMessage();

        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("{\"blocks\":[],\"connections\":[]} x"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(format),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson(
                        "{\"blocks\":[],\"connections\":[]}{\"blocks\":[],\"connections\":[]}"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(format),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("""
                        {"blocks":[{"id":"a","kind":"DB","label":"A","props":[],"position":{"x":null,"y":0}}],\
                        "connections":[]}"""))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(position),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("""
                        {"blocks":[{"id":"a","kind":"DB","label":"A","props":[],"position":{"x":"abc","y":0}}],\
                        "connections":[]}"""))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(format)
        );
    }
}
