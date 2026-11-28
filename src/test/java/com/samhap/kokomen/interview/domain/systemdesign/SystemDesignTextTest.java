package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SystemDesignTextTest {

    /**
     * JS String.prototype.trim()이 지우는 문자 전부. WhiteSpace(TAB, VT, FF, U+FEFF, 유니코드 Zs)와
     * LineTerminator(LF, CR, U+2028, U+2029). Zs는 U+0020, U+00A0, U+1680, U+2000~U+200A, U+202F, U+205F, U+3000이다.
     */
    private static final String JS_TRIMMED_CHARACTERS = "\t\n\u000B\f\r\uFEFF\u2028\u2029"
            + " \u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009"
            + "\u200A\u202F\u205F\u3000";

    @Test
    void normalize는_null을_빈_문자열로_바꾼다() {
        assertThat(SystemDesignText.normalize(null)).isEmpty();
    }

    @Test
    void normalize는_앞뒤의_공백_전각_공백_NBSP_BOM을_지운다() {
        assertAll(
                () -> assertThat(SystemDesignText.normalize("\u3000  질문 \uFEFF")).isEqualTo("질문"),
                () -> assertThat(SystemDesignText.normalize("\u00A0질문\u00A0")).isEqualTo("질문"),
                () -> assertThat(SystemDesignText.normalize("\u3000 \u00A0\uFEFF")).isEmpty()
        );
    }

    @Test
    void normalize는_가운데_공백을_그대로_둔다() {
        assertThat(SystemDesignText.normalize("  시스템  설계\u3000면접\n질문  "))
                .isEqualTo("시스템  설계\u3000면접\n질문");
    }

    @Test
    void normalize는_JS_trim이_지우는_문자를_모두_지운다() {
        assertThat(SystemDesignText.normalize(JS_TRIMMED_CHARACTERS + "질문" + JS_TRIMMED_CHARACTERS))
                .isEqualTo("질문");
    }

    // U+200B(ZWSP)는 Cf, U+0085(NEL)·U+001C는 Cc라 JS trim()이 남긴다.
    // Java String.strip()은 U+001C를 지우고 NBSP를 남기므로 쓰지 않는다.
    @Test
    void normalize는_JS_trim이_지우지_않는_문자를_남긴다() {
        assertAll(
                () -> assertThat(SystemDesignText.normalize("\u200B질문\u200B")).isEqualTo("\u200B질문\u200B"),
                () -> assertThat(SystemDesignText.normalize("\u0085질문\u0085")).isEqualTo("\u0085질문\u0085"),
                () -> assertThat(SystemDesignText.normalize("\u001C질문\u001C")).isEqualTo("\u001C질문\u001C")
        );
    }

    @Test
    void truncate는_최대_길이를_넘는_뒷부분을_자른다() {
        assertThat(SystemDesignText.truncate("가나다라마", 3)).isEqualTo("가나다");
    }

    @Test
    void truncate는_최대_길이_이하이거나_null이면_그대로_둔다() {
        assertAll(
                () -> assertThat(SystemDesignText.truncate("가나다", 3)).isEqualTo("가나다"),
                () -> assertThat(SystemDesignText.truncate("가", 3)).isEqualTo("가"),
                () -> assertThat(SystemDesignText.truncate(null, 3)).isNull()
        );
    }

    @Test
    void truncate는_최대_길이가_0_이하면_값과_상관없이_거절한다() {
        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignText.truncate("가나다", 0))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("최대 길이는 1 이상이어야 합니다: 0"),
                () -> assertThatThrownBy(() -> SystemDesignText.truncate("가", -1))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("최대 길이는 1 이상이어야 합니다: -1"),
                () -> assertThatThrownBy(() -> SystemDesignText.truncate(null, 0))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("최대 길이는 1 이상이어야 합니다: 0")
        );
    }

    // 자른 자리가 서로게이트 쌍 가운데면 짝 잃은 상위 서로게이트가 남지 않게 한 글자 앞에서 자른다.
    @Test
    void truncate는_서로게이트_쌍을_가르지_않는다() {
        String emoji = "😀";

        assertAll(
                () -> assertThat(SystemDesignText.truncate("가나" + emoji, 3)).isEqualTo("가나"),
                () -> assertThat(SystemDesignText.truncate("가나" + emoji, 4)).isEqualTo("가나" + emoji),
                () -> assertThat(SystemDesignText.truncate("가" + emoji + "나", 3)).isEqualTo("가" + emoji)
        );
    }

    // 답과 확정 항목에서 보는 '숫자+단위'다. 역질문 답 검사(SystemDesignRequirementAnswerComposer)와 확정한 요구사항 모으기
    // (SystemDesignConfirmedRequirement)가 같은 정의를 쓴다.
    @Test
    void numberUnits는_숫자_바로_뒤의_단위와_함께_모은다() {
        assertAll(
                () -> assertThat(SystemDesignText.numberUnits("하루 평균 10만 개, 조회는 100배쯤"))
                        .containsExactly("10만", "100배"),
                () -> assertThat(SystemDesignText.numberUnits("가용성 99.9 % 이상, 원본은 20 MB까지"))
                        .containsExactly("99.9%", "20mb"),
                () -> assertThat(SystemDesignText.numberUnits("1,000만 명과 １０만 명")).containsExactly("1000만", "10만")
        );
    }

    // 한글 단위는 붙여 쓴 것만 본다("5 개"는 숫자만 같은 우연이 늘어 보지 않는다). 단위 없는 숫자와 null은 모으지 않는다.
    @Test
    void numberUnits는_띄어_쓴_한글_단위와_단위_없는_숫자와_null을_모으지_않는다() {
        assertAll(
                () -> assertThat(SystemDesignText.numberUnits("5 개만 받고 응답은 302")).isEmpty(),
                () -> assertThat(SystemDesignText.numberUnits(null)).isEmpty()
        );
    }
}
