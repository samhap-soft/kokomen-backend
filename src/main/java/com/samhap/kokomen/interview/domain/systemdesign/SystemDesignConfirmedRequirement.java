package com.samhap.kokomen.interview.domain.systemdesign;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * 면접자가 요구사항 역질문으로 확정한 요구사항 하나. 리포트·공개 리포트의 confirmed_requirements와 답변 채점(L2·L3) user 메시지의
 * {@code <confirmed_requirements>}가 {@link #collect}로 모은 같은 목록을 쓴다. 역질문 행의 확정 항목(SystemDesignRequirementItem)과
 * 달리 숨은 요구사항의 이름·정해 둔 사실을 그대로 담을 수 있어 LLM 출력의 글자 수 한도를 두지 않는다.
 *
 * @param name   요구사항 이름(앞뒤 공백을 지운 값). 목록 안에서 겹치지 않는다
 * @param detail 확정한 내용
 * @param source SEED면 문제에 정해 둔 숨은 요구사항의 사실, ASSUMED면 숨은 요구사항에 없어 면접관(LLM)이 정해 답한 내용
 */
@Slf4j
public record SystemDesignConfirmedRequirement(
        String name,
        String detail,
        SystemDesignRequirementSource source
) {

    private static final String NAME_DETAIL_DELIMITER = " ";

    /**
     * 역질문 행들(물은 순서)에서 확정한 요구사항을 모은다. LLM을 부르지 않고 저장된 값만 쓴다.
     *
     * <ul>
     *     <li>행마다 그 행에서 처음 물은 숨은 요구사항(물은 code 중 이 문제에 있는 것, 물은 code의 순서)을 정해 둔 사실로 먼저 두고,
     *     이어서 그 행의 확정 항목을 적은 순서로 잇는다. 모델이 시드 항목을 적지 않았어도 확인한 시드는 그 시드를 처음 물은 자리에
     *     나온다.</li>
     *     <li>이름이 같은 항목(앞뒤 공백을 지운 값이 같다)은 처음 나온 자리에 두고 값은 나중 것으로 덮는다. 같은 항목을 다시 확정했으면
     *     마지막 말이 기준이다. 모델이 적은 시드 항목도 같은 이름의 정해 둔 사실을 덮는다.</li>
     *     <li>숨은 요구사항의 이름은 확인한 숨은 요구사항의 SEED 항목만 쓴다. SEED 항목은 그 행까지 물은 시드의 이름일 때만 쓰고(묻지
     *     않은 시드, 이름을 바꿔 적은 시드, 뒤에서야 물은 시드를 앞 행에 적은 항목은 넣지 않는다), 숨은 요구사항의 이름을 단 ASSUMED
     *     항목은 넣지 않는다.</li>
     *     <li>항목의 수치는 면접자가 들은 것이어야 한다. SEED 항목은 내용에 '숫자+단위'({@link SystemDesignText#numberUnits})가 있고
     *     모두 그 시드의 정해 둔 사실 안에 있을 때만 정해 둔 사실을 덮는다. 아니면(값을 바꾸거나 면접자가 할 계산을 대신한 항목,
     *     역질문 답을 시드 답으로 바꾼 행에 남은 항목, 수를 한글로 써 가릴 수 없는 항목) 정해 둔 사실이 남는다. ASSUMED 항목은
     *     이름·내용의 '숫자+단위'가 그 역질문의 답에 모두 있을 때만 넣는다.</li>
     *     <li>ASSUMED 항목의 이름이나 내용에 묻지 않은 숨은 요구사항의 이름이 그대로 들어 있으면 넣지 않는다.</li>
     *     <li>확정 항목을 저장하기 전(V58 전)에 만든 행(NULL)은 항목을 건너뛰고 물은 숨은 요구사항만 정해 둔 사실로 나온다.
     *     요구사항을 묻는 질문이 아니었던 행(DECLINED)은 물은 code도 항목도 없다.</li>
     * </ul>
     * 그래서 목록의 SEED 항목은 역질문으로 확인한 숨은 요구사항(모든 행의 물은 code 합집합)과 이름마다 하나씩 같고 처음 물은 순서로
     * 놓이며, 리포트의 놓친 요구사항과 합치면 문제의 숨은 요구사항 전부다. 묻지 않은 숨은 요구사항은 정해 둔 사실로도, 그 이름을 단
     * 항목으로도 들어가지 않는다. 다만 이름이 다르고 '숫자+단위'도 없이 뜻만 같은 ASSUMED 항목은 가리지 못한다(역질문 답 규칙이 막는다).
     * 또 모델이 시드 항목을 적지 않았거나 V58 전 행이면 정해 둔 사실 전체가 나오므로, 답이 그 사실의 일부만 말했다면 답에 없던 내용이
     * 함께 나온다. 넣지 않은 항목은 WARN 로그(역질문 행 id와 이유, 내용은 남기지 않는다)로 남긴다.
     *
     * @param requirementQuestions 이 면접의 역질문 행(물은 순서)
     * @param hiddenRequirements   이 문제의 숨은 요구사항(순서는 상관없다)
     */
    public static List<SystemDesignConfirmedRequirement> collect(
            List<SystemDesignRequirementQuestion> requirementQuestions,
            List<SystemDesignHiddenRequirement> hiddenRequirements) {
        Map<String, SystemDesignHiddenRequirement> requirementsByCode = hiddenRequirements.stream()
                .collect(Collectors.toMap(SystemDesignHiddenRequirement::getCode, Function.identity()));
        Map<String, String> seedFactsByName = hiddenRequirements.stream()
                .collect(Collectors.toMap(requirement -> nameKey(requirement.getName()),
                        SystemDesignHiddenRequirement::getAnswer));
        Set<String> confirmedNames = SystemDesignRequirementQuestion.collectAskedRequirementCodes(requirementQuestions)
                .stream()
                .filter(requirementsByCode::containsKey)
                .map(code -> nameKey(requirementsByCode.get(code).getName()))
                .collect(Collectors.toSet());
        Set<String> unconfirmedNames = new HashSet<>(seedFactsByName.keySet());
        unconfirmedNames.removeAll(confirmedNames);
        Map<String, SystemDesignConfirmedRequirement> requirementsByName = new LinkedHashMap<>();
        Set<String> askedNames = new HashSet<>();
        for (SystemDesignRequirementQuestion requirementQuestion : requirementQuestions) {
            putFirstAskedSeeds(requirementQuestion, requirementsByCode, askedNames, requirementsByName);
            List<SystemDesignRequirementItem> items = requirementQuestion.getRequirementItems();
            if (items == null) {
                continue;
            }
            Set<String> heardNumberUnits = SystemDesignText.numberUnits(requirementQuestion.getAnswer());
            for (SystemDesignRequirementItem item : items) {
                String rejectReason = item.source() == SystemDesignRequirementSource.SEED
                        ? findSeedItemRejectReason(item, seedFactsByName, askedNames)
                        : findAssumedItemRejectReason(item, seedFactsByName, unconfirmedNames, heardNumberUnits);
                if (rejectReason != null) {
                    log.warn("확정한 요구사항에 이 확정 항목을 넣지 않습니다. requirementQuestionId: {}, source: {}, reason: {}",
                            requirementQuestion.getId(), item.source(), rejectReason);
                    continue;
                }
                requirementsByName.put(nameKey(item.name()),
                        new SystemDesignConfirmedRequirement(nameKey(item.name()), item.detail(), item.source()));
            }
        }
        return List.copyOf(requirementsByName.values());
    }

    // 이 행에서 처음 물은 숨은 요구사항을 물은 code의 순서로 정해 둔 사실로 두고, 그 이름을 지금까지 물은 시드 이름에 더한다.
    // 이미 같은 이름이 있으면 두지 않는다.
    private static void putFirstAskedSeeds(SystemDesignRequirementQuestion requirementQuestion,
                                           Map<String, SystemDesignHiddenRequirement> requirementsByCode,
                                           Set<String> askedNames,
                                           Map<String, SystemDesignConfirmedRequirement> requirementsByName) {
        for (String code : requirementQuestion.getAskedRequirementCodes()) {
            SystemDesignHiddenRequirement requirement = requirementsByCode.get(code);
            if (requirement == null || !askedNames.add(nameKey(requirement.getName()))) {
                continue;
            }
            requirementsByName.putIfAbsent(nameKey(requirement.getName()), new SystemDesignConfirmedRequirement(
                    nameKey(requirement.getName()), requirement.getAnswer(), SystemDesignRequirementSource.SEED));
        }
    }

    // SEED 항목은 그 행까지 물은 시드의 이름이고, 내용의 '숫자+단위'가 비어 있지 않고 그 시드의 정해 둔 사실 안에 있어야 한다.
    // 이름은 시드 이름이라 수치를 보지 않는다("1인당 발급 한도"의 "1인").
    private static String findSeedItemRejectReason(SystemDesignRequirementItem item,
                                                   Map<String, String> seedFactsByName, Set<String> askedNames) {
        String name = nameKey(item.name());
        if (!askedNames.contains(name)) {
            return "그 행까지 물은 시드의 이름이 아님";
        }
        Set<String> detailNumberUnits = SystemDesignText.numberUnits(item.detail());
        if (detailNumberUnits.isEmpty()
                || !SystemDesignText.numberUnits(seedFactsByName.get(name)).containsAll(detailNumberUnits)) {
            return "내용의 숫자+단위가 정해 둔 사실과 맞지 않음";
        }
        return null;
    }

    // ASSUMED 항목은 시드의 이름이 아니고, 이름·내용에 묻지 않은 시드의 이름이 없고, 이름·내용의 '숫자+단위'가 그 역질문의 답(면접자가
    // 들은 문장) 안에 있어야 한다.
    private static String findAssumedItemRejectReason(SystemDesignRequirementItem item,
                                                      Map<String, String> seedFactsByName,
                                                      Set<String> unconfirmedNames, Set<String> heardNumberUnits) {
        if (seedFactsByName.containsKey(nameKey(item.name()))) {
            return "시드의 이름";
        }
        String text = item.name() + NAME_DETAIL_DELIMITER + item.detail();
        if (unconfirmedNames.stream().anyMatch(text::contains)) {
            return "묻지 않은 시드의 이름이 들어 있음";
        }
        if (!heardNumberUnits.containsAll(SystemDesignText.numberUnits(text))) {
            return "답에 없는 숫자+단위";
        }
        return null;
    }

    private static String nameKey(String name) {
        return SystemDesignText.normalize(name);
    }
}
