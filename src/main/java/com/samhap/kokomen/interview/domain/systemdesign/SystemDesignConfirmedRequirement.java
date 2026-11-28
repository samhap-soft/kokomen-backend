package com.samhap.kokomen.interview.domain.systemdesign;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 면접자가 요구사항 역질문으로 확정한 요구사항 하나. 리포트·공개 리포트의 confirmed_requirements와 답변 채점(L2·L3) user 메시지의
 * {@code <confirmed_requirements>}가 {@link #collect}로 모은 같은 목록을 쓴다. 역질문 행의 확정 항목(SystemDesignRequirementItem)과
 * 달리 숨은 요구사항의 이름·정해 둔 사실을 그대로 담을 수 있어 LLM 출력의 글자 수 한도를 두지 않는다.
 *
 * @param name   요구사항 이름(앞뒤 공백을 지운 값). 목록 안에서 겹치지 않는다
 * @param detail 확정한 내용
 * @param source SEED면 문제에 정해 둔 숨은 요구사항의 사실, ASSUMED면 숨은 요구사항에 없어 면접관(LLM)이 정해 답한 내용
 */
public record SystemDesignConfirmedRequirement(
        String name,
        String detail,
        SystemDesignRequirementSource source
) {

    /**
     * 역질문 행들(물은 순서)에서 확정한 요구사항을 모은다. LLM을 부르지 않고 저장된 값만 쓴다.
     *
     * <ul>
     *     <li>행마다 그 행에서 처음 물은 숨은 요구사항(물은 code 중 이 문제에 있는 것, 문제의 순서)을 정해 둔 사실로 먼저 두고, 이어서
     *     그 행의 확정 항목을 적은 순서로 잇는다. 모델이 시드 항목을 적지 않았어도 확인한 시드는 그 시드를 처음 물은 자리에 나온다.</li>
     *     <li>이름이 같은 항목(앞뒤 공백을 지운 값이 같다)은 처음 나온 자리에 두고 값은 나중 것으로 덮는다. 같은 항목을 다시 확정했으면
     *     마지막 말이 기준이다. 모델이 적은 시드 항목도 같은 이름의 정해 둔 사실을 덮는다.</li>
     *     <li>숨은 요구사항의 이름은 확인한 숨은 요구사항의 SEED 항목만 쓴다. 확인한 시드의 이름이 아닌 SEED 항목(묻지 않은 시드나 이름을
     *     바꿔 적은 시드)과 숨은 요구사항의 이름을 단 ASSUMED 항목은 넣지 않는다.</li>
     *     <li>확정 항목을 저장하기 전(V58 전)에 만든 행(NULL)은 항목을 건너뛰고 물은 숨은 요구사항만 정해 둔 사실로 나온다.
     *     요구사항을 묻는 질문이 아니었던 행(DECLINED)은 물은 code도 항목도 없다.</li>
     * </ul>
     * 그래서 목록의 SEED 항목은 역질문으로 확인한 숨은 요구사항(모든 행의 물은 code 합집합)과 이름마다 하나씩 같고, 리포트의 놓친
     * 요구사항과 합치면 문제의 숨은 요구사항 전부다. 묻지 않은 숨은 요구사항의 정해 둔 사실은 들어가지 않는다.
     *
     * @param requirementQuestions 이 면접의 역질문 행(물은 순서)
     * @param hiddenRequirements   이 문제의 숨은 요구사항(순서는 상관없다)
     */
    public static List<SystemDesignConfirmedRequirement> collect(
            List<SystemDesignRequirementQuestion> requirementQuestions,
            List<SystemDesignHiddenRequirement> hiddenRequirements) {
        Map<String, SystemDesignHiddenRequirement> requirementsByCode = hiddenRequirements.stream()
                .collect(Collectors.toMap(SystemDesignHiddenRequirement::getCode, Function.identity()));
        Set<String> requirementNames = hiddenRequirements.stream()
                .map(requirement -> nameKey(requirement.getName()))
                .collect(Collectors.toSet());
        Set<String> confirmedNames = SystemDesignRequirementQuestion.collectAskedRequirementCodes(requirementQuestions)
                .stream()
                .filter(requirementsByCode::containsKey)
                .map(code -> nameKey(requirementsByCode.get(code).getName()))
                .collect(Collectors.toSet());
        Map<String, SystemDesignConfirmedRequirement> requirementsByName = new LinkedHashMap<>();
        Set<String> seenCodes = new HashSet<>();
        for (SystemDesignRequirementQuestion requirementQuestion : requirementQuestions) {
            putFirstAskedSeeds(requirementQuestion, requirementsByCode, seenCodes, requirementsByName);
            List<SystemDesignRequirementItem> items = requirementQuestion.getRequirementItems();
            if (items == null) {
                continue;
            }
            items.stream()
                    .filter(item -> isAllowed(item, requirementNames, confirmedNames))
                    .forEach(item -> requirementsByName.put(nameKey(item.name()),
                            new SystemDesignConfirmedRequirement(nameKey(item.name()), item.detail(), item.source())));
        }
        return List.copyOf(requirementsByName.values());
    }

    // 이 행에서 처음 물은 숨은 요구사항을 문제의 순서(물은 code의 순서)로 정해 둔 사실로 둔다. 이미 같은 이름이 있으면 두지 않는다.
    private static void putFirstAskedSeeds(SystemDesignRequirementQuestion requirementQuestion,
                                           Map<String, SystemDesignHiddenRequirement> requirementsByCode,
                                           Set<String> seenCodes,
                                           Map<String, SystemDesignConfirmedRequirement> requirementsByName) {
        for (String code : requirementQuestion.getAskedRequirementCodes()) {
            SystemDesignHiddenRequirement requirement = requirementsByCode.get(code);
            if (requirement == null || !seenCodes.add(code)) {
                continue;
            }
            requirementsByName.putIfAbsent(nameKey(requirement.getName()), new SystemDesignConfirmedRequirement(
                    nameKey(requirement.getName()), requirement.getAnswer(), SystemDesignRequirementSource.SEED));
        }
    }

    private static boolean isAllowed(SystemDesignRequirementItem item, Set<String> requirementNames,
                                     Set<String> confirmedNames) {
        String name = nameKey(item.name());
        if (item.source() == SystemDesignRequirementSource.SEED) {
            return confirmedNames.contains(name);
        }
        return !requirementNames.contains(name);
    }

    private static String nameKey(String name) {
        return SystemDesignText.normalize(name);
    }
}
