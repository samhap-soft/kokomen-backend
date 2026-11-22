package com.samhap.kokomen.interview.domain.systemdesign;

/**
 * 역질문·답변 문자열을 다듬는 규칙. 프론트가 JS {@code trim()}으로 센 글자 수와 서버가 센 글자 수가 같아야 한다.
 */
public final class SystemDesignText {

    private static final char CHARACTER_TABULATION = 0x0009;
    private static final char CARRIAGE_RETURN = 0x000D;
    private static final char LINE_SEPARATOR = 0x2028;
    private static final char PARAGRAPH_SEPARATOR = 0x2029;
    private static final char ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;

    private SystemDesignText() {
    }

    /**
     * null이면 빈 문자열을 돌려주고, 아니면 JS {@code String.prototype.trim()}과 같이 앞뒤의 공백 문자를 지운다.
     * 지우는 문자는 유니코드 Zs 전체, U+0009~U+000D, U+FEFF, U+2028, U+2029다. 가운데 문자는 그대로 둔다.
     * Java {@code String.strip()}은 NBSP(U+00A0)를 남기고 U+001C~U+001F를 지워 JS와 다르므로 쓰지 않는다.
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && isTrimmed(value.charAt(start))) {
            start++;
        }
        while (end > start && isTrimmed(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isTrimmed(char character) {
        return Character.getType(character) == Character.SPACE_SEPARATOR
                || (character >= CHARACTER_TABULATION && character <= CARRIAGE_RETURN)
                || character == LINE_SEPARATOR
                || character == PARAGRAPH_SEPARATOR
                || character == ZERO_WIDTH_NO_BREAK_SPACE;
    }

    /**
     * LLM 출력처럼 길이를 믿을 수 없는 값을 컬럼 한도({@code String.length()} 기준)에 맞춰 뒤를 자른다.
     * 자르는 자리가 서로게이트 쌍 가운데면 한 글자 앞에서 잘라 짝 잃은 서로게이트를 남기지 않는다. null은 그대로 둔다.
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }
}
