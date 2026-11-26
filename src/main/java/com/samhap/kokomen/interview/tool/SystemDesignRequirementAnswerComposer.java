package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignText;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 역질문 답(L1) 출력에서 물은 요구사항과 최종 답을 정한다. 도구 스키마의 required·enum은 모델에 대한 지시일 뿐이라 서버가 다시 검사한다.
 */
@Slf4j
public final class SystemDesignRequirementAnswerComposer {

    public static final String DEFAULT_ANSWER = "등록되지 않은 내용이에요. 가정하고 진행하세요.";
    public static final int MAX_ANSWER_LENGTH = 300;

    private static final Pattern THOUSANDS_SEPARATOR = Pattern.compile("(?<=\\d),(?=\\d{3})");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final String SEED_ANSWER_DELIMITER = " ";

    private SystemDesignRequirementAnswerComposer() {
    }

    /**
     * 요구사항마다 판정 필드가 문자열 YES/NO(대소문자 구분)인지 본다. 하나라도 없거나 다른 값이면 응답 전체를 실패로 본다.
     * 빠진 판정을 "묻지 않음"으로 조용히 받으면 면접자가 역질문 하나를 잃기 때문이다. 모르는 필드는 무시한다.
     *
     * @return YES로 판정한 요구사항의 code(문제 순서)
     * @throws ExternalApiException 판정 필드가 없거나 값이 YES/NO가 아니면
     */
    public static List<String> resolveAskedCodes(List<Requirement> requirements, JsonNode toolInput) {
        List<String> askedCodes = new ArrayList<>();
        for (Requirement requirement : requirements) {
            String fieldName = SystemDesignToolSchemas.requirementFieldName(requirement.code());
            String verdict = readVerdict(toolInput, fieldName);
            if (SystemDesignToolSchemas.REQUIREMENT_ASKED.equals(verdict)) {
                askedCodes.add(requirement.code());
            }
        }
        return List.copyOf(askedCodes);
    }

    private static String readVerdict(JsonNode toolInput, String fieldName) {
        JsonNode value = toolInput == null ? null : toolInput.get(fieldName);
        if (value == null || !value.isString() || !isVerdict(value.stringValue())) {
            throw new ExternalApiException("시스템 설계 역질문 판정 필드가 없거나 값이 잘못됐습니다. field=" + fieldName);
        }
        return value.stringValue();
    }

    private static boolean isVerdict(String value) {
        return SystemDesignToolSchemas.REQUIREMENT_ASKED.equals(value)
                || SystemDesignToolSchemas.REQUIREMENT_NOT_ASKED.equals(value);
    }

    /**
     * 물은 요구사항이 없으면 LLM 답과 상관없이 정해진 문구다. 물은 요구사항이 있으면 LLM 답을 쓰되, 답이 비었거나
     * {@value #MAX_ANSWER_LENGTH}자를 넘거나 정해진 문구를 담았거나 물은 요구사항의 시드 답에 없는 숫자가 있으면 그 요구사항들의
     * 시드 답을 문제 순서로 공백 하나를 두고 이어 쓴다(폴백). 숫자 검증은 지어낸 수치, 묻지 않은 요구사항의 수치, 면접자가 직접 할
     * 계산을 막는다. "백만" 같은 한글 수사와 전각 숫자처럼 ASCII가 아닌 숫자는 잡지 못해 프롬프트 규칙이 함께 막는다. 허용하는 숫자는
     * 물은 요구사항의 시드 답에서만 나오므로, 이름에만 있는 숫자(예: "1인당")를 답에 쓰면 폴백된다.
     *
     * @param askedCodes {@link #resolveAskedCodes}가 돌려준 code
     * @param llmAnswer  엔티티를 되돌린 LLM 답
     */
    public static String compose(List<Requirement> requirements, List<String> askedCodes, String llmAnswer) {
        List<Requirement> askedRequirements = requirements.stream()
                .filter(requirement -> askedCodes.contains(requirement.code()))
                .toList();
        if (askedRequirements.isEmpty()) {
            return DEFAULT_ANSWER;
        }
        String seedAnswer = askedRequirements.stream()
                .map(Requirement::answer)
                .collect(Collectors.joining(SEED_ANSWER_DELIMITER));
        String answer = SystemDesignText.normalize(llmAnswer);
        String fallbackReason = findFallbackReason(answer, seedAnswer);
        if (fallbackReason != null) {
            log.warn("시스템 설계 역질문 답을 시드 답으로 바꿉니다. reason: {}, askedCodes: {}", fallbackReason, askedCodes);
            return seedAnswer;
        }
        return answer;
    }

    private static String findFallbackReason(String answer, String seedAnswer) {
        if (answer.isEmpty()) {
            return "빈 답";
        }
        if (answer.length() > MAX_ANSWER_LENGTH) {
            return "답 길이 " + answer.length() + "자";
        }
        // 판정은 YES인데 답은 "등록되지 않은 내용"이면 둘이 어긋난다. 물은 것으로 기록하는 요구사항을 면접자가 실제로 듣게 한다.
        if (answer.contains(DEFAULT_ANSWER)) {
            return "물은 요구사항이 있는데 정해진 문구";
        }
        Set<String> unknownNumbers = new LinkedHashSet<>(numbers(answer));
        unknownNumbers.removeAll(numbers(seedAnswer));
        if (!unknownNumbers.isEmpty()) {
            return "시드에 없는 숫자 " + unknownNumbers;
        }
        return null;
    }

    // 천 단위 쉼표를 지운 뒤 숫자(소수 포함)를 문자열로 모은다. "1,000만"과 "1000만"은 같은 숫자 1000이다.
    private static Set<String> numbers(String text) {
        Matcher matcher = NUMBER.matcher(THOUSANDS_SEPARATOR.matcher(text).replaceAll(""));
        Set<String> numbers = new LinkedHashSet<>();
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }
}
