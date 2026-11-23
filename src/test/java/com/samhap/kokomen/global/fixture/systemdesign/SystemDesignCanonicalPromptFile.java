package com.samhap.kokomen.global.fixture.systemdesign;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 프롬프트 정본 파일(src/test/resources/prompts/)을 읽는다. 정본 파일은 모델이 받는 system 블록의 글자 그대로다.
 * system은 끝에 줄바꿈을 붙이지 않으므로, 편집기가 파일 끝에 붙이는 줄바꿈 하나와 CRLF 체크아웃은 비교 전에 걷어 낸다.
 */
public final class SystemDesignCanonicalPromptFile {

    private SystemDesignCanonicalPromptFile() {
    }

    public static String read(String path) throws IOException {
        try (InputStream input = SystemDesignCanonicalPromptFile.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("테스트 리소스가 없습니다: " + path);
            }
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
        }
    }
}
