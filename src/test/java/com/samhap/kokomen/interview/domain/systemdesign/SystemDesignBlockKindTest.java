package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.BadRequestException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SystemDesignBlockKindTest {

    @Test
    void 아홉_가지_종류를_읽는다() {
        assertAll(
                () -> assertThat(SystemDesignBlockKind.values()).extracting(SystemDesignBlockKind::name)
                        .containsExactly("CLIENT", "LOAD_BALANCER", "API_SERVER", "DB", "CACHE", "QUEUE", "STORAGE",
                                "CDN", "CUSTOM"),
                () -> assertThat(Arrays.stream(SystemDesignBlockKind.values())
                        .map(kind -> SystemDesignBlockKind.from(kind.name())))
                        .containsExactly(SystemDesignBlockKind.values())
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"client", "Client", "SERVER", "", " CLIENT", "CLIENT "})
    void 소문자나_모르는_값이나_null이면_거절한다(String value) {
        assertThatThrownBy(() -> SystemDesignBlockKind.from(value))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("블록 종류를 알 수 없어요.");
    }
}
