package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignConfirmedRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementAnswerType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementItem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementSource;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignText;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 역질문 답(L1) 출력을 검증해 최종 결과를 정한다. 도구 스키마의 required·enum은 모델에 대한 지시일 뿐이라 서버가 다시 검사한다.
 *
 * <ul>
 *     <li>판정 필드(requirement_*)와 답의 종류(answer_type)가 없거나 값이 잘못되면 응답 전체를 실패로 본다.</li>
 *     <li>답의 종류는 판정을 기준으로 바로잡는다. YES인 숨은 요구사항이 있으면 시드 사실이 답에 들어간 것이다.</li>
 *     <li>요구사항을 묻는 질문이 아니면(DECLINED) 정해진 문구 {@value #DECLINED_ANSWER}로 답하고 확정 항목을 비운다.</li>
 *     <li>답이 비었거나 {@value #MAX_ANSWER_LENGTH}자를 넘거나, 결정을 면접자에게 넘기는 회피 문구가 있거나, 묻지 않은 숨은
 *     요구사항의 '숫자+단위'가 있거나, SEED 답에 정해 둔 사실에 없는 '숫자+단위'가 있으면 쓰지 않는다. 물은 숨은 요구사항이 있으면
 *     그 시드 답으로 바꾸고(폴백), 없으면 실패다. 다만 모델이 DECLINED로 낸 답이면 시드 답으로 바꾸지 않고 정해진 문구로 답한다.</li>
 *     <li>확정 항목은 규칙을 어긴 것만 버린다. 형식과 묻지 않은 숨은 요구사항의 '숫자+단위'를 먼저 보고, 리포트·채점의 확정한
 *     요구사항 모으기(SystemDesignConfirmedRequirement.collect)와 같은 규칙으로 면접자가 들을 답에 맞춰 한 번 더 거른다. 그래서 저장하는
 *     항목, 다음 역질문의 확정한 요구사항 문맥, 리포트·채점의 목록이 같은 규칙을 지난다. 답의 종류는 거른 뒤 남은 항목으로 정한다.
 *     항목은 다음 역질문의 문맥과 리포트용이라 버려도 답은 나간다.</li>
 * </ul>
 */
@Slf4j
public final class SystemDesignRequirementAnswerComposer {

    public static final String DECLINED_ANSWER = "요구사항을 묻는 질문에만 답할 수 있어요. 설계 방법은 직접 정해 주세요.";
    public static final int MAX_ANSWER_LENGTH = 300;
    public static final int MAX_ITEM_COUNT = 3;

    private static final ObjectMapper ITEMS_MAPPER = Jackson2CompatibleJsonMapper.builder().build();
    // 결정을 면접자에게 넘기는 문구(배포 전의 고정 답 "등록되지 않은 내용이에요. 가정하고 진행하세요." 포함)와, 요구사항 질문이 아닐 때의
    // 정해진 문구. 답의 종류가 DECLINED가 아닌데 거절했다면 답과 종류가 어긋난다. "등록되지 않은 사용자"처럼 정상 문장에도 나오는 말은
    // 뒤에 "내용"이 올 때만 본다.
    private static final Pattern EVASION = Pattern.compile("(?:등록되지|정해\\s*두지|정해지지)\\s*않은\\s*내용"
            + "|가정(?:하고|해서|하여)\\s*진행|(?:자유롭게|직접|알아서|임의로|적절히|편하게)\\s*가정|가정해\\s*주세요"
            + "|요구사항을\\s*묻는\\s*질문에만");
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
     * 도구 입력을 검증해 저장하고 면접자에게 줄 결과를 정한다. 답과 확정 항목은 엔티티를 되돌리고 앞뒤 공백을 지운 값으로 검사한다.
     *
     * @throws ExternalApiException 판정 필드나 답의 종류가 없거나 잘못됐을 때, 답을 쓸 수 없는데 바꿀 시드 답(물은 숨은 요구사항)이
     *                              없을 때
     */
    public static SystemDesignRequirementAnswerResult compose(SystemDesignRequirementAnswerCommand command,
                                                              JsonNode toolInput) {
        List<Requirement> requirements = command.problem().requirements();
        List<String> askedCodes = resolveAskedCodes(requirements, toolInput);
        SystemDesignRequirementAnswerType llmAnswerType = readAnswerType(toolInput);
        SystemDesignRequirementAnswerType answerType = correctAnswerType(llmAnswerType, askedCodes,
                command.interviewId());
        if (answerType == SystemDesignRequirementAnswerType.DECLINED) {
            return declined();
        }
        NumberUnits numberUnits = NumberUnits.of(requirements, command.confirmedRequirements(), askedCodes);
        String answer = Objects.requireNonNullElse(readText(toolInput, SystemDesignToolSchemas.ANSWER_FIELD), "");
        List<SystemDesignRequirementItem> candidates = readItems(toolInput, askedCodes, numberUnits.forbidden(),
                command.interviewId());
        // 확정 항목은 면접자가 들을 답으로 거른다. LLM 답을 쓰면 그 답이다. 시드 답으로 바꾸면 아래에서 ASSUMED 항목을 모두 버리고, SEED
        // 항목의 검사는 답이 아니라 정해 둔 사실과 견주므로 이 답으로 거른 결과와 같다. 정해진 문구로 답하면 항목을 모두 비운다. 답의
        // 종류를 거른 항목으로 정해야 버려질 ASSUMED 항목 때문에 MIXED로 보고 SEED 답의 수치 검사를 건너뛰지 않는다.
        List<SystemDesignRequirementItem> items = ConfirmedItemRule.of(requirements, command.confirmedRequirements(),
                askedCodes).keepConfirmableItems(candidates, answer, command.interviewId());
        answerType = adjustForAssumedItems(answerType, items);
        String rejectReason = findRejectReason(answer, answerType, numberUnits);
        if (rejectReason == null) {
            return new SystemDesignRequirementAnswerResult(askedCodes, answerType, answer, items);
        }
        // 모델은 요구사항을 묻는 질문이 아니라고 봤는데 판정에 YES를 표시하고 쓸 답을 내지 않았다. "요구사항을 전부 알려 주세요"를 이렇게
        // 내면 시드 답으로 바꿀 때 정해 둔 사실을 한꺼번에 알려 주게 된다. 실제로 알려 준 것이 없으니 물은 요구사항으로 남기지도 않는다.
        if (llmAnswerType == SystemDesignRequirementAnswerType.DECLINED) {
            log.warn("시스템 설계 역질문을 DECLINED로 냈는데 YES가 있고 쓸 답이 없어 정해진 문구로 답합니다. reason: {}, "
                    + "askedCodes: {}, interviewId: {}", rejectReason, askedCodes, command.interviewId());
            return declined();
        }
        if (askedCodes.isEmpty()) {
            log.warn("시스템 설계 역질문 답을 쓸 수 없고 바꿀 시드 답도 없어 실패로 봅니다. reason: {}, answerType: {}, "
                    + "interviewId: {}", rejectReason, answerType, command.interviewId());
            throw new ExternalApiException("시스템 설계 역질문 답을 쓸 수 없습니다. reason=" + rejectReason);
        }
        log.warn("시스템 설계 역질문 답을 시드 답으로 바꿉니다. reason: {}, askedCodes: {}, interviewId: {}", rejectReason,
                askedCodes, command.interviewId());
        // 답이 시드 문장으로 바뀌면 LLM이 정한 내용은 면접자에게 나가지 않는다. 답과 항목이 어긋나지 않게 LLM이 정한 항목을 버린다.
        // 남은 SEED 항목은 정해 둔 사실과 견준 검사를 이미 지났다.
        List<SystemDesignRequirementItem> seedItems = items.stream()
                .filter(item -> item.source() == SystemDesignRequirementSource.SEED)
                .toList();
        return new SystemDesignRequirementAnswerResult(askedCodes, SystemDesignRequirementAnswerType.SEED,
                seedAnswer(requirements, askedCodes), seedItems);
    }

    private static SystemDesignRequirementAnswerResult declined() {
        return new SystemDesignRequirementAnswerResult(List.of(), SystemDesignRequirementAnswerType.DECLINED,
                DECLINED_ANSWER, List.of());
    }

    private static SystemDesignRequirementAnswerType readAnswerType(JsonNode toolInput) {
        JsonNode value = toolInput == null ? null : toolInput.get(SystemDesignToolSchemas.ANSWER_TYPE_FIELD);
        if (value != null && value.isString()) {
            for (SystemDesignRequirementAnswerType answerType : SystemDesignRequirementAnswerType.values()) {
                if (answerType.name().equals(value.stringValue())) {
                    return answerType;
                }
            }
        }
        throw new ExternalApiException("시스템 설계 역질문 답의 종류가 없거나 값이 잘못됐습니다. field="
                + SystemDesignToolSchemas.ANSWER_TYPE_FIELD);
    }

    /**
     * 판정을 믿는다. YES가 있으면 시드 사실이 답에 들어가므로 ASSUMED는 MIXED로, DECLINED는 SEED로 본다(DECLINED였던 답도 다른
     * 답과 같은 검사를 받아 거절 문구나 빈 답이면 시드 답으로 바뀐다). YES가 없으면 SEED·MIXED는 ASSUMED로 본다.
     */
    private static SystemDesignRequirementAnswerType correctAnswerType(SystemDesignRequirementAnswerType answerType,
                                                                       List<String> askedCodes, Long interviewId) {
        SystemDesignRequirementAnswerType corrected = askedCodes.isEmpty()
                ? withoutAskedRequirement(answerType)
                : withAskedRequirement(answerType);
        if (corrected != answerType) {
            log.info("시스템 설계 역질문 답의 종류를 판정에 맞춰 바꿉니다. from: {}, to: {}, askedCodes: {}, interviewId: {}",
                    answerType, corrected, askedCodes, interviewId);
        }
        return corrected;
    }

    private static SystemDesignRequirementAnswerType withAskedRequirement(
            SystemDesignRequirementAnswerType answerType) {
        return switch (answerType) {
            case ASSUMED -> SystemDesignRequirementAnswerType.MIXED;
            case DECLINED -> SystemDesignRequirementAnswerType.SEED;
            case SEED, MIXED -> answerType;
        };
    }

    private static SystemDesignRequirementAnswerType withoutAskedRequirement(
            SystemDesignRequirementAnswerType answerType) {
        return switch (answerType) {
            case SEED, MIXED -> SystemDesignRequirementAnswerType.ASSUMED;
            case ASSUMED, DECLINED -> answerType;
        };
    }

    // LLM이 정한 내용(거른 뒤에도 남은 ASSUMED 항목)이 있는 SEED 답은 둘 다 답한 것이다.
    private static SystemDesignRequirementAnswerType adjustForAssumedItems(
            SystemDesignRequirementAnswerType answerType, List<SystemDesignRequirementItem> items) {
        boolean hasAssumedItem = items.stream()
                .anyMatch(item -> item.source() == SystemDesignRequirementSource.ASSUMED);
        if (answerType == SystemDesignRequirementAnswerType.SEED && hasAssumedItem) {
            return SystemDesignRequirementAnswerType.MIXED;
        }
        return answerType;
    }

    private static String findRejectReason(String answer, SystemDesignRequirementAnswerType answerType,
                                           NumberUnits numberUnits) {
        if (answer.isEmpty()) {
            return "빈 답";
        }
        if (answer.length() > MAX_ANSWER_LENGTH) {
            return "답 길이 " + answer.length() + "자";
        }
        if (EVASION.matcher(answer).find()) {
            return "회피 문구";
        }
        List<String> leaked = numberUnits.leakedIn(answer);
        if (!leaked.isEmpty()) {
            return "묻지 않은 요구사항의 수치 " + leaked;
        }
        // 정해 둔 사실만으로 답했다면 시드 값을 바꾸거나 면접자가 할 계산을 대신한 수치가 들어갈 까닭이 없다. LLM이 정한 내용이 들어간
        // 답(ASSUMED·MIXED)은 정한 값의 수치를 담으므로 보지 않는다.
        if (answerType == SystemDesignRequirementAnswerType.SEED) {
            List<String> unknown = numberUnits.unknownIn(answer);
            if (!unknown.isEmpty()) {
                return "정해 둔 사실에 없는 수치 " + unknown;
            }
        }
        return null;
    }

    private static List<SystemDesignRequirementItem> readItems(JsonNode toolInput, List<String> askedCodes,
                                                               Set<String> forbiddenNumberUnits, Long interviewId) {
        JsonNode itemsNode = itemsArray(toolInput.get(SystemDesignToolSchemas.REQUIREMENT_ITEMS_FIELD), interviewId);
        List<SystemDesignRequirementItem> items = new ArrayList<>();
        for (int index = 0; index < itemsNode.size(); index++) {
            ItemCandidate candidate = ItemCandidate.from(itemsNode.get(index));
            String dropReason = candidate.findDropReason(askedCodes, forbiddenNumberUnits);
            if (dropReason != null) {
                log.warn("시스템 설계 역질문 확정 항목을 버립니다. reason: {}, index: {}, interviewId: {}", dropReason, index,
                        interviewId);
                continue;
            }
            items.add(candidate.toItem());
        }
        if (items.size() > MAX_ITEM_COUNT) {
            log.warn("시스템 설계 역질문에서 {}개를 넘는 확정 항목을 버립니다. count: {}, interviewId: {}", MAX_ITEM_COUNT,
                    items.size(), interviewId);
            return List.copyOf(items.subList(0, MAX_ITEM_COUNT));
        }
        return List.copyOf(items);
    }

    // 배열이 아니면 빈 배열로 본다. 모델이 배열을 JSON 문자열로 감싸 보내기도 해서, 문자열이면 배열로 읽어 본다.
    private static JsonNode itemsArray(JsonNode itemsNode, Long interviewId) {
        if (itemsNode != null && itemsNode.isString()) {
            try {
                itemsNode = ITEMS_MAPPER.readTree(itemsNode.stringValue());
            } catch (JacksonException e) {
                itemsNode = null;
            }
        }
        if (itemsNode == null || !itemsNode.isArray()) {
            log.warn("시스템 설계 역질문 확정 항목 필드가 없거나 배열이 아니어서 비웁니다. interviewId: {}", interviewId);
            return ITEMS_MAPPER.createArrayNode();
        }
        return itemsNode;
    }

    // 문자열이 아니면 null이다. 엔티티를 되돌린 뒤 앞뒤 공백을 지운다.
    private static String readText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isString()) {
            return null;
        }
        return SystemDesignText.normalize(SystemDesignUserMessages.unescape(value.stringValue()));
    }

    private static String seedAnswer(List<Requirement> requirements, List<String> askedCodes) {
        return requirements.stream()
                .filter(requirement -> askedCodes.contains(requirement.code()))
                .map(Requirement::answer)
                .collect(Collectors.joining(SEED_ANSWER_DELIMITER));
    }

    /**
     * 답과 확정 항목에서 보는 '숫자+단위'. forbidden은 묻지 않은 숨은 요구사항의 표현 가운데 지금까지 물은 숨은 요구사항(이전 역질문과
     * 이번 판정)과 이전 확정 항목에 없는 것이고, known은 지금까지 물은 숨은 요구사항과 이전 확정 항목의 표현이다. 이번 SEED 항목도
     * 같은 검사를 통과해야 남으므로 허용 목록에 더해도 결과가 같다. 숫자만 같은 우연(정한 "5개"와 시드 "5년")은 단위가 달라 걸리지
     * 않는다. "백만" 같은 한글 수사, 단위를 바꾼 표기("10만"과 "100,000")는 잡지 못해 프롬프트 규칙이 함께 막는다.
     */
    private record NumberUnits(Set<String> forbidden, Set<String> known) {

        static NumberUnits of(List<Requirement> requirements, SystemDesignConfirmedRequirements confirmed,
                              List<String> askedCodes) {
            Set<String> knownCodes = new HashSet<>(confirmed.askedRequirementCodes());
            knownCodes.addAll(askedCodes);
            Set<String> forbidden = new LinkedHashSet<>();
            Set<String> known = new HashSet<>();
            for (Requirement requirement : requirements) {
                Set<String> numberUnits = extract(requirement.answer());
                if (knownCodes.contains(requirement.code())) {
                    known.addAll(numberUnits);
                } else {
                    forbidden.addAll(numberUnits);
                }
            }
            for (SystemDesignRequirementItem item : confirmed.items()) {
                known.addAll(extract(item.name() + SEED_ANSWER_DELIMITER + item.detail()));
            }
            forbidden.removeAll(known);
            return new NumberUnits(forbidden, known);
        }

        // '숫자+단위'의 정의는 SystemDesignText.numberUnits에 있다(확정한 요구사항 모으기와 같은 정의).
        static Set<String> extract(String text) {
            return SystemDesignText.numberUnits(text);
        }

        List<String> leakedIn(String text) {
            return extract(text).stream()
                    .filter(forbidden::contains)
                    .toList();
        }

        List<String> unknownIn(String text) {
            return extract(text).stream()
                    .filter(numberUnit -> !known.contains(numberUnit))
                    .toList();
        }
    }

    /**
     * 확정 항목을 리포트·채점의 확정한 요구사항 모으기(SystemDesignConfirmedRequirement.collect)와 같은 규칙
     * (SystemDesignConfirmedRequirement.findItemRejectReason)으로 거를 때 넘기는 숨은 요구사항. 이번 행을 마지막 행으로 둔 collect가
     * 이 행에 쓰는 값과 같다. 물은 시드는 이전 역질문과 이번 판정을 합친 것이고(collect도 그 행까지 누적한다), 묻지 않은 시드는 그
     * 나머지다. 들은 '숫자+단위'는 이번 답만 본다(collect도 행마다 그 행의 답만 본다). 뒤 역질문에서 다른 시드를 물으면 collect의 묻지
     * 않은 시드가 줄 뿐이라, 여기서 남긴 항목은 나중에 모아도 버려지지 않는다.
     */
    private record ConfirmedItemRule(Map<String, String> seedFactsByName, Set<String> askedSeedNames,
                                     Set<String> unaskedSeedNames) {

        static ConfirmedItemRule of(List<Requirement> requirements, SystemDesignConfirmedRequirements confirmed,
                                    List<String> askedCodes) {
            Set<String> askedCodesSoFar = new HashSet<>(confirmed.askedRequirementCodes());
            askedCodesSoFar.addAll(askedCodes);
            Map<String, String> seedFactsByName = new HashMap<>();
            Set<String> askedSeedNames = new HashSet<>();
            for (Requirement requirement : requirements) {
                String name = SystemDesignText.normalize(requirement.name());
                seedFactsByName.put(name, requirement.answer());
                if (askedCodesSoFar.contains(requirement.code())) {
                    askedSeedNames.add(name);
                }
            }
            Set<String> unaskedSeedNames = new HashSet<>(seedFactsByName.keySet());
            unaskedSeedNames.removeAll(askedSeedNames);
            return new ConfirmedItemRule(seedFactsByName, askedSeedNames, unaskedSeedNames);
        }

        // 규칙을 어긴 항목을 버린다. 로그에는 이유만 남기고 항목 내용은 남기지 않는다.
        List<SystemDesignRequirementItem> keepConfirmableItems(List<SystemDesignRequirementItem> items,
                                                               String answer, Long interviewId) {
            Set<String> heardNumberUnits = SystemDesignText.numberUnits(answer);
            List<SystemDesignRequirementItem> kept = new ArrayList<>();
            for (SystemDesignRequirementItem item : items) {
                Optional<String> rejectReason = SystemDesignConfirmedRequirement.findItemRejectReason(item,
                        seedFactsByName, askedSeedNames, unaskedSeedNames, heardNumberUnits);
                if (rejectReason.isPresent()) {
                    log.warn("시스템 설계 역질문 확정 항목이 확정한 요구사항 규칙에 맞지 않아 버립니다. reason: {}, interviewId: {}",
                            rejectReason.get(), interviewId);
                    continue;
                }
                kept.add(item);
            }
            return List.copyOf(kept);
        }
    }

    // 확정 항목 원소 하나를 읽은 값. 객체가 아니거나 필드가 문자열이 아니면 그 값이 null이다.
    private record ItemCandidate(boolean object, String name, String detail, String source) {

        static ItemCandidate from(JsonNode element) {
            if (element == null || !element.isObject()) {
                return new ItemCandidate(false, null, null, null);
            }
            JsonNode source = element.get(SystemDesignToolSchemas.ITEM_SOURCE_FIELD);
            return new ItemCandidate(true, readText(element, SystemDesignToolSchemas.ITEM_NAME_FIELD),
                    readText(element, SystemDesignToolSchemas.ITEM_DETAIL_FIELD),
                    source != null && source.isString() ? source.stringValue() : null);
        }

        // 잘라 내면 뜻이 바뀔 수 있어 규칙을 어긴 항목은 통째로 버린다.
        String findDropReason(List<String> askedCodes, Set<String> forbiddenNumberUnits) {
            if (!object) {
                return "객체가 아님";
            }
            if (!SystemDesignRequirementItem.hasValidLength(name, SystemDesignRequirementItem.MAX_NAME_LENGTH)) {
                return "이름 길이 " + (name == null ? "없음" : name.length() + "자");
            }
            if (!SystemDesignRequirementItem.hasValidLength(detail, SystemDesignRequirementItem.MAX_DETAIL_LENGTH)) {
                return "내용 길이 " + (detail == null ? "없음" : detail.length() + "자");
            }
            SystemDesignRequirementSource itemSource = parseSource();
            if (itemSource == null) {
                return "출처 값 " + source;
            }
            if (itemSource == SystemDesignRequirementSource.SEED && askedCodes.isEmpty()) {
                return "물은 숨은 요구사항이 없는데 SEED";
            }
            List<String> leaked = NumberUnits.extract(name + SEED_ANSWER_DELIMITER + detail).stream()
                    .filter(forbiddenNumberUnits::contains)
                    .toList();
            if (!leaked.isEmpty()) {
                return "묻지 않은 요구사항의 수치 " + leaked;
            }
            return null;
        }

        SystemDesignRequirementItem toItem() {
            return new SystemDesignRequirementItem(name, detail, parseSource());
        }

        private SystemDesignRequirementSource parseSource() {
            for (SystemDesignRequirementSource candidate : SystemDesignRequirementSource.values()) {
                if (candidate.name().equals(source)) {
                    return candidate;
                }
            }
            return null;
        }
    }
}
