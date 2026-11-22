package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
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
}
