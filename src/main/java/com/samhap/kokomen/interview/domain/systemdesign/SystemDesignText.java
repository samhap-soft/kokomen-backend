package com.samhap.kokomen.interview.domain.systemdesign;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 역질문·답변 문자열을 다듬는 규칙. 프론트가 JS {@code trim()}으로 센 글자 수와 서버가 센 글자 수가 같아야 한다. 역질문 답과 확정
 * 항목에서 보는 '숫자+단위'도 여기서 정한다.
 */
public final class SystemDesignText {

    private static final char CHARACTER_TABULATION = 0x0009;
    private static final char CARRIAGE_RETURN = 0x000D;
    private static final char LINE_SEPARATOR = 0x2028;
    private static final char PARAGRAPH_SEPARATOR = 0x2029;
    private static final char ZERO_WIDTH_NO_BREAK_SPACE = 0xFEFF;
    private static final Pattern THOUSANDS_SEPARATOR = Pattern.compile("(?<=\\d),(?=\\d{3})");
    // 숫자(소수 포함) 바로 뒤의 단위. 한글이면 붙여 쓴 첫 글자, 영문이면 이어진 영문 전체, %다. 영문 단위와 %는 숫자와의 사이에 공백
    // 하나를 허용한다("100 ms"). 한글은 붙여 쓰는 단위라 공백을 허용하면 "5 개"처럼 숫자만 같은 우연이 늘어 붙여 쓴 것만 본다.
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile("(\\d+(?:\\.\\d+)?)(?:([가-힣])|\\s?(%|[A-Za-z]+))");

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
     * 최대 길이가 0 이하면 값과 상관없이 IllegalArgumentException이다.
     */
    public static String truncate(String value, int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("최대 길이는 1 이상이어야 합니다: %d".formatted(maxLength));
        }
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * 글에 나오는 '숫자+단위'(처음 나온 순서, 중복 없음). 역질문 답 검사(SystemDesignRequirementAnswerComposer)와 확정한 요구사항
     * 모으기(SystemDesignConfirmedRequirement)가 같은 정의를 쓴다. 전각 숫자·영문을 NFKC로 맞추고 천 단위 쉼표를 지운 뒤 모은다.
     * "1,000만 명"과 "1000만명"은 같은 1000만이고, "100배쯤"과 "100배예요"는 같은 100배다. 영문 단위는 소문자로 맞춘다("20MB"와
     * "20 mb"). 단위가 붙지 않은 숫자는 모으지 않고, null은 빈 집합이다.
     */
    public static Set<String> numberUnits(String text) {
        if (text == null) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        Matcher matcher = NUMBER_WITH_UNIT.matcher(THOUSANDS_SEPARATOR.matcher(normalized).replaceAll(""));
        Set<String> numberUnits = new LinkedHashSet<>();
        while (matcher.find()) {
            String unit = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            numberUnits.add(matcher.group(1) + unit.toLowerCase(Locale.ROOT));
        }
        return numberUnits;
    }
}
